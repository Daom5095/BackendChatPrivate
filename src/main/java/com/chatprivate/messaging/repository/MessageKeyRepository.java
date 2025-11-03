package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.MessageKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional; // <-- AÑADIR IMPORT

public interface MessageKeyRepository extends JpaRepository<MessageKey, Long> {
    List<MessageKey> findByRecipientId(Long recipientId);
    List<MessageKey> findByMessageId(Long messageId);

    List<MessageKey> findByMessage_IdInAndRecipientId(List<Long> messageIds, Long recipientId);

    // --- CAMBIO: AÑADIDO ESTE MÉTODO ---
    /**
     * Busca la clave específica para un ID de mensaje y un ID de destinatario.
     */
    Optional<MessageKey> findByMessage_IdAndRecipientId(Long messageId, Long recipientId);
    // --- FIN DEL CAMBIO ---
}