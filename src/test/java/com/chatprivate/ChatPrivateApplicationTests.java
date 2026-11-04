package com.chatprivate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles; // <-- IMPORTA ESTO

/** Estas pruebas comprueban que la aplicación pueda cargar su contexto. */
@SpringBootTest
@ActiveProfiles("test")
class ChatPrivateApplicationTests {

    // Compruebo que Spring pueda iniciar el contexto de la aplicación.
    @Test
    void contextLoads() {
        // Esta prueba simple verifica que la aplicación
        // puede arrancar con el perfil "test"
    }
}