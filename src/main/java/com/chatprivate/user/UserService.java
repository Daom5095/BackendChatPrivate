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

/**
 * Servicio principal para la lógica de negocio de Usuarios.
 * Maneja el registro y el login.
 */
@Service
@RequiredArgsConstructor // Lombok genera el constructor con los campos 'final'
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // Dependencias inyectadas
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Para hashear contraseñas
    private final JwtService jwtService;
    private final UserPublicKeyRepository userPublicKeyRepository;

    /**
     * Registra un nuevo usuario en el sistema.
     * 1. Valida que el username/email no existan.
     * 2. Hashea la contraseña.
     * 3. Guarda la entidad User (incluyendo salt/IV/clave privada cifrada).
     * 4. Guarda la clave pública del usuario.
     * 5. Genera y devuelve un token JWT.
     *
     * @param request DTO con los datos de registro (validados por @Valid en el controller).
     * @return AuthResponse con el token JWT.
     */
    @Transactional // Si algo falla (ej. guardar clave pública), se revierte el guardado del usuario
    public AuthResponse register(RegisterRequest request) {
        log.info("Iniciando registro para usuario: {}", request.getUsername());

        // 1. Validar si usuario o email ya existen
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Intento de registro fallido: Username {} ya existe.", request.getUsername());
            throw new IllegalArgumentException("El nombre de usuario ya está en uso.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Intento de registro fallido: Email {} ya existe.", request.getEmail());
            throw new IllegalArgumentException("El email ya está en uso.");
        }


        // 2. Hashear la contraseña recibida para guardarla en la BD
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        log.debug("Contraseña hasheada para usuario: {}", request.getUsername());

        // 3. Crear la entidad User con todos los datos del request
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(hashedPassword) // Guardo el hash
                // --- Guardo los campos de cifrado que me envía el cliente ---
                .kekSalt(request.getKekSalt())
                .encryptedPrivateKey(request.getEncryptedPrivateKey())
                .kekIv(request.getKekIv())
                // -----------------------------------------------------------
                .build(); // createdAt se genera automáticamente en la entidad

        // 4. Guardar el usuario
        User savedUser = userRepository.save(user);
        log.info("Usuario {} guardado con ID: {}", savedUser.getUsername(), savedUser.getId());

        // 5. Guardar la clave pública RSA (en su propia tabla)
        if (request.getPublicKey() != null && !request.getPublicKey().isEmpty()) {
            UserPublicKey upk = new UserPublicKey();
            upk.setUserId(savedUser.getId()); // ID del usuario recién guardado
            upk.setPublicKeyPem(request.getPublicKey());
            userPublicKeyRepository.save(upk);
            log.info("Clave pública guardada para usuario ID: {}", savedUser.getId());
        } else {
            // Esto es un problema de lógica de cliente, debería ser obligatorio
            log.warn("No se proporcionó clave pública durante el registro para usuario: {}", savedUser.getUsername());
        }

        // 6. Generar token JWT para el nuevo usuario
        String token = jwtService.generateToken(savedUser);
        log.info("Token JWT generado para usuario: {}", savedUser.getUsername());

        // 7. Devolver solo el token en la respuesta de registro.
        // No devuelvo los datos de la clave cifrada aquí, solo en el login.
        return AuthResponse.builder()
                .token(token)
                .build();
    }

    /**
     * Autentica a un usuario y le devuelve un token JWT
     * junto con los datos necesarios para descifrar su clave privada.
     *
     * @param request DTO con username y password.
     * @return AuthResponse con JWT y los campos de cifrado (kekSalt, encryptedPrivateKey, kekIv).
     */
    public AuthResponse login(LoginRequest request) {
        log.info("Iniciando login para usuario: {}", request.getUsername());

        // 1. Buscar usuario por nombre de usuario
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.warn("Intento de login fallido: Usuario {} no encontrado.", request.getUsername());
                    // Lanzo UsernameNotFoundException, mi GlobalExceptionHandler la convierte en 404
                    // y devuelve un mensaje genérico.
                    return new UsernameNotFoundException("Usuario o contraseña incorrectos.");
                });

        // 2. Verificar la contraseña usando el hash almacenado
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Intento de login fallido: Contraseña incorrecta para usuario {}.", request.getUsername());
            // Lanzo BadCredentialsException, mi GlobalExceptionHandler la convierte en 401
            // y devuelve un mensaje genérico.
            throw new BadCredentialsException("Usuario o contraseña incorrectos.");
        }
        log.info("Contraseña verificada para usuario: {}", request.getUsername());

        // 3. Generar token JWT
        String token = jwtService.generateToken(user);
        log.info("Token JWT generado para usuario: {}", request.getUsername());

        // 4. Construir la respuesta CON los datos de la clave cifrada
        log.debug("Recuperando datos de clave cifrada para usuario: {}", request.getUsername());
        AuthResponse response = AuthResponse.builder()
                .token(token)
                // --- Devuelvo los campos que el cliente necesita para descifrar su clave privada ---
                .kekSalt(user.getKekSalt())
                .encryptedPrivateKey(user.getEncryptedPrivateKey())
                .kekIv(user.getKekIv())
                // ---------------------------------------------------------------------------------
                .build();

        // Validar que los campos no sean nulos (¡importante!)
        if (response.getKekSalt() == null || response.getEncryptedPrivateKey() == null || response.getKekIv() == null) {
            log.error("¡Error crítico! Faltan datos de clave cifrada en la base de datos para el usuario: {}", user.getUsername());
            // Si esto pasa, el cliente no puede descifrar su clave, es un error grave.
            throw new RuntimeException("Error interno: Faltan datos de seguridad para el usuario.");
        }


        log.info("Login exitoso para usuario: {}", request.getUsername());
        return response;
    }

    /**
     * Permite a un usuario subir/actualizar su clave pública.
     *
     * @param username     El usuario (obtenido del token de autenticación).
     * @param publicKeyPem La nueva clave pública en formato PEM.
     */
    @Transactional
    public void uploadPublicKey(String username, String publicKeyPem) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Busca si ya existe una clave para actualizarla, o crea una nueva (upsert)
        UserPublicKey upk = userPublicKeyRepository.findByUserId(user.getId())
                .orElse(new UserPublicKey()); // Crea una nueva si no existe

        upk.setUserId(user.getId());
        upk.setPublicKeyPem(publicKeyPem);
        // 'updatedAt' se actualiza automáticamente en la entidad UserPublicKey

        userPublicKeyRepository.save(upk);
        log.info("Clave pública (re)guardada para usuario ID: {}", user.getId());
    }
}