package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.model.UserPublicKey;
import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.UserRepository;
import com.chatprivate.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Aquí pruebo las rutas REST que manejan las claves públicas. */
class MessagingRestControllerTest {

    private UserPublicKeyRepository publicKeyRepository;
    private UserService userService;
    private MessagingRestController controller;

    @BeforeEach
    void setUp() {
        // Preparo las dependencias que usa el controlador.
        publicKeyRepository = mock(UserPublicKeyRepository.class);
        MessageService messageService = mock(MessageService.class);
        UserRepository userRepository = mock(UserRepository.class);
        userService = mock(UserService.class);
        controller = new MessagingRestController(
                publicKeyRepository, messageService, userRepository, userService);
    }

    @Test
    void uploadPublicKey_savesKeyForAuthenticatedUsername() {
        // Compruebo que la clave se guarde asociada al usuario autenticado.
        var authentication = new UsernamePasswordAuthenticationToken("diego", null, List.of());

        var response = controller.uploadPublicKey(authentication, "clave-publica-de-prueba");

        assertEquals(200, response.getStatusCode().value());
        verify(userService).uploadPublicKey("diego", "clave-publica-de-prueba");
    }

    @Test
    void getPublicKey_returnsKeyWhenItExists() {
        // Reviso que se devuelva la clave pública guardada para el usuario.
        UserPublicKey key = new UserPublicKey();
        key.setUserId(12L);
        key.setPublicKeyPem("clave-publica-de-prueba");
        when(publicKeyRepository.findById(12L)).thenReturn(Optional.of(key));

        var response = controller.getPublicKey(12L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("clave-publica-de-prueba", response.getBody());
    }

    @Test
    void getPublicKey_returnsNotFoundWhenThereIsNoKey() {
        // Compruebo que una clave inexistente responda HTTP 404.
        when(publicKeyRepository.findById(44L)).thenReturn(Optional.empty());

        var response = controller.getPublicKey(44L);

        assertEquals(404, response.getStatusCode().value());
        assertNull(response.getBody());
    }
}
