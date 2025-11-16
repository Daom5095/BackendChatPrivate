package com.chatprivate.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    // Nota: No usamos Mockito aquí, estamos probando la lógica interna
    private RateLimitService rateLimitService = new RateLimitService();

    @Test
    void tryConsumeLogin_ShouldAllow5Attempts_AndBlockThe6th() {
        String testIp = "1.2.3.4";

        // ARRANGE & ACT
        // Los primeros 5 intentos deben ser true
        assertTrue(rateLimitService.tryConsumeLogin(testIp), "Intento 1 fallido");
        assertTrue(rateLimitService.tryConsumeLogin(testIp), "Intento 2 fallido");
        assertTrue(rateLimitService.tryConsumeLogin(testIp), "Intento 3 fallido");
        assertTrue(rateLimitService.tryConsumeLogin(testIp), "Intento 4 fallido");
        assertTrue(rateLimitService.tryConsumeLogin(testIp), "Intento 5 fallido");

        long remaining = rateLimitService.getRemainingLoginAttempts(testIp);
        assertEquals(0, remaining);

        // El 6to intento debe ser false
        assertFalse(rateLimitService.tryConsumeLogin(testIp), "Intento 6 DEBIÓ fallar");

        // ASSERT
        long finalRemaining = rateLimitService.getRemainingLoginAttempts(testIp);
        assertEquals(0, finalRemaining);
    }
}