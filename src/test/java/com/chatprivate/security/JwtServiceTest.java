package com.chatprivate.security;

import com.chatprivate.user.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/** Aquí pruebo la creación y validación de los tokens JWT. */
@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@test.com")
                .password("hashedPass")
                .build();
    }

    // Compruebo que se genere un token con contenido.
    @Test
    void generateToken_ShouldReturnNonEmptyToken() {
        // ACT
        String token = jwtService.generateToken(testUser);

        // ASSERT
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT tiene 3 partes separadas por '.'
    }

    // Compruebo que se pueda leer el usuario guardado en el token.
    @Test
    void extractUsername_ShouldReturnCorrectUsername() {
        // ARRANGE
        String token = jwtService.generateToken(testUser);

        // ACT
        String extractedUsername = jwtService.extractUsername(token);

        // ASSERT
        assertEquals("testuser", extractedUsername);
    }

    // Compruebo que un token vigente del usuario sea válido.
    @Test
    void isTokenValid_ShouldReturnTrue_WhenTokenIsValid() {
        // ARRANGE
        String token = jwtService.generateToken(testUser);
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("testuser")
                .password("hashedPass")
                .authorities("USER")
                .build();

        // ACT
        boolean isValid = jwtService.isTokenValid(token, userDetails);

        // ASSERT
        assertTrue(isValid);
    }

    // Compruebo que el token no sirva para otro usuario.
    @Test
    void isTokenValid_ShouldReturnFalse_WhenUsernameDoesNotMatch() {
        // ARRANGE
        String token = jwtService.generateToken(testUser);
        UserDetails differentUser = org.springframework.security.core.userdetails.User
                .withUsername("differentuser") // Username diferente
                .password("hashedPass")
                .authorities("USER")
                .build();

        // ACT
        boolean isValid = jwtService.isTokenValid(token, differentUser);

        // ASSERT
        assertFalse(isValid);
    }

    // Compruebo que se rechace un token mal formado.
    @Test
    void extractUsername_ShouldThrowException_WhenTokenIsMalformed() {
        // ARRANGE
        String malformedToken = "esto.no.es.un.jwt.valido";

        // ACT & ASSERT
        assertThrows(JwtException.class, () -> {
            jwtService.extractUsername(malformedToken);
        });
    }

    // Compruebo que se rechace un token vacío.
    @Test
    void extractUsername_ShouldThrowException_WhenTokenIsEmpty() {
        // ACT & ASSERT
        assertThrows(Exception.class, () -> {
            jwtService.extractUsername("");
        });
    }

    // NOTA: Testear tokens expirados es complejo sin mockear el tiempo
    // En un proyecto real, usarías una librería como 'java-time' para
    // controlar el tiempo en tests. Por ahora, lo dejamos así.
}