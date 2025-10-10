package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.MessageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageKeyRepository extends JpaRepository<MessageKey, Long> {
    List<MessageKey> findByRecipientId(Long recipientId);
    List<MessageKey> findByMessageId(Long messageId);
}
