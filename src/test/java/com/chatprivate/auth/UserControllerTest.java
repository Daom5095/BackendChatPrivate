package com.chatprivate.auth;

import com.chatprivate.user.CustomUserDetails;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Aquí compruebo qué datos de usuario devuelve la API al cliente. */
class UserControllerTest {

    private UserRepository userRepository;
    private UserController controller;

    @BeforeEach
    void setUp() {
        // Preparo el controlador con el repositorio simulado.
        userRepository = mock(UserRepository.class);
        controller = new UserController(userRepository);
    }

    @Test
    void getMe_returnsOnlyIdAndUsernameOfAuthenticatedUser() {
        // Reviso que el endpoint use la cuenta autenticada.
        User user = User.builder().id(12L).username("diego").email("private@test.com").build();
        var authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), null, List.of());

        var response = controller.getMe(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(12L, response.getBody().getId());
        assertEquals("diego", response.getBody().getUsername());
        verifyNoInteractions(userRepository);
    }

    @Test
    void getAllUsers_doesNotReturnTheCurrentUser() {
        // Compruebo que la lista no incluya al usuario que hizo la solicitud.
        User currentUser = User.builder().id(12L).username("diego").build();
        User otherUser = User.builder().id(19L).username("ana").build();
        when(userRepository.findAll()).thenReturn(List.of(currentUser, otherUser));
        var authentication = new UsernamePasswordAuthenticationToken("diego", null, List.of());

        var response = controller.getAllUsers(authentication);

        assertEquals(1, response.getBody().size());
        assertEquals(19L, response.getBody().get(0).getId());
        assertEquals("ana", response.getBody().get(0).getUsername());
    }

    @Test
    void getAllUsers_returnsEmptyListWhenThereAreNoOtherUsers() {
        // Compruebo el caso en que solo existe la cuenta actual.
        User currentUser = User.builder().id(12L).username("diego").build();
        when(userRepository.findAll()).thenReturn(List.of(currentUser));
        var authentication = new UsernamePasswordAuthenticationToken("diego", null, List.of());

        var response = controller.getAllUsers(authentication);

        assertTrue(response.getBody().isEmpty());
    }
}
