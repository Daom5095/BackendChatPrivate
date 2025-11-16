package com.chatprivate.auth;

import static org.hamcrest.Matchers.containsString;
import com.chatprivate.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.chatprivate.security.RateLimitService;
import org.junit.jupiter.api.AfterEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc // Configura MockMvc para simular peticiones HTTP
@ActiveProfiles("test") // ¡MUY IMPORTANTE!
@Transactional // Revierte la BD después de cada test
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc; // El simulador de peticiones HTTP

    @Autowired
    private ObjectMapper objectMapper; // Para convertir objetos Java a JSON

    @Autowired
    private RateLimitService rateLimitService; // <-- ¡ESTA ERA LA LÍNEA FALTANTE!

    /**
     * Este método se ejecuta DESPUÉS de CADA test en esta clase.
     * Limpia los buckets de rate limit para que los tests
     * no interfieran entre sí.
     */
    @AfterEach
    void tearDown() {
        rateLimitService.cleanupOldBuckets();
    }

    @Test
    void register_ShouldCreateUser_AndReturnToken() throws Exception {
        // ARRANGE
        // Datos de registro válidos
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setEmail("test@user.com");
        req.setPassword("password123");
        req.setPublicKey("---PUBLIC KEY---");
        req.setKekSalt("salt");
        req.setEncryptedPrivateKey("encKey");
        req.setKekIv("iv");

        // ACT
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))

                // ASSERT
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty()); // Verifica que la respuesta JSON tiene un token
    }

    @Test
    void login_ShouldBeRateLimited_After5FailedAttempts() throws Exception {
        // ARRANGE
        LoginRequest req = new LoginRequest("usuario-no-existe", "pass-incorrecto");
        String jsonRequest = objectMapper.writeValueAsString(req);

        // ACT
        // Hacemos 5 intentos fallidos (esperamos 401 o 404, no importa)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonRequest));
            // No validamos el status, solo consumimos el bucket
        }

        // ASSERT
        // El 6to intento DEBE devolver 429 Too Many Requests
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))

                .andExpect(status().isTooManyRequests()) // HTTP 429
                .andExpect(jsonPath("$.error").value(containsString("Has excedido el límite")));
    }

    @Test
    void register_ShouldBeRateLimited_After3SuccessfulAttempts() throws Exception {
        // ARRANGE
        RegisterRequest req = new RegisterRequest();
        req.setPassword("password123");
        req.setPublicKey("---PUBLIC KEY---");
        req.setKekSalt("salt");
        req.setEncryptedPrivateKey("encKey");
        req.setKekIv("iv");

        // ACT
        // 1. Hacemos 3 registros exitosos (consumimos los 3 tokens)
        // CADA INTENTO DEBE TENER USERNAME Y EMAIL ÚNICOS

        req.setUsername("regUser1");
        req.setEmail("reg1@test.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        req.setUsername("regUser2");
        req.setEmail("reg2@test.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        req.setUsername("regUser3");
        req.setEmail("reg3@test.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // ASSERT
        // 2. El 4to intento DEBE ser bloqueado con 429
        req.setUsername("regUser4");
        req.setEmail("reg4@test.com");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))

                .andExpect(status().isTooManyRequests()) // HTTP 429
                .andExpect(jsonPath("$.error").value(containsString("Has excedido el límite de registros")));
    }
}