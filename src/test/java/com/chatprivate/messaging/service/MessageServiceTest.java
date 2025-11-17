package com.chatprivate.messaging.service;

import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.ConversationParticipant;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import com.chatprivate.messaging.repository.ConversationParticipantRepository;
import com.chatprivate.security.PermissionService;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para MessageService.
 *
 * COBERTURA:
 * - Validación de permisos al enviar mensajes
 * - Guardado de mensajes y claves cifradas
 * - Validación de mapa de claves
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageServiceTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageKeyRepository messageKeyRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;

    @Autowired
    private com.chatprivate.messaging.repository.ConversationRepository conversationRepository;

    private User sender;
    private User recipient;
    private User outsider;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        // Crear usuarios
        sender = userRepository.save(User.builder()
                .username("sender")
                .email("sender@test.com")
                .password("pass")
                .build());

        recipient = userRepository.save(User.builder()
                .username("recipient")
                .email("recipient@test.com")
                .password("pass")
                .build());

        outsider = userRepository.save(User.builder()
                .username("outsider")
                .email("outsider@test.com")
                .password("pass")
                .build());

        // ¡CORRECCIÓN! Crear y GUARDAR la conversación PRIMERO
        conversation = new Conversation();
        conversation.setType("direct");
        conversation = conversationRepository.save(conversation); // ← ESTO FALTABA

        // Ahora sí, añadir participantes (ya existe en BD)
        ConversationParticipant senderP = new ConversationParticipant();
        senderP.setConversation(conversation);
        senderP.setUserId(sender.getId());
        senderP.setRole("owner");
        participantRepository.save(senderP);

        ConversationParticipant recipientP = new ConversationParticipant();
        recipientP.setConversation(conversation);
        recipientP.setUserId(recipient.getId());
        recipientP.setRole("member");
        participantRepository.save(recipientP);
    }

    @Test
    void sendAndStoreMessage_ShouldSaveMessage_WhenSenderIsParticipant() {
        // ARRANGE
        String ciphertext = "mensaje cifrado de prueba";
        Map<String, String> encryptedKeys = Map.of(
                recipient.getId().toString(), "clave_para_recipient"
        );

        // ACT
        messageService.sendAndStoreMessage(
                sender.getId(),
                conversation.getId(),
                ciphertext,
                encryptedKeys
        );

        // ASSERT
        // Verificar que el mensaje se guardó
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertEquals(1, messages.size());
        assertEquals(ciphertext, messages.get(0).getCiphertext());
        assertEquals(sender.getId(), messages.get(0).getSenderId());

        // Verificar que la clave se guardó
        List<MessageKey> keys = messageKeyRepository.findByMessageId(messages.get(0).getId());
        assertEquals(1, keys.size());
        assertEquals(recipient.getId(), keys.get(0).getRecipientId());
        assertEquals("clave_para_recipient", keys.get(0).getEncryptedKey());
    }

    @Test
    void sendAndStoreMessage_ShouldThrowAccessDenied_WhenSenderIsNotParticipant() {
        // ARRANGE
        String ciphertext = "mensaje malicioso";
        Map<String, String> encryptedKeys = Map.of(
                recipient.getId().toString(), "clave"
        );

        // ACT & ASSERT
        // El outsider NO es participante, debe fallar
        assertThrows(AccessDeniedException.class, () -> {
            messageService.sendAndStoreMessage(
                    outsider.getId(),
                    conversation.getId(),
                    ciphertext,
                    encryptedKeys
            );
        });

        // Verificar que NO se guardó ningún mensaje
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertEquals(0, messages.size());
    }

    @Test
    void sendAndStoreMessage_ShouldThrowException_WhenEncryptedKeysMapIsEmpty() {
        // ARRANGE
        String ciphertext = "mensaje sin claves";
        Map<String, String> emptyKeys = Map.of(); // Vacío

        // ACT & ASSERT
        assertThrows(IllegalArgumentException.class, () -> {
            messageService.sendAndStoreMessage(
                    sender.getId(),
                    conversation.getId(),
                    ciphertext,
                    emptyKeys
            );
        });
    }

    @Test
    void sendAndStoreMessage_ShouldIgnoreKey_WhenRecipientIsNotParticipant() {
        // ARRANGE
        String ciphertext = "mensaje con clave para outsider";
        Map<String, String> encryptedKeys = Map.of(
                recipient.getId().toString(), "clave_valida",
                outsider.getId().toString(), "clave_invalida" // NO es participante
        );

        // ACT
        messageService.sendAndStoreMessage(
                sender.getId(),
                conversation.getId(),
                ciphertext,
                encryptedKeys
        );

        // ASSERT
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertEquals(1, messages.size());

        // Solo debe haber guardado 1 clave (la del recipient válido)
        List<MessageKey> keys = messageKeyRepository.findByMessageId(messages.get(0).getId());
        assertEquals(1, keys.size());
        assertEquals(recipient.getId(), keys.get(0).getRecipientId());
    }
}