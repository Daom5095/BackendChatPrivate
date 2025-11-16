package com.chatprivate.security;

import com.chatprivate.messaging.repository.ConversationParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Servicio centralizado para validar permisos de acceso.
 *
 * Este servicio es el "guardián" de mi aplicación. Todas las operaciones
 * que requieren permisos específicos (leer mensajes, enviar mensajes, etc.)
 * pasan primero por aquí.
 *
 * VENTAJAS de centralizar las validaciones:
 * 1. Evito repetir código en múltiples servicios
 * 2. Es más fácil mantener y actualizar la lógica de seguridad
 * 3. Puedo cambiar las reglas de permisos en un solo lugar
 * 4. Los logs de seguridad están centralizados
 *
 * IMPORTANTE: Todos los métodos lanzan AccessDeniedException si el
 * usuario NO tiene permiso. Estas excepciones son manejadas por mi
 * GlobalExceptionHandler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final ConversationParticipantRepository participantRepository;

    /**
     * Valida que un usuario sea participante de una conversación.
     *
     * Esta es la validación MÁS IMPORTANTE de toda la app.
     * Si un usuario no es participante de un chat, NO puede:
     * - Leer mensajes
     * - Enviar mensajes
     * - Ver quiénes son los otros participantes
     * - Modificar la conversación
     *
     * @param userId El ID del usuario a validar
     * @param conversationId El ID de la conversación
     * @throws AccessDeniedException Si el usuario NO es participante
     */
    public void validateIsParticipant(Long userId, Long conversationId) {
        // Busco en la BD si existe una relación entre el usuario y la conversación
        boolean isParticipant = participantRepository
                .existsByConversation_IdAndUserId(conversationId, userId);

        if (!isParticipant) {
            // ¡ACCESO DENEGADO!
            // Logueo el intento (esto es importante para auditoría de seguridad)
            log.warn("🚨 INTENTO DE ACCESO NO AUTORIZADO: Usuario {} intentó acceder a conversación {} sin ser participante",
                    userId, conversationId);

            // Lanzo la excepción que mi GlobalExceptionHandler convertirá en un 403 Forbidden
            throw new AccessDeniedException(
                    "No tienes permiso para acceder a esta conversación"
            );
        }

        // Si llego aquí, todo está OK
        log.debug("✅ Validación exitosa: Usuario {} es participante de conversación {}",
                userId, conversationId);
    }

    /**
     * Valida que un usuario sea el "owner" (dueño) de una conversación.
     *
     * Solo el owner puede:
     * - Añadir nuevos participantes
     * - Eliminar participantes (excepto que se eliminen a sí mismos)
     * - Cambiar el título del chat grupal
     * - Eliminar la conversación (si implementamos esa funcionalidad)
     *
     * @param userId El ID del usuario a validar
     * @param conversationId El ID de la conversación
     * @throws AccessDeniedException Si el usuario NO es el owner
     */
    public void validateIsOwner(Long userId, Long conversationId) {
        // Busco al usuario en la conversación
        boolean isOwner = participantRepository
                .findByConversation_IdAndUserId(conversationId, userId)
                .map(participant -> "owner".equalsIgnoreCase(participant.getRole()))
                .orElse(false); // Si no lo encuentro, obviamente no es owner

        if (!isOwner) {
            log.warn("🚨 INTENTO DE ACCIÓN PRIVILEGIADA: Usuario {} intentó realizar una acción de owner en conversación {} sin serlo",
                    userId, conversationId);

            throw new AccessDeniedException(
                    "Solo el dueño de la conversación puede realizar esta acción"
            );
        }

        log.debug("✅ Validación exitosa: Usuario {} es owner de conversación {}",
                userId, conversationId);
    }

    /**
     * Valida que un usuario pueda eliminar a otro participante.
     *
     * REGLAS:
     * 1. El owner puede eliminar a cualquiera (incluso a sí mismo)
     * 2. Un member solo puede eliminarse a sí mismo (abandonar el chat)
     * 3. Un member NO puede eliminar a otros
     *
     * @param requesterId El ID del usuario que quiere eliminar
     * @param targetUserId El ID del usuario a eliminar
     * @param conversationId El ID de la conversación
     * @throws AccessDeniedException Si el usuario NO tiene permiso para eliminar
     */
    public void validateCanRemoveParticipant(Long requesterId, Long targetUserId, Long conversationId) {
        // Primero, valido que el requester sea participante
        validateIsParticipant(requesterId, conversationId);

        // Obtengo el rol del requester
        String requesterRole = participantRepository
                .findByConversation_IdAndUserId(conversationId, requesterId)
                .map(participant -> participant.getRole())
                .orElse("unknown");

        // CASO 1: El owner puede eliminar a cualquiera
        if ("owner".equalsIgnoreCase(requesterRole)) {
            log.debug("✅ Usuario {} (owner) puede eliminar al usuario {} de conversación {}",
                    requesterId, targetUserId, conversationId);
            return; // OK
        }

        // CASO 2: Un member solo puede eliminarse a sí mismo
        if (requesterId.equals(targetUserId)) {
            log.debug("✅ Usuario {} puede eliminarse a sí mismo de conversación {}",
                    requesterId, conversationId);
            return; // OK (abandonar el chat)
        }

        // CASO 3: Un member intenta eliminar a otro → ¡DENEGADO!
        log.warn("🚨 INTENTO DE ELIMINACIÓN NO AUTORIZADA: Usuario {} (role: {}) intentó eliminar al usuario {} de conversación {}",
                requesterId, requesterRole, targetUserId, conversationId);

        throw new AccessDeniedException(
                "No tienes permiso para eliminar a este participante"
        );
    }

    /**
     * Valida que el usuario actual pueda leer el historial de mensajes.
     *
     * Por ahora, simplemente valida que sea participante.
     * En el futuro, aquí podríamos añadir lógica adicional, como:
     * - Validar que el usuario no esté bloqueado
     * - Validar que el chat no esté archivado
     * - Validar límites de lectura por tiempo
     *
     * @param userId El ID del usuario
     * @param conversationId El ID de la conversación
     * @throws AccessDeniedException Si el usuario NO tiene permiso de lectura
     */
    public void validateCanReadMessages(Long userId, Long conversationId) {
        // Por ahora, solo valido que sea participante
        validateIsParticipant(userId, conversationId);

        // Aquí podría añadir validaciones adicionales en el futuro
        // Por ejemplo:
        // - if (isUserBlocked(userId, conversationId)) throw ...
        // - if (isConversationArchived(conversationId)) throw ...
    }

    /**
     * Valida que el usuario actual pueda enviar mensajes.
     *
     * Similar a validateCanReadMessages, pero podría tener reglas diferentes.
     * Por ejemplo:
     * - Un usuario puede leer un chat aunque esté muteado
     * - Pero NO puede enviar mensajes si está muteado
     *
     * @param userId El ID del usuario
     * @param conversationId El ID de la conversación
     * @throws AccessDeniedException Si el usuario NO tiene permiso de escritura
     */
    public void validateCanSendMessages(Long userId, Long conversationId) {
        // Por ahora, solo valido que sea participante
        validateIsParticipant(userId, conversationId);

        // En el futuro, podría añadir:
        // - if (isUserMuted(userId, conversationId)) throw ...
        // - if (hasExceededMessageLimit(userId)) throw ...
    }
}