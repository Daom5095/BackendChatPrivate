package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
// import lombok.RequiredArgsConstructor; // ¡Eliminar esta!
import lombok.extern.slf4j.Slf4j; // Importar
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy; // ¡Importante!
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
// @RequiredArgsConstructor // ¡ELIMINADO! No funciona con @Lazy en el constructor.
@Slf4j // Añade el objeto 'log' automáticamente
public class MessageService {

    // Campos finales para asegurar la inyección
    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserRepository userRepository;
    private final SimpUserRegistry simpUserRegistry; // Mi registro de usuarios de WebSocket


    // Debo usar el constructor manual para poder aplicar @Lazy
    // a SimpUserRegistry y evitar la dependencia circular.
    @Autowired
    public MessageService(MessageRepository messageRepository,
                          MessageKeyRepository messageKeyRepository,
                          SimpMessagingTemplate simpMessagingTemplate,
                          UserRepository userRepository,
                          @Lazy SimpUserRegistry simpUserRegistry) { // ¡@Lazy es la clave!
        this.messageRepository = messageRepository;
        this.messageKeyRepository = messageKeyRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userRepository = userRepository;
        this.simpUserRegistry = simpUserRegistry;
    }


    /**
     * Este es el método central para enviar y guardar un mensaje.
     * Es transaccional, si algo falla, se revierte todo.
     */
    @Transactional
    public void sendAndStoreMessage(Long senderId, Long conversationId, String ciphertext, Map<String, String> encryptedKeys) {

        // 1. Guardar el mensaje principal (el ciphertext)
        Conversation conv = new Conversation();
        conv.setId(conversationId); // Solo necesito el ID para la relación
        Message message = new Message();
        message.setConversation(conv);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);
        message = messageRepository.save(message); // Guardo y obtengo el ID del mensaje

        // 2. Obtener IDs de los destinatarios (las claves String se convierten a Long)
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            log.error("Error: encryptedKeys map está vacío o nulo para convId: {}", conversationId);
            // Esto detendrá la ejecución y le dirá al cliente que algo salió mal
            throw new IllegalArgumentException("El mapa de claves cifradas no puede estar vacío.");
        }

        List<Long> recipientIds = encryptedKeys.keySet().stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 3. Obtener los usernames (necesarios para el STOMP destination)
        // Busco todos los usuarios de una vez para ser eficiente
        Map<Long, String> userIdToUsernameMap = userRepository.findAllById(recipientIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // 4. Iterar, guardar la MessageKey (clave cifrada) y ENVIAR por WebSocket
        for (Long recipientId : recipientIds) {
            String encryptedKeyForRecipient = encryptedKeys.get(recipientId.toString());
            String recipientUsername = userIdToUsernameMap.get(recipientId);

            if (recipientUsername != null && encryptedKeyForRecipient != null) {

                // Guardar la MessageKey específica para este destinatario
                MessageKey mk = new MessageKey();
                mk.setMessage(message); // Relaciono con el mensaje que guardé
                mk.setRecipientId(recipientId);
                mk.setEncryptedKey(encryptedKeyForRecipient);
                messageKeyRepository.save(mk);

                // Crear el payload para STOMP
                StompMessagePayload payload = new StompMessagePayload();
                payload.setConversationId(conversationId);
                payload.setCiphertext(ciphertext);
                payload.setSenderId(senderId);
                // Solo envío la clave que le pertenece a este destinatario
                payload.setEncryptedKeys(Map.of(recipientId.toString(), encryptedKeyForRecipient));

                // VERIFICACIÓN CON SimpUserRegistry
                // Aquí compruebo si el usuario está REALMENTE conectado al WebSocket
                SimpUser user = simpUserRegistry.getUser(recipientUsername);

                if (user != null && user.hasSessions()) {
                    log.info("Usuario '{}' encontrado en SimpUserRegistry con {} sesión(es). Intentando enviar...", recipientUsername, user.getSessions().size());

                    // ¡Enviando el mensaje!
                    // El destino es /user/{username}/queue/messages
                    simpMessagingTemplate.convertAndSendToUser(recipientUsername, "/queue/messages", payload);

                    log.info("Mensaje reenviado a usuario: {} (ID: {})", recipientUsername, recipientId);
                } else {
                    log.warn("Usuario '{}' NO encontrado en SimpUserRegistry o sin sesiones activas. El mensaje se guardó pero no se pudo entregar en tiempo real.", recipientUsername);
                    if (user != null) {
                        log.warn("Usuario '{}' encontrado pero user.hasSessions() es false.", recipientUsername);
                    }
                    // NOTA: El mensaje SÍ se guardó. El usuario lo recibirá
                    // cuando pida el historial de mensajes (getMessageHistory).
                    // Esto maneja el caso de "entrega offline".
                }

            } else {
                log.warn("No se pudo encontrar username para ID: {} o falta la clave cifrada.", recipientId);
            }
        }
    }
}