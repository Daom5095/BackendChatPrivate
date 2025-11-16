package com.chatprivate.user;

import com.chatprivate.auth.AuthResponse;
import com.chatprivate.auth.LoginRequest;
import com.chatprivate.auth.RegisterRequest;
import com.chatprivate.messaging.model.UserPublicKey;
import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import com.chatprivate.security.JwtService;
import com.chatprivate.security.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio principal para la lógica de negocio de Usuarios.
 *
 * ACTUALIZADO EN SESIÓN 2:
 * - Integrado SecurityAuditLogger para eventos de seguridad
 * - Mejorada la validación de datos
 * - Mejor manejo de errores
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserPublicKeyRepository userPublicKeyRepository;
    private final SecurityAuditLogger auditLogger; // <-- NUEVO

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * FLUJO DE SEGURIDAD:
     * 1. Valida que username/email no existan
     * 2. Hashea la contraseña
     * 3. Guarda el usuario
     * 4. Guarda la clave pública
     * 5. Genera token JWT
     * 6. Loguea el evento de seguridad
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Logueo el intento de registro
        auditLogger.logRegistration(
                request.getUsername(),
                request.getEmail(),
                false, // Aún no sabemos si será exitoso
                "N/A" // La IP se loguea en el controller
        );

        // Validaciones de negocio
        if (userRepository.existsByUsername(request.getUsername())) {
            auditLogger.logRegistration(request.getUsername(), request.getEmail(), false, "N/A");
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            auditLogger.logRegistration(request.getUsername(), request.getEmail(), false, "N/A");
            throw new IllegalArgumentException("El email ya está en uso.");
        }

        // Hasheo la contraseña
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // Creo la entidad User
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(hashedPassword)
                .kekSalt(request.getKekSalt())
                .encryptedPrivateKey(request.getEncryptedPrivateKey())
                .kekIv(request.getKekIv())
                .build();

        // Guardo el usuario
        User savedUser = userRepository.save(user);

        // Guardo la clave pública
        if (request.getPublicKey() != null && !request.getPublicKey().isEmpty()) {
            UserPublicKey upk = new UserPublicKey();
            upk.setUserId(savedUser.getId());
            upk.setPublicKeyPem(request.getPublicKey());
            userPublicKeyRepository.save(upk);
        } else {
            auditLogger.logSuspiciousActivity(
                    "Registro sin clave pública",
                    "Usuario: " + savedUser.getUsername()
            );
        }

        // Genero token JWT
        String token = jwtService.generateToken(savedUser);

        // Logueo el éxito
        auditLogger.logRegistration(
                savedUser.getUsername(),
                savedUser.getEmail(),
                true,
                "N/A"
        );

        return AuthResponse.builder()
                .token(token)
                .build();
    }

    /**
     * Autentica a un usuario y devuelve un token JWT.
     *
     * FLUJO DE SEGURIDAD:
     * 1. Busca el usuario
     * 2. Valida la contraseña
     * 3. Genera token JWT
     * 4. Loguea el evento de seguridad
     */
    public AuthResponse login(LoginRequest request) {
        // Busco el usuario
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    auditLogger.logLoginAttempt(
                            request.getUsername(),
                            false,
                            "N/A",
                            "Usuario no encontrado"
                    );
                    return new UsernameNotFoundException("Usuario o contraseña incorrectos.");
                });

        // Verifico la contraseña
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            auditLogger.logLoginAttempt(
                    request.getUsername(),
                    false,
                    "N/A",
                    "Contraseña incorrecta"
            );
            throw new BadCredentialsException("Usuario o contraseña incorrectos.");
        }

        // Genero token JWT
        String token = jwtService.generateToken(user);

        // Logueo el éxito
        auditLogger.logLoginAttempt(
                request.getUsername(),
                true,
                "N/A",
                null
        );

        // Construyo la respuesta con los datos de la clave cifrada
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .kekSalt(user.getKekSalt())
                .encryptedPrivateKey(user.getEncryptedPrivateKey())
                .kekIv(user.getKekIv())
                .build();

        // Valido que los campos críticos no sean nulos
        if (response.getKekSalt() == null ||
                response.getEncryptedPrivateKey() == null ||
                response.getKekIv() == null) {

            auditLogger.logSecurityError(
                    "Datos de clave cifrada faltantes para usuario: " + user.getUsername(),
                    new RuntimeException("Datos incompletos")
            );

            throw new RuntimeException("Error interno: Faltan datos de seguridad para el usuario.");
        }

        return response;
    }

    /**
     * Permite a un usuario subir/actualizar su clave pública.
     */
    @Transactional
    public void uploadPublicKey(String username, String publicKeyPem) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        UserPublicKey upk = userPublicKeyRepository.findByUserId(user.getId())
                .orElse(new UserPublicKey());

        upk.setUserId(user.getId());
        upk.setPublicKeyPem(publicKeyPem);

        userPublicKeyRepository.save(upk);

        // Logueo el evento de seguridad
        auditLogger.logSuspiciousActivity(
                "Actualización de clave pública",
                "Usuario: " + username + ", userId: " + user.getId()
        );
    }
}