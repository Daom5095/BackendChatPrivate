package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.MessageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageKeyRepository extends JpaRepository<MessageKey, Long> {
    List<MessageKey> findByRecipientId(Long recipientId);
    List<MessageKey> findByMessageId(Long messageId);

    // --- AÑADIR ESTE MÉTODO ---
    // Busca todas las claves para una lista de IDs de mensaje Y un destinatario específico
    List<MessageKey> findByMessage_IdInAndRecipientId(List<Long> messageIds, Long recipientId);
}