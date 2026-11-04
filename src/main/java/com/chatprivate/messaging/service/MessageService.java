package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import com.chatprivate.security.PermissionService;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/** Aquí valido y guardo los mensajes cifrados que envían los participantes. */
@Service
@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserRepository userRepository;
    private final SimpUserRegistry simpUserRegistry;

    // ¡NUEVO! Mi servicio de validación de permisos
    private final PermissionService permissionService;

    /**
     * Constructor con todas las dependencias.
     * Ya no uso @RequiredArgsConstructor porque tengo muchas dependencias
     * y es más claro hacerlo explícito.
     */
    @Autowired
    public MessageService(MessageRepository messageRepository,
                          MessageKeyRepository messageKeyRepository,
                          SimpMessagingTemplate simpMessagingTemplate,
                          UserRepository userRepository,
                          SimpUserRegistry simpUserRegistry,
                          PermissionService permissionService) { // <-- NUEVO
        this.messageRepository = messageRepository;
        this.messageKeyRepository = messageKeyRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userRepository = userRepository;
        this.simpUserRegistry = simpUserRegistry;
        this.permissionService = permissionService; // <-- NUEVO
    }

    /**
     * Método central para enviar y guardar un mensaje.
     *
     * FLUJO DE SEGURIDAD (NUEVO):
     * 1. ✅ Valida que el sender sea participante de la conversación
     * 2. ✅ Valida que el mapa de claves no esté vacío
     * 3. Guarda el mensaje
     * 4. Guarda las claves cifradas
     * 5. Envía el mensaje por WebSocket a los destinatarios online
     *
     * Es transaccional: si algo falla, se revierte TODO.
     *
     * @param senderId      ID del usuario que envía
     * @param conversationId ID de la conversación
     * @param ciphertext    Contenido del mensaje cifrado con AES
     * @param encryptedKeys Mapa de { "recipientId" -> "clave AES cifrada con RSA" }
     *
     * @throws org.springframework.security.access.AccessDeniedException Si el sender no es participante
     * @throws IllegalArgumentException Si el mapa de claves está vacío o es inválido
     */
    @Transactional
    public void sendAndStoreMessage(Long senderId, Long conversationId,
                                    String ciphertext, Map<String, String> encryptedKeys) {

        log.info("📨 Procesando mensaje de usuario {} para conversación {}", senderId, conversationId);

        // ============================================
        // 🔒 VALIDACIONES DE SEGURIDAD (NUEVAS)
        // ============================================

        // VALIDACIÓN #1: El sender DEBE ser participante de la conversación
        // Si no lo es, lanza AccessDeniedException
        permissionService.validateCanSendMessages(senderId, conversationId);
        log.debug("✅ Validación de permisos exitosa para usuario {}", senderId);

        // VALIDACIÓN #2: El mapa de claves NO puede estar vacío
        // Esto es crucial para E2EE: cada destinatario necesita su clave
        if (encryptedKeys == null || encryptedKeys.isEmpty()) {
            log.error(" Error de validación: Mapa de claves vacío para mensaje en conversación {}", conversationId);
            throw new IllegalArgumentException(
                    "El mapa de claves cifradas no puede estar vacío. " +
                            "Cada destinatario debe tener una clave para descifrar el mensaje."
            );
        }

        // ============================================
        // GUARDADO DEL MENSAJE
        // ============================================

        // 1. Guardo el mensaje principal (el ciphertext)
        Conversation conv = new Conversation();
        conv.setId(conversationId); // Solo necesito el ID para la relación JPA

        Message message = new Message();
        message.setConversation(conv);
        message.setSenderId(senderId);
        message.setCiphertext(ciphertext);

        message = messageRepository.save(message);
        log.debug(" Mensaje {} guardado en BD para conversación {}", message.getId(), conversationId);

        // ============================================
        //  GUARDADO DE CLAVES Y ENVÍO POR WEBSOCKET
        // ============================================

        // 2. Convierto los IDs de String a Long (las claves del mapa vienen como String desde JSON)
        Map<Long, String> recipientKeysMap = encryptedKeys.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Long.parseLong(entry.getKey()),
                        Map.Entry::getValue
                ));

        // 3. Obtengo los usernames de todos los destinatarios de UNA VEZ
        // (evito hacer N queries individuales - optimización de rendimiento)
        Map<Long, String> userIdToUsernameMap = userRepository
                .findAllById(recipientKeysMap.keySet())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // 4. Para cada destinatario: guardo su clave y le envío el mensaje (si está online)
        for (Map.Entry<Long, String> entry : recipientKeysMap.entrySet()) {
            Long recipientId = entry.getKey();
            String encryptedKeyForRecipient = entry.getValue();
            String recipientUsername = userIdToUsernameMap.get(recipientId);

            // Valido que el destinatario exista en mi BD
            if (recipientUsername == null) {
                log.warn(" Destinatario con ID {} no encontrado en la BD. Saltando...", recipientId);
                continue; // Paso al siguiente destinatario
            }

            // VALIDACIÓN ADICIONAL: El destinatario también debe ser participante
            // (esto evita que un atacante agregue claves para usuarios random)
            try {
                permissionService.validateIsParticipant(recipientId, conversationId);
            } catch (Exception e) {
                log.warn(" INTENTO SOSPECHOSO: El mensaje incluye una clave para el usuario {} " +
                                "que NO es participante de la conversación {}. Ignorando.",
                        recipientId, conversationId);
                continue; // No guardo la clave ni envío el mensaje
            }

            // 4a. Guardo la MessageKey específica para este destinatario
            MessageKey mk = new MessageKey();
            mk.setMessage(message);
            mk.setRecipientId(recipientId);
            mk.setEncryptedKey(encryptedKeyForRecipient);
            messageKeyRepository.save(mk);
            log.debug(" Clave guardada para mensaje {} y destinatario {}", message.getId(), recipientId);

            // 4b. Preparo el payload para STOMP
            StompMessagePayload payload = new StompMessagePayload();
            payload.setConversationId(conversationId);
            payload.setCiphertext(ciphertext);
            payload.setSenderId(senderId);
            // Solo envío la clave que le pertenece a ESTE destinatario
            payload.setEncryptedKeys(Map.of(recipientId.toString(), encryptedKeyForRecipient));

            // 4c. Verifico si el destinatario está ONLINE (conectado a WebSocket)
            SimpUser user = simpUserRegistry.getUser(recipientUsername);

            if (user != null && user.hasSessions()) {
                // ¡El destinatario está online! Envío el mensaje en tiempo real
                log.info(" Enviando mensaje a usuario online: {} (ID: {})", recipientUsername, recipientId);

                simpMessagingTemplate.convertAndSendToUser(
                        recipientUsername,
                        "/queue/messages",
                        payload
                );

                log.debug(" Mensaje entregado exitosamente a {}", recipientUsername);
            } else {
                // El destinatario está offline
                // El mensaje YA está guardado en la BD, lo recibirá cuando pida el historial
                log.debug("📭 Usuario {} está offline. Mensaje guardado para entrega posterior.", recipientUsername);
            }
        }

        log.info(" Procesamiento de mensaje completado para conversación {}", conversationId);
    }
}