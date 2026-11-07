package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.AddParticipantRequest;
import com.chatprivate.messaging.dto.ConversationResponse;
import com.chatprivate.messaging.dto.MessageHistoryDto;
import com.chatprivate.messaging.dto.ParticipantDto;
import com.chatprivate.messaging.service.ConversationService;
import com.chatprivate.security.PermissionService;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Aquí pruebo que las rutas de conversaciones deleguen el trabajo correctamente. */
class ConversationControllerTest {

    private ConversationService conversationService;
    private UserRepository userRepository;
    private PermissionService permissionService;
    private ConversationController controller;
    private Authentication authentication;
    private User currentUser;

    @BeforeEach
    void setUp() {
        // Preparo un usuario autenticado y las dependencias simuladas.
        conversationService = mock(ConversationService.class);
        userRepository = mock(UserRepository.class);
        permissionService = mock(PermissionService.class);
        controller = new ConversationController(conversationService, userRepository, permissionService);
        authentication = new UsernamePasswordAuthenticationToken("diego", null, List.of());
        currentUser = User.builder().id(27L).username("diego").build();
        when(userRepository.findByUsername("diego")).thenReturn(Optional.of(currentUser));
    }

    @Test
    void createConversation_usesAuthenticatedUserAsCreator() {
        // Compruebo que la conversación se cree con el ID del usuario autenticado.
        var request = new com.chatprivate.messaging.dto.CreateConversationRequest();
        var expected = new ConversationResponse();
        when(conversationService.createConversation(request, 27L)).thenReturn(expected);

        var response = controller.createConversation(authentication, request);

        assertSame(expected, response.getBody());
        verify(conversationService).createConversation(request, 27L);
    }

    @Test
    void getUserConversations_returnsConversationsForCurrentUser() {
        // Compruebo que solo se consulten las conversaciones del usuario actual.
        List<ConversationResponse> expected = List.of(new ConversationResponse());
        when(conversationService.getUserConversations(27L)).thenReturn(expected);

        var response = controller.getUserConversations(authentication);

        assertSame(expected, response.getBody());
        verify(conversationService).getUserConversations(27L);
    }

    @Test
    void addParticipant_delegatesWithConversationAndRequesterIds() {
        // Reviso que el servicio reciba el chat, el usuario autenticado y el nuevo miembro.
        AddParticipantRequest request = new AddParticipantRequest();
        request.setUserId(34L);

        var response = controller.addParticipant(authentication, 81L, request);

        assertEquals(200, response.getStatusCode().value());
        verify(conversationService).addParticipant(81L, 27L, request);
    }

    @Test
    void removeParticipant_delegatesWithTargetUserId() {
        // Compruebo que se envíe al servicio a quién se quiere retirar del chat.
        var response = controller.removeParticipant(authentication, 81L, 34L);

        assertEquals(200, response.getStatusCode().value());
        verify(conversationService).removeParticipant(81L, 27L, 34L);
    }

    @Test
    void getParticipants_checksMembershipBeforeReturningList() {
        // Verifico que primero se valide que el usuario pertenece a la conversación.
        List<ParticipantDto> expected = List.of(new ParticipantDto());
        when(conversationService.getParticipants(81L)).thenReturn(expected);

        var response = controller.getParticipants(authentication, 81L);

        assertSame(expected, response.getBody());
        var order = inOrder(permissionService, conversationService);
        order.verify(permissionService).validateIsParticipant(27L, 81L);
        order.verify(conversationService).getParticipants(81L);
    }

    @Test
    void getMessageHistory_returnsHistoryForCurrentUser() {
        // Compruebo que el historial se solicite con el ID del usuario autenticado.
        List<MessageHistoryDto> expected = List.of(new MessageHistoryDto());
        when(conversationService.getMessageHistory(81L, 27L)).thenReturn(expected);

        var response = controller.getMessageHistory(authentication, 81L);

        assertSame(expected, response.getBody());
        verify(conversationService).getMessageHistory(81L, 27L);
    }

    @Test
    void getMessageHistoryPaged_usesDefaultSafeValuesWhenParametersAreOutOfRange() {
        // Si llegan valores fuera del rango permitido, compruebo que se normalicen.
        Page<MessageHistoryDto> expected = new PageImpl<>(List.of());
        when(conversationService.getMessageHistoryPaged(81L, 27L, 0, 50)).thenReturn(expected);

        var response = controller.getMessageHistoryPaged(authentication, 81L, -3, 500);

        assertSame(expected, response.getBody());
        verify(conversationService).getMessageHistoryPaged(81L, 27L, 0, 50);
    }

    @Test
    void getMessageHistoryPaged_keepsValidPageAndSize() {
        // Cuando los parámetros son válidos, compruebo que se conserven.
        Page<MessageHistoryDto> expected = new PageImpl<>(List.of());
        when(conversationService.getMessageHistoryPaged(81L, 27L, 2, 25)).thenReturn(expected);

        var response = controller.getMessageHistoryPaged(authentication, 81L, 2, 25);

        assertSame(expected, response.getBody());
        verify(conversationService).getMessageHistoryPaged(81L, 27L, 2, 25);
    }

    @Test
    void getUserConversations_throwsWhenAuthenticatedUserNoLongerExists() {
        // Compruebo el caso en que el usuario autenticado ya no está en la base de datos.
        when(userRepository.findByUsername("diego")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> controller.getUserConversations(authentication));
        verifyNoInteractions(conversationService);
    }
}
