package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import lombok.RequiredArgsConstructor; // Importar
import lombok.extern.slf4j.Slf4j; // Importar
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
@RequiredArgsConstructor // Usamos Lombok para la inyección
@Slf4j // Añade el objeto 'log' automáticamente
public class MessageService {

    // Convertidos a 'final'
    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserRepository userRepository;
    private final SimpUserRegistry simpUserRegistry;

    // Eliminamos el constructor manual (Lombok lo crea)
    // NOTA: @Lazy se debe poner en el parámetro del constructor,
    // por lo que debemos volver al constructor manual solo por SimpUserRegistry

    // --- CORRECCIÓN a la mejora de Lombok ---
    // @RequiredArgsConstructor no funciona con @Lazy en el campo.
    // Volvemos al constructor manual SOLO para esta clase.
    /*
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
    */
    // (Tu constructor original estaba bien, mantenlo y añade @Slf4j a la clase)
    // --- FIN CORRECCIÓN ---


    @Transactional
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
        // --- INICIO MEJORA ERROR ---
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            // Reemplazamos System.err por log.error y lanzamos una excepción
            log.error("Error: encryptedKeys map está vacío o nulo para convId: {}", conversationId);
            // Esto detendrá la ejecución y le dirá al cliente que algo salió mal
            throw new IllegalArgumentException("El mapa de claves cifradas no puede estar vacío.");
        }
        // --- FIN MEJORA ERROR ---

        List<Long> recipientIds = encryptedKeys.keySet().stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 3. Obtener usernames
        Map<Long, String> userIdToUsernameMap = userRepository.findAllById(recipientIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // 4. Iterar, guardar MessageKey y ENVIAR
        for (Long recipientId : recipientIds) {
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
                payload.setEncryptedKeys(Map.of(recipientId.toString(), encryptedKeyForRecipient));

                // VERIFICACIÓN CON SimpUserRegistry
                SimpUser user = simpUserRegistry.getUser(recipientUsername);
                if (user != null && user.hasSessions()) {
                    // Reemplazamos System.out por log.info
                    log.info("Usuario '{}' encontrado en SimpUserRegistry con {} sesión(es). Intentando enviar...", recipientUsername, user.getSessions().size());
                    simpMessagingTemplate.convertAndSendToUser(recipientUsername, "/queue/messages", payload);
                    log.info("Mensaje reenviado a usuario: {} (ID: {})", recipientUsername, recipientId);
                } else {
                    // Reemplazamos System.err por log.warn (es un error, pero no rompe la app)
                    log.warn("Error Crítico: Usuario '{}' NO encontrado en SimpUserRegistry o sin sesiones activas. No se puede enviar mensaje.", recipientUsername);
                    if (user != null) {
                        log.warn("Usuario '{}' encontrado pero user.hasSessions() es false.", recipientUsername);
                    }
                }

            } else {
                // Reemplazamos System.err por log.warn
                log.warn("No se pudo encontrar username para ID: {} o falta la clave cifrada.", recipientId);
            }
        }
    }
}