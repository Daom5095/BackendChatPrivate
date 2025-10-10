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
        conv.setId(conversationId); // si conversacion existe, lo ideal es buscarla; aquí asumimos que existe
        Message message = new Message();
        message.setConversation(conv);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);
        message = messageRepository.save(message);

        // Guardamos claves cifradas por destinatario y enviamos por WebSocket a cada user
        for (Map.Entry<Long,String> e : encryptedKeys.entrySet()) {
            Long recipientId = e.getKey();
            String encKey = e.getValue();

            MessageKey mk = new MessageKey();
            mk.setMessage(message);
            mk.setRecipientId(recipientId);
            mk.setEncryptedKey(encKey);
            messageKeyRepository.save(mk);

            // Enviamos al usuario usando destination /user/{principalName}/queue/messages
            StompMessagePayload payload = new StompMessagePayload();
            payload.setConversationId(conversationId);
            payload.setCiphertext(ciphertext);
            payload.setSenderId(senderId);
            payload.setEncryptedKeys(Map.of(recipientId, encKey)); // payload con la key para ese usuario

            // Enviar a user: el principal que puso el interceptor debe devolver .getName() igual a recipientId.toString() o username
            simpMessagingTemplate.convertAndSendToUser(recipientId.toString(), "/queue/messages", payload);
        }
    }
}
