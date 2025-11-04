package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * Busca el mensaje más reciente (TOP 1) para un ID de conversación
     * ordenando por fecha de creación descendente.
     */
    Optional<Message> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);

}