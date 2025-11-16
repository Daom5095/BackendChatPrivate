package com.chatprivate.user;

import com.chatprivate.auth.AuthResponse;
import com.chatprivate.auth.LoginRequest;
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
}