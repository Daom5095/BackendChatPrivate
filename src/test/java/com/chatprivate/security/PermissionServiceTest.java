package com.chatprivate.security;

import com.chatprivate.messaging.model.ConversationParticipant;
import com.chatprivate.messaging.repository.ConversationParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Aquí pruebo los permisos para participar, leer, escribir y administrar conversaciones. */
@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private ConversationParticipantRepository participantRepository;

    @InjectMocks
    private PermissionService permissionService;

    // ==============================================
    // TESTS PARA validateIsParticipant
    // ==============================================

    // Compruebo que se permita continuar si el usuario participa en el chat.
    @Test
    void validateIsParticipant_ShouldDoNothing_WhenUserIsParticipant() {
        Long userId = 1L;
        Long conversationId = 100L;
        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(true);

        assertDoesNotThrow(() -> {
            permissionService.validateIsParticipant(userId, conversationId);
        });

        verify(participantRepository).existsByConversation_IdAndUserId(conversationId, userId);
    }

    // Compruebo que se bloquee a quien no participa en el chat.
    @Test
    void validateIsParticipant_ShouldThrowAccessDenied_WhenUserIsNotParticipant() {
        Long userId = 1L;
        Long conversationId = 100L;
        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateIsParticipant(userId, conversationId);
        });

        verify(participantRepository).existsByConversation_IdAndUserId(conversationId, userId);
    }

    // ==============================================
    // TESTS PARA validateIsOwner
    // ==============================================

    // Compruebo que el propietario pueda hacer acciones de administración.
    @Test
    void validateIsOwner_ShouldDoNothing_WhenUserIsOwner() {
        Long userId = 1L;
        Long conversationId = 100L;

        ConversationParticipant mockParticipant = new ConversationParticipant();
        mockParticipant.setUserId(userId);
        mockParticipant.setRole("owner");

        when(participantRepository.findByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(mockParticipant));

        assertDoesNotThrow(() -> {
            permissionService.validateIsOwner(userId, conversationId);
        });
    }

    // Compruebo que un participante normal no tenga permisos de propietario.
    @Test
    void validateIsOwner_ShouldThrowAccessDenied_WhenUserIsNotOwner() {
        Long userId = 1L;
        Long conversationId = 100L;

        ConversationParticipant mockParticipant = new ConversationParticipant();
        mockParticipant.setUserId(userId);
        mockParticipant.setRole("member"); // NO es owner

        when(participantRepository.findByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(mockParticipant));

        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateIsOwner(userId, conversationId);
        });
    }

    // Compruebo que alguien ajeno al chat no pueda administrarlo.
    @Test
    void validateIsOwner_ShouldThrowAccessDenied_WhenUserIsNotParticipant() {
        Long userId = 1L;
        Long conversationId = 100L;

        when(participantRepository.findByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty()); // No es participante

        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateIsOwner(userId, conversationId);
        });
    }

    // ==============================================
    // TESTS PARA validateCanRemoveParticipant
    // ==============================================

    // Compruebo que el propietario pueda quitar a otro participante.
    @Test
    void validateCanRemoveParticipant_ShouldAllow_WhenRequesterIsOwner() {
        Long ownerId = 1L;
        Long targetUserId = 2L;
        Long conversationId = 100L;

        ConversationParticipant ownerParticipant = new ConversationParticipant();
        ownerParticipant.setUserId(ownerId);
        ownerParticipant.setRole("owner");

        when(participantRepository.existsByConversation_IdAndUserId(conversationId, ownerId))
                .thenReturn(true);
        when(participantRepository.findByConversation_IdAndUserId(conversationId, ownerId))
                .thenReturn(Optional.of(ownerParticipant));

        assertDoesNotThrow(() -> {
            permissionService.validateCanRemoveParticipant(ownerId, targetUserId, conversationId);
        });
    }

    // Compruebo que un miembro pueda quitarse a sí mismo.
    @Test
    void validateCanRemoveParticipant_ShouldAllow_WhenMemberRemovesThemselves() {
        Long memberId = 1L;
        Long conversationId = 100L;

        ConversationParticipant memberParticipant = new ConversationParticipant();
        memberParticipant.setUserId(memberId);
        memberParticipant.setRole("member");

        when(participantRepository.existsByConversation_IdAndUserId(conversationId, memberId))
                .thenReturn(true);
        when(participantRepository.findByConversation_IdAndUserId(conversationId, memberId))
                .thenReturn(Optional.of(memberParticipant));

        assertDoesNotThrow(() -> {
            permissionService.validateCanRemoveParticipant(memberId, memberId, conversationId);
        });
    }

    // Compruebo que un miembro no pueda quitar a otra persona.
    @Test
    void validateCanRemoveParticipant_ShouldThrow_WhenMemberTriesToRemoveOther() {
        Long memberId = 1L;
        Long targetUserId = 2L;
        Long conversationId = 100L;

        ConversationParticipant memberParticipant = new ConversationParticipant();
        memberParticipant.setUserId(memberId);
        memberParticipant.setRole("member");

        when(participantRepository.existsByConversation_IdAndUserId(conversationId, memberId))
                .thenReturn(true);
        when(participantRepository.findByConversation_IdAndUserId(conversationId, memberId))
                .thenReturn(Optional.of(memberParticipant));

        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateCanRemoveParticipant(memberId, targetUserId, conversationId);
        });
    }

    // ==============================================
    // TESTS PARA validateCanReadMessages
    // ==============================================

    // Compruebo que un participante pueda leer mensajes.
    @Test
    void validateCanReadMessages_ShouldWork_WhenUserIsParticipant() {
        Long userId = 1L;
        Long conversationId = 100L;

        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(true);

        assertDoesNotThrow(() -> {
            permissionService.validateCanReadMessages(userId, conversationId);
        });
    }

    // Compruebo que alguien ajeno no pueda leer mensajes.
    @Test
    void validateCanReadMessages_ShouldThrow_WhenUserIsNotParticipant() {
        Long userId = 1L;
        Long conversationId = 100L;

        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateCanReadMessages(userId, conversationId);
        });
    }

    // ==============================================
    // TESTS PARA validateCanSendMessages
    // ==============================================

    // Compruebo que un participante pueda enviar mensajes.
    @Test
    void validateCanSendMessages_ShouldWork_WhenUserIsParticipant() {
        Long userId = 1L;
        Long conversationId = 100L;

        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(true);

        assertDoesNotThrow(() -> {
            permissionService.validateCanSendMessages(userId, conversationId);
        });
    }

    // Compruebo que alguien ajeno no pueda enviar mensajes.
    @Test
    void validateCanSendMessages_ShouldThrow_WhenUserIsNotParticipant() {
        Long userId = 1L;
        Long conversationId = 100L;

        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateCanSendMessages(userId, conversationId);
        });
    }
}