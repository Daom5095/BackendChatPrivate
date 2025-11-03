package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional; // <-- AÑADIR IMPORT

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    // --- CAMBIO: AÑADIDO ESTE MÉTODO ---
    /**
     * Busca el mensaje más reciente (TOP 1) para un ID de conversación
     * ordenando por fecha de creación descendente.
     */
    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);
    // --- FIN DEL CAMBIO ---
}