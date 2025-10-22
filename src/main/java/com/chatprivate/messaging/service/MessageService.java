package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public MessageService(MessageRepository messageRepository,
                          MessageKeyRepository messageKeyRepository,
                          SimpMessagingTemplate simpMessagingTemplate) {
        this.messageRepository = messageRepository;
        this.messageKeyRepository = messageKeyRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Transactional
    public void sendAndStoreMessage(Long senderId, Long conversationId, String ciphertext, Map<Long,String> encryptedKeys) {
        // Construimos el message y lo persistimos
        Conversation conv = new Conversation();
        conv.setId(conversationId);
        Message message = new Message();
        message.setConversation(conv);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);
        message = messageRepository.save(message);

        // Guardamos claves cifradas por destinatario y enviamos por WebSocket a cada user
        for (Long recipientId : encryptedKeys.keySet()) {
    String encKey = encryptedKeys.get(recipientId); // Obtiene el valor (placeholder por ahora)

    StompMessagePayload payload = new StompMessagePayload();
    payload.setConversationId(conversationId);
    payload.setCiphertext(ciphertext);
    payload.setSenderId(senderId);
    // payload.setEncryptedKeys(...); // Podrías enviar solo la clave de este destinatario

    // Envía al destinatario específico
    simpMessagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", payload);
}
    }
}
