package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

/**
 * Repositorio para operaciones de BD sobre Mensajes.
 *
 * ACTUALIZADO EN SESIÓN 3 - PARTE B:
 * - Añadida paginación para historial de mensajes
 * - Queries optimizadas con los índices de V2
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Busca todos los mensajes de una conversación, ordenados por fecha.
     *
     * OPTIMIZACIÓN:
     * - Usa el índice idx_messages_conversation_created (conversation_id, created_at)
     * - La ordenación es gratis porque el índice ya está ordenado
     *
     * USO TÍPICO: Cargar historial completo (para conversaciones pequeñas)
     */
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * Busca mensajes de una conversación con PAGINACIÓN.
     *
     * VENTAJAS:
     * - No carga miles de mensajes de golpe
     * - Permite "infinite scroll" en el frontend
     * - Reduce memoria y ancho de banda
     *
     * EJEMPLO DE USO:
     * ```java
     * Pageable pageable = PageRequest.of(0, 50, Sort.by("createdAt").ascending());
     * Page<Message> page = messageRepository.findByConversationId(convId, pageable);
     * ```
     *
     * @param conversationId ID de la conversación
     * @param pageable Configuración de paginación (página, tamaño, orden)
     * @return Página de mensajes
     */
    Page<Message> findByConversationId(Long conversationId, Pageable pageable);

    /**
     * Busca el último mensaje de una conversación.
     *
     * OPTIMIZACIÓN:
     * - Usa el índice idx_messages_conversation
     * - Solo trae 1 registro (LIMIT 1 implícito)
     *
     * USO TÍPICO: Mostrar preview en lista de conversaciones
     */
    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

    /**
     * Busca mensajes por IDs (para cargar claves en batch).
     *
     * OPTIMIZACIÓN:
     * - Usa el índice PRIMARY KEY
     * - Carga múltiples mensajes en una sola query (evita N queries)
     *
     * USO TÍPICO: Cargar claves de múltiples mensajes a la vez
     */
    List<Message> findByIdIn(List<Long> messageIds);

    /**
     * Cuenta cuántos mensajes tiene una conversación.
     *
     * OPTIMIZACIÓN:
     * - Solo cuenta, no carga datos (COUNT(*) es rápido)
     * - Usa el índice idx_messages_conversation
     *
     * USO TÍPICO: Mostrar "X mensajes" en la UI
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId")
    long countByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Busca mensajes de un usuario en una conversación.
     *
     * OPTIMIZACIÓN:
     * - MySQL decidirá entre idx_messages_conversation y idx_messages_sender
     *
     * USO TÍPICO: Buscar "mis mensajes" en un chat
     */
    List<Message> findByConversationIdAndSenderId(Long conversationId, Long senderId);


}