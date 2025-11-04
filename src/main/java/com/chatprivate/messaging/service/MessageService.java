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

/**
 * Servicio central para el envío y almacenamiento de mensajes.
 * Este servicio es llamado por el StompChatController cuando
 * un mensaje llega por WebSocket.
 */
@Service
// No uso @RequiredArgsConstructor porque necesito un constructor manual
// para aplicar @Lazy y romper una dependencia circular.
@Slf4j // Añade el objeto 'log' automáticamente
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;
    private final SimpMessagingTemplate simpMessagingTemplate; // Para enviar mensajes por WebSocket
    private final UserRepository userRepository;
    private final SimpUserRegistry simpUserRegistry; // Mi registro de usuarios de WebSocket


    /**
     * Constructor manual.
     * Utilizo @Lazy en SimpUserRegistry.
     * Razón: SimpUserRegistry puede depender de beans que dependen de
     * MessageService, creando un "ciclo de dependencia" que impide
     * a Spring arrancar. @Lazy le dice a Spring que no inyecte
     * SimpUserRegistry hasta que se use por primera vez, rompiendo el ciclo.
     */
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
     * Es transaccional: si algo falla (ej. guardar una MessageKey),
     * se revierte *todo* (incluyendo el Message principal).
     *
     * @param senderId      ID del usuario que envía.
     * @param conversationId ID de la conversación.
     * @param ciphertext    El contenido del mensaje, cifrado con AES.
     * @param encryptedKeys Un mapa de { "recipientId" -> "clave AES cifrada con RSA de ese recipient" }
     */
    @Transactional
    public void sendAndStoreMessage(Long senderId, Long conversationId, String ciphertext, Map<String, String> encryptedKeys) {

        // 1. Guardar el mensaje principal (el ciphertext)
        Conversation conv = new Conversation();
        conv.setId(conversationId); // Solo necesito el ID para la relación JPA
        Message message = new Message();
        message.setConversation(conv);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);
        message = messageRepository.save(message); // Guardo y obtengo el ID del mensaje
        log.debug("Mensaje {} guardado en conv {}", message.getId(), conversationId);

        // 2. Validar y obtener IDs de los destinatarios
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            log.error("Error: encryptedKeys map está vacío o nulo para convId: {}", conversationId);
            // Lanzo una excepción para que se revierta la transacción (rollback)
            throw new IllegalArgumentException("El mapa de claves cifradas no puede estar vacío.");
        }

        // Las claves del mapa son String, las convierto a Long
        List<Long> recipientIds = encryptedKeys.keySet().stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 3. Obtener los usernames (necesarios para el STOMP destination)
        // Busco todos los usuarios de una vez para ser eficiente (evito N+1 queries)
        Map<Long, String> userIdToUsernameMap = userRepository.findAllById(recipientIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // 4. Iterar, guardar la MessageKey (clave cifrada) y ENVIAR por WebSocket
        for (Long recipientId : recipientIds) {
            String encryptedKeyForRecipient = encryptedKeys.get(recipientId.toString());
            String recipientUsername = userIdToUsernameMap.get(recipientId);

            if (recipientUsername != null && encryptedKeyForRecipient != null) {

                // 4a. Guardar la MessageKey específica para este destinatario
                MessageKey mk = new MessageKey();
                mk.setMessage(message); // Relaciono con el mensaje que guardé en paso 1
                mk.setRecipientId(recipientId);
                mk.setEncryptedKey(encryptedKeyForRecipient);
                messageKeyRepository.save(mk);
                log.debug("MessageKey guardada para msg {} y recipient {}", message.getId(), recipientId);

                // 4b. Crear el payload para STOMP
                StompMessagePayload payload = new StompMessagePayload();
                payload.setConversationId(conversationId);
                payload.setCiphertext(ciphertext);
                payload.setSenderId(senderId);
                // Solo envío la clave que le pertenece a ESTE destinatario
                payload.setEncryptedKeys(Map.of(recipientId.toString(), encryptedKeyForRecipient));

                // 4c. VERIFICACIÓN CON SimpUserRegistry (Entrega en tiempo real)
                // Aquí compruebo si el usuario está REALMENTE conectado al WebSocket
                SimpUser user = simpUserRegistry.getUser(recipientUsername);

                if (user != null && user.hasSessions()) {
                    // ¡El usuario está online!
                    log.info("Usuario '{}' encontrado en SimpUserRegistry con {} sesión(es). Intentando enviar...", recipientUsername, user.getSessions().size());

                    // ¡Enviando el mensaje!
                    // El destino es /user/{username}/queue/messages
                    // Spring lo traducirá a la cola específica de la sesión del usuario.
                    simpMessagingTemplate.convertAndSendToUser(recipientUsername, "/queue/messages", payload);

                    log.info("Mensaje reenviado a usuario: {} (ID: {})", recipientUsername, recipientId);
                } else {
                    // 4d. Manejo de entrega "offline"
                    log.warn("Usuario '{}' NO encontrado en SimpUserRegistry o sin sesiones activas. El mensaje se guardó pero no se pudo entregar en tiempo real.", recipientUsername);
                    // NOTA: No hago nada más. El mensaje SÍ se guardó en la BD (pasos 1 y 4a).
                    // El usuario recibirá este mensaje la próxima vez que pida
                    // el historial de mensajes (getMessageHistory).
                }

            } else {
                log.warn("No se pudo encontrar username para ID: {} o falta la clave cifrada. Saltando...", recipientId);
            }
        }
    }
}