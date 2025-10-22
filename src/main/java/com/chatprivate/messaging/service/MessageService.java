package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import com.chatprivate.user.User; // Importar User
import com.chatprivate.user.UserRepository; // Importar UserRepository
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List; // Importar List
import java.util.Map;
import java.util.Optional; // Importar Optional
import java.util.stream.Collectors; // Importar Collectors

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserRepository userRepository; // Añadir UserRepository

    public MessageService(MessageRepository messageRepository,
                          MessageKeyRepository messageKeyRepository,
                          SimpMessagingTemplate simpMessagingTemplate,
                          UserRepository userRepository) { // Añadir al constructor
        this.messageRepository = messageRepository;
        this.messageKeyRepository = messageKeyRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userRepository = userRepository; // Asignar
    }

    @Transactional
    // Asegurarse que el Map use String como clave, ya que JSON lo hace
    public void sendAndStoreMessage(Long senderId, Long conversationId, String ciphertext, Map<Long, String> encryptedKeys) {
        // 1. Guardar el mensaje principal
        Conversation conv = new Conversation();
        conv.setId(conversationId);
        Message message = new Message();
        message.setConversation(conv);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);
        message = messageRepository.save(message);

        // 2. Obtener los IDs de los destinatarios (las claves del mapa)
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            System.err.println("Error: encryptedKeys map está vacío o nulo para convId: " + conversationId);
            return; // No se puede continuar sin destinatarios
        }

        List<Long> recipientIds = encryptedKeys.keySet().stream()
                .map(Long::parseLong) // Convertir las claves String de nuevo a Long
                .collect(Collectors.toList());

        // 3. Buscar los objetos User para obtener sus usernames (más eficiente buscar todos juntos)
        Map<Long, String> userIdToUsernameMap = userRepository.findAllById(recipientIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // 4. Iterar y guardar MessageKey + ENVIAR usando USERNAME
        for (Long recipientId : recipientIds) {
            String encryptedKeyForRecipient = encryptedKeys.get(recipientId.toString()); // Obtener valor usando ID como String
            String recipientUsername = userIdToUsernameMap.get(recipientId);

            // Si encontramos el username y la clave cifrada...
            if (recipientUsername != null && encryptedKeyForRecipient != null) {
                // Guardar la MessageKey (como antes)
                MessageKey mk = new MessageKey();
                mk.setMessage(message);
                mk.setRecipientId(recipientId);
                mk.setEncryptedKey(encryptedKeyForRecipient);
                messageKeyRepository.save(mk);

                // Crear el payload
                StompMessagePayload payload = new StompMessagePayload();
                payload.setConversationId(conversationId);
                payload.setCiphertext(ciphertext);
                payload.setSenderId(senderId);
                // Enviamos solo la clave para este destinatario específico
                payload.setEncryptedKeys(Map.of(recipientId, encryptedKeyForRecipient));
                // ¡CAMBIO CLAVE! Enviar usando el USERNAME del destinatario
                simpMessagingTemplate.convertAndSendToUser(recipientUsername, "/queue/messages", payload);

                // --- PUNTO Y COMA AÑADIDO AQUÍ ---
                System.out.println("Mensaje reenviado a usuario: " + recipientUsername + " (ID: " + recipientId + ")");
                // --- FIN PUNTO Y COMA ---

            } else {
                System.err.println("No se pudo encontrar username para ID: " + recipientId + " o falta la clave cifrada. No se envió mensaje.");
            }
        }
    }
}