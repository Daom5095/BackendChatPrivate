package com.chatprivate.user;

import com.chatprivate.auth.AuthResponse;
import com.chatprivate.auth.LoginRequest;
import com.chatprivate.auth.RegisterRequest;
import com.chatprivate.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.chatprivate.messaging.model.UserPublicKey;
import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor // Lombok genera el constructor
public class UserService {

    // Logger para reemplazar System.out/err
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // Campos finales inyectados por Lombok
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Para hashear contraseña de login
    private final JwtService jwtService;
    private final UserPublicKeyRepository userPublicKeyRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Iniciando registro para usuario: {}", request.getUsername());

        // 1. Validar si usuario o email ya existen (opcional pero recomendado)
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Intento de registro fallido: Username {} ya existe.", request.getUsername());
            // Puedes lanzar una excepción específica o devolver un error
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Intento de registro fallido: Email {} ya existe.", request.getEmail());
            throw new IllegalArgumentException("El email ya está en uso.");
        }


        // 2. Hashear la contraseña recibida para guardarla
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        log.debug("Contraseña hasheada para usuario: {}", request.getUsername());

        // 3. Crear la entidad User con todos los datos
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(hashedPassword) // Guardar el hash
                // --- Guardar los nuevos campos ---
                .kekSalt(request.getKekSalt())
                .encryptedPrivateKey(request.getEncryptedPrivateKey())
                .kekIv(request.getKekIv())
                // ---------------------------------
                .build(); // createdAt se genera automáticamente

        // 4. Guardar el usuario
        User savedUser = userRepository.save(user);
        log.info("Usuario {} guardado con ID: {}", savedUser.getUsername(), savedUser.getId());

        // 5. Guardar la clave pública RSA (si se proporcionó)
        if (request.getPublicKey() != null && !request.getPublicKey().isEmpty()) {
            UserPublicKey upk = new UserPublicKey();
            upk.setUserId(savedUser.getId()); // ID del usuario recién guardado
            upk.setPublicKeyPem(request.getPublicKey());
            userPublicKeyRepository.save(upk);
            log.info("Clave pública guardada para usuario ID: {}", savedUser.getId());
        } else {
            log.warn("No se proporcionó clave pública durante el registro para usuario: {}", savedUser.getUsername());
        }

        // 6. Generar token JWT
        String token = jwtService.generateToken(savedUser);
        log.info("Token JWT generado para usuario: {}", savedUser.getUsername());

        // 7. Devolver solo el token en la respuesta de registro
        // (No devolvemos los datos de la clave cifrada aquí)
        return AuthResponse.builder()
                .token(token)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Iniciando login para usuario: {}", request.getUsername());

        // 1. Buscar usuario por nombre de usuario
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Intento de login fallido: Usuario {} no encontrado.", request.getUsername());
                    return new UsernameNotFoundException("Usuario o contraseña incorrectos."); // Mensaje genérico por seguridad
                });

        // 2. Verificar la contraseña usando el hash almacenado
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Intento de login fallido: Contraseña incorrecta para usuario {}.", request.getUsername());
            throw new BadCredentialsException("Usuario o contraseña incorrectos."); // Mensaje genérico
        }
        log.info("Contraseña verificada para usuario: {}", request.getUsername());

        // 3. Generar token JWT
        String token = jwtService.generateToken(user);
        log.info("Token JWT generado para usuario: {}", request.getUsername());

        // 4. Construir la respuesta CON los datos de la clave cifrada
        log.debug("Recuperando datos de clave cifrada para usuario: {}", request.getUsername());
        AuthResponse response = AuthResponse.builder()
                .token(token)
                // --- Devolver los campos necesarios para que el frontend descifre ---
                .kekSalt(user.getKekSalt())
                .encryptedPrivateKey(user.getEncryptedPrivateKey())
                .kekIv(user.getKekIv())
                // --------------------------------------------------------------------
                .build();

        // Validar que los campos no sean nulos antes de devolver (importante)
        if (response.getKekSalt() == null || response.getEncryptedPrivateKey() == null || response.getKekIv() == null) {
            log.error("¡Error crítico! Faltan datos de clave cifrada en la base de datos para el usuario: {}", user.getUsername());
            // Decide cómo manejar esto. Lanzar excepción es una opción segura.
            throw new RuntimeException("Error interno: Faltan datos de seguridad para el usuario.");
        }


        log.info("Login exitoso para usuario: {}", request.getUsername());
        return response;
    }

    // --- ¡NUEVO MÉTODO MOVIDO DESDE EL CONTROLADOR! ---
    @Transactional
    public void uploadPublicKey(String username, String publicKeyPem) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Busca si ya existe una clave para actualizarla, o crea una nueva
        UserPublicKey upk = userPublicKeyRepository.findByUserId(user.getId())
                .orElse(new UserPublicKey()); // Crea una nueva si no existe

        upk.setUserId(user.getId());
        upk.setPublicKeyPem(publicKeyPem);
        // 'updatedAt' se actualiza automáticamente en la entidad

        userPublicKeyRepository.save(upk);
        log.info("Clave pública (re)guardada para usuario ID: {}", user.getId());
    }
}