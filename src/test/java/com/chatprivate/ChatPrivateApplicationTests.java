package com.chatprivate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles; // <-- IMPORTA ESTO

@SpringBootTest
@ActiveProfiles("test")
class ChatPrivateApplicationTests {

    @Test
    void contextLoads() {
        // Esta prueba simple verifica que la aplicación
        // puede arrancar con el perfil "test"
    }
}