package com.chatprivate.user;

import com.chatprivate.auth.AuthResponse;
import com.chatprivate.auth.LoginRequest;
import com.chatprivate.auth.RegisterRequest;
import com.chatprivate.messaging.model.UserPublicKey;
import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import com.chatprivate.security.JwtService;
import com.chatprivate.security.SecurityAuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Aquí pruebo el inicio de sesión y las respuestas cuando las credenciales no son válidas. */
@ExtendWith(MockitoExtension.class) // Activa Mockito
class UserServiceTest {

    // --- Dependencias Falsas (Mocks) ---
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserPublicKeyRepository userPublicKeyRepository;
    @Mock
    private SecurityAuditLogger auditLogger;

    // --- La Clase Real que Estamos Probando ---
    @InjectMocks
    private UserService userService;

    // Un usuario de prueba que usaremos en los tests
    private User testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // ARRANGE (Preparar)
        // Este bloque se ejecuta ANTES de cada @Test
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedPassword123") // Contraseña ya hasheada
                .email("test@test.com")
                .kekSalt("testsalt")
                .encryptedPrivateKey("testkey")
                .kekIv("testiv")
                .build();

        loginRequest = new LoginRequest("testuser", "correctPassword");
    }

    // --- TEST 1: El "Happy Path" (Login Exitoso) ---
    // Compruebo que unas credenciales correctas permitan iniciar sesión.
    @Test
    void login_ShouldReturnToken_WhenCredentialsAreValid() {
        // ARRANGE (Continuación)
        // 1. Cuando se busque a "testuser", devuelve nuestro usuario de prueba
        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(testUser));

        // 2. Cuando se compare la contraseña, devuelve 'true' (son iguales)
        when(passwordEncoder.matches("correctPassword", "hashedPassword123"))
                .thenReturn(true);

        // 3. Cuando se genere el token, devuelve uno de prueba
        when(jwtService.generateToken(testUser))
                .thenReturn("fake.jwt.token");

        // ACT (Ejecutar)
        AuthResponse response = userService.login(loginRequest);

        // ASSERT (Verificar)
        assertNotNull(response);
        assertEquals("fake.jwt.token", response.getToken());
        assertEquals("testsalt", response.getKekSalt()); // Verifica que los datos de clave se devuelven
        assertEquals("testkey", response.getEncryptedPrivateKey());

        // Verifica que el log de auditoría de ÉXITO fue llamado
        verify(auditLogger).logLoginAttempt("testuser", true, "N/A", null);

        // Verifica que NUNCA se llamó al log de fallo
        verify(auditLogger, never()).logLoginAttempt(anyString(), eq(false), anyString(), anyString());
    }

    // --- TEST 2: "Sad Path" (Contraseña Incorrecta) ---
    // Compruebo que una contraseña equivocada no permita iniciar sesión.
    @Test
    void login_ShouldThrowBadCredentials_WhenPasswordIsWrong() {
        // ARRANGE
        loginRequest.setPassword("wrongPassword"); // <-- Contraseña incorrecta

        // 1. El usuario SÍ se encuentra
        when(userRepository.findByUsername("testuser"))
                .thenReturn(Optional.of(testUser));

        // 2. La contraseña NO coincide
        when(passwordEncoder.matches("wrongPassword", "hashedPassword123"))
                .thenReturn(false);

        // ACT & ASSERT
        // Verifica que SÍ lanza la excepción
        assertThrows(BadCredentialsException.class, () -> {
            userService.login(loginRequest);
        });

        // Verifica que el log de auditoría de FALLO fue llamado
        verify(auditLogger).logLoginAttempt("testuser", false, "N/A", "Contraseña incorrecta");

        // Verifica que NUNCA se llamó al log de éxito
        verify(auditLogger, never()).logLoginAttempt(anyString(), eq(true), anyString(), any());
    }

    // --- TEST 3: "Sad Path" (Usuario No Existe) ---
    // Compruebo la respuesta cuando la cuenta no existe.
    @Test
    void login_ShouldThrowUsernameNotFound_WhenUserDoesNotExist() {
        // ARRANGE
        loginRequest.setUsername("nonExistentUser");

        // 1. El usuario NO se encuentra
        when(userRepository.findByUsername("nonExistentUser"))
                .thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(UsernameNotFoundException.class, () -> {
            userService.login(loginRequest);
        });

        // Verifica que el log de auditoría de FALLO fue llamado
        verify(auditLogger).logLoginAttempt("nonExistentUser", false, "N/A", "Usuario no encontrado");
    }

    // No permito registrar un nombre de usuario que ya está tomado.
    @Test
    void register_ShouldFail_WhenUsernameAlreadyExists() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    // Tampoco permito registrar un email que ya esté registrado.
    @Test
    void register_ShouldFail_WhenEmailAlreadyExists() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> userService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    // Compruebo que el registro guarde la clave pública y devuelva un token.
    @Test
    void register_ShouldSavePublicKeyAndReturnToken() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(testUser)).thenReturn("fake.jwt.token");

        AuthResponse response = userService.register(request);

        assertEquals("fake.jwt.token", response.getToken());
        verify(userPublicKeyRepository).save(argThat(key ->
                testUser.getId().equals(key.getUserId())
                        && request.getPublicKey().equals(key.getPublicKeyPem())));
    }

    // Si el registro llega sin clave pública, guardo el usuario y dejo el aviso.
    @Test
    void register_ShouldLogWarning_WhenPublicKeyIsMissing() {
        RegisterRequest request = registerRequest();
        request.setPublicKey(null);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(testUser)).thenReturn("fake.jwt.token");

        assertEquals("fake.jwt.token", userService.register(request).getToken());

        verify(userPublicKeyRepository, never()).save(any(UserPublicKey.class));
        verify(auditLogger).logSuspiciousActivity(
                eq("Registro sin clave pública"), contains(testUser.getUsername()));
    }

    // Si faltan los datos cifrados de la clave privada, no devuelvo un login incompleto.
    @Test
    void login_ShouldFail_WhenEncryptedKeyDataIsMissing() {
        testUser.setKekSalt(null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", "hashedPassword123")).thenReturn(true);
        when(jwtService.generateToken(testUser)).thenReturn("fake.jwt.token");

        assertThrows(RuntimeException.class, () -> userService.login(loginRequest));
        verify(auditLogger).logSecurityError(contains("testuser"), any(RuntimeException.class));
    }

    // Al subir una clave nueva, la creo si el usuario todavía no tiene una guardada.
    @Test
    void uploadPublicKey_ShouldCreateKey_WhenUserHasNoKeyYet() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userPublicKeyRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

        userService.uploadPublicKey("testuser", "public-key-pem");

        verify(userPublicKeyRepository).save(argThat(key ->
                testUser.getId().equals(key.getUserId())
                        && "public-key-pem".equals(key.getPublicKeyPem())));
    }

    // Si el usuario ya tenía una clave pública, actualizo esa misma.
    @Test
    void uploadPublicKey_ShouldUpdateExistingKey() {
        UserPublicKey existingKey = new UserPublicKey();
        existingKey.setUserId(testUser.getId());
        existingKey.setPublicKeyPem("old-key");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userPublicKeyRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(existingKey));

        userService.uploadPublicKey("testuser", "new-key");

        verify(userPublicKeyRepository).save(existingKey);
        assertEquals("new-key", existingKey.getPublicKeyPem());
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@test.com");
        request.setPassword("password123");
        request.setPublicKey("public-key-pem");
        request.setKekSalt("salt");
        request.setEncryptedPrivateKey("encrypted-private-key");
        request.setKekIv("iv");
        return request;
    }
}
