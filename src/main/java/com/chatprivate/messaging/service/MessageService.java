package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList; // Asegúrate que esta importación esté
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserRepository userRepository;
    private final SimpUserRegistry simpUserRegistry;

    @Autowired
    public MessageService(MessageRepository messageRepository,
                          MessageKeyRepository messageKeyRepository,
                          SimpMessagingTemplate simpMessagingTemplate,
                          UserRepository userRepository,
                          @Lazy SimpUserRegistry simpUserRegistry) {
        this.messageRepository = messageRepository;
        this.messageKeyRepository = messageKeyRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userRepository = userRepository;
        this.simpUserRegistry = simpUserRegistry;
    }

    @Transactional
    // --- CORRECCIÓN 1: El parámetro DEBE ser Map<String, String> ---
    public void sendAndStoreMessage(Long senderId, Long conversationId, String ciphertext, Map<String, String> encryptedKeys) {
        // 1. Guardar mensaje principal
        Conversation conv = new Conversation();
        conv.setId(conversationId);
        Message message = new Message();
        message.setConversation(conv);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);
        message = messageRepository.save(message);

        // 2. Obtener IDs (las claves String se convierten a Long)
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            System.err.println("Error: encryptedKeys map está vacío o nulo para convId: " + conversationId);
            return;
        }

        // --- CORRECCIÓN 2: .map(Long::parseLong) AHORA ES CORRECTO ---
        // porque encryptedKeys.keySet() devuelve Set<String>
        List<Long> recipientIds = encryptedKeys.keySet().stream()
                .map(Long::parseLong) // Convertir claves String a Long
                .collect(Collectors.toList());
        // --- FIN CORRECCIÓN 2 ---

        // 3. Obtener usernames
        Map<Long, String> userIdToUsernameMap = userRepository.findAllById(recipientIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // 4. Iterar, guardar MessageKey y ENVIAR
        for (Long recipientId : recipientIds) {
            // Usar clave String para buscar en el mapa original
            String encryptedKeyForRecipient = encryptedKeys.get(recipientId.toString());
            String recipientUsername = userIdToUsernameMap.get(recipientId);

            if (recipientUsername != null && encryptedKeyForRecipient != null) {
                // Guardar MessageKey
                MessageKey mk = new MessageKey();
                mk.setMessage(message);
                mk.setRecipientId(recipientId);
                mk.setEncryptedKey(encryptedKeyForRecipient);
                messageKeyRepository.save(mk);

                // Crear payload
                StompMessagePayload payload = new StompMessagePayload();
                payload.setConversationId(conversationId);
                payload.setCiphertext(ciphertext);
                payload.setSenderId(senderId);
                // El DTO espera Map<String, String>
                payload.setEncryptedKeys(Map.of(recipientId.toString(), encryptedKeyForRecipient));

                // VERIFICACIÓN CON SimpUserRegistry
                SimpUser user = simpUserRegistry.getUser(recipientUsername);
                if (user != null && user.hasSessions()) {
                    System.out.println("Usuario '" + recipientUsername + "' encontrado en SimpUserRegistry con " + user.getSessions().size() + " sesión(es). Intentando enviar...");
                    simpMessagingTemplate.convertAndSendToUser(recipientUsername, "/queue/messages", payload);
                    System.out.println("Mensaje reenviado a usuario: " + recipientUsername + " (ID: " + recipientId + ")");
                } else {
                    System.err.println("Error Crítico: Usuario '" + recipientUsername + "' NO encontrado en SimpUserRegistry o sin sesiones activas. No se puede enviar mensaje.");
                    if (user != null) {
                        System.err.println("Usuario '" + recipientUsername + "' encontrado pero user.hasSessions() es false.");
                    }
                }

            } else {
                System.err.println("No se pudo encontrar username para ID: " + recipientId + " o falta la clave cifrada.");
            }
        }
    }
}