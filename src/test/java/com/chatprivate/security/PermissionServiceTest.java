package com.chatprivate.security;

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

@ExtendWith(MockitoExtension.class) // Activa Mockito
class PermissionServiceTest {

    @Mock // Crea una versión "falsa" de este repositorio
    private ConversationParticipantRepository participantRepository;

    @InjectMocks // Inyecta los Mocks de arriba en esta instancia real
    private PermissionService permissionService;

    // --- Tests para validateIsParticipant ---

    @Test
    void validateIsParticipant_ShouldDoNothing_WhenUserIsParticipant() {
        // ARRANGE (Preparar)
        Long userId = 1L;
        Long conversationId = 100L;
        // Le decimos al mock qué devolver
        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(true);

        // ACT (Ejecutar) & ASSERT (Verificar)
        // Verificamos que NO lance ninguna excepción
        assertDoesNotThrow(() -> {
            permissionService.validateIsParticipant(userId, conversationId);
        });

        // Verificamos que el repositorio fue llamado
        verify(participantRepository).existsByConversation_IdAndUserId(conversationId, userId);
    }

    @Test
    void validateIsParticipant_ShouldThrowAccessDenied_WhenUserIsNotParticipant() {
        // ARRANGE
        Long userId = 1L;
        Long conversationId = 100L;
        when(participantRepository.existsByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(false);

        // ACT & ASSERT
        // Verificamos que SÍ lance la excepción esperada
        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateIsParticipant(userId, conversationId);
        }, "No tienes permiso para acceder a esta conversación"); // Mensaje esperado

        // Verificamos que el log de advertencia se haya intentado ejecutar (aunque no lo veamos)
        verify(participantRepository).existsByConversation_IdAndUserId(conversationId, userId);
    }

    // --- Tests para validateIsOwner ---

    @Test
    void validateIsOwner_ShouldThrowAccessDenied_WhenUserIsNotOwner() {
        // ARRANGE
        Long userId = 1L;
        Long conversationId = 100L;
        // Simulamos que el usuario NO es "owner"
        when(participantRepository.findByConversation_IdAndUserId(conversationId, userId))
                .thenReturn(Optional.empty()); // Ni siquiera es participante

        // ACT & ASSERT
        assertThrows(AccessDeniedException.class, () -> {
            permissionService.validateIsOwner(userId, conversationId);
        });
    }

    // TODO: Añadir más tests para los otros métodos (validateCanRemoveParticipant, etc.)
}