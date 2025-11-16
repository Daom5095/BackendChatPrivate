package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.AddParticipantRequest;
import com.chatprivate.messaging.dto.CreateConversationRequest;
import com.chatprivate.messaging.dto.ConversationResponse;
import com.chatprivate.messaging.dto.MessageHistoryDto;
import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.ConversationParticipant;
import com.chatprivate.messaging.model.Message;
import com.chatprivate.messaging.model.MessageKey;
import com.chatprivate.messaging.repository.ConversationParticipantRepository;
import com.chatprivate.messaging.repository.ConversationRepository;
import com.chatprivate.messaging.repository.MessageKeyRepository;
import com.chatprivate.messaging.repository.MessageRepository;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;


import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// ... (El resto de tu clase va aquí)

// ¡Importante! Usamos @SpringBootTest para un test de integración
@SpringBootTest
@ActiveProfiles("test") // Usa la BD H2 en memoria
@Transactional // Limpia la BD después de cada test
class ConversationServiceTest {

    // Inyectamos los servicios y repositorios REALES
    @Autowired
    private ConversationService conversationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository participantRepository;


    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageKeyRepository messageKeyRepository;

    private User userC;
    private Conversation conv1; // Chat entre A y B
    private Conversation conv2; // Chat entre A y C

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        // ARRANGE (Preparar)
        // Creamos usuarios
        userA = userRepository.save(User.builder()
                .username("userA")
                .email("a@test.com")
                .password("pass")
                .build());

        userB = userRepository.save(User.builder()
                .username("userB")
                .email("b@test.com")
                .password("pass")
                .build());

        userC = userRepository.save(User.builder()
                .username("userC")
                .email("c@test.com")
                .password("pass")
                .build());

        // --- Creamos un escenario de chat ---

        // 1. Chat entre A y B
        conv1 = createTestConversation("direct", userA, userB);
        // Añadimos un mensaje de B para A
        addTestMessage(conv1, userB, "Hola A, este es el chat 1", Map.of(
                userA.getId(), "keyForA_1",
                userB.getId(), "keyForB_1"
        ));

        // 2. Chat entre A y C
        conv2 = createTestConversation("direct", userA, userC);
        // Añadimos dos mensajes a este chat
        addTestMessage(conv2, userA, "Hola C, este es el primer mensaje", Map.of(
                userA.getId(), "keyForA_2",
                userC.getId(), "keyForC_1"
        ));

        // Este será el "último mensaje"
        addTestMessage(conv2, userC, "Hola A, este es el segundo mensaje", Map.of(
                userA.getId(), "keyForA_3",
                userC.getId(), "keyForC_2"
        ));
    }

    @Test
    void createConversation_ShouldCreateChat_AndAddParticipants() {
        // ARRANGE
        CreateConversationRequest req = new CreateConversationRequest();
        req.setType("direct");
        req.setParticipantIds(List.of(userB.getId())); // Añadir a userB

        // ACT (Ejecutar)
        // El creador es userA
        ConversationResponse response = conversationService.createConversation(req, userA.getId());

        // ASSERT (Verificar)
        assertNotNull(response);
        assertNotNull(response.getId());

        // 1. Verificamos que los participantes están en el DTO de respuesta
        assertEquals(2, response.getParticipants().size());
        assertTrue(response.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userA.getId()) && p.getRole().equals("owner")));
        assertTrue(response.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(userB.getId()) && p.getRole().equals("member")));

        // 2. Verificamos DIRECTAMENTE en la BD (la prueba de oro)
        List<ConversationParticipant> participantsInDb = participantRepository.findByConversation_Id(response.getId());

        assertEquals(2, participantsInDb.size());
        assertTrue(participantsInDb.stream()
                .anyMatch(p -> p.getUserId().equals(userA.getId()) && "owner".equals(p.getRole())));
        assertTrue(participantsInDb.stream()
                .anyMatch(p -> p.getUserId().equals(userB.getId()) && "member".equals(p.getRole())));
    }


    /**
     * Helper para crear una conversación y sus participantes en la BD
     */
    private Conversation createTestConversation(String type, User owner, User... members) {
        Conversation conv = new Conversation();
        conv.setType(type);
        conv = conversationRepository.save(conv); // Guardamos para obtener ID

        // Añadimos al dueño
        ConversationParticipant ownerP = new ConversationParticipant();
        ownerP.setConversation(conv);
        ownerP.setUserId(owner.getId());
        ownerP.setRole("owner");
        participantRepository.save(ownerP);

        // Añadimos a los otros miembros
        for (User member : members) {
            ConversationParticipant memberP = new ConversationParticipant();
            memberP.setConversation(conv);
            memberP.setUserId(member.getId());
            memberP.setRole("member");
            participantRepository.save(memberP);
        }
        return conv;
    }



    // --- NUEVO TEST PARA getUserConversations ---
    @Test
    void getUserConversations_ShouldReturnAllUserChats_WithLastMessage() {
        // ACT
        // Pedimos las conversaciones de userA
        List<ConversationResponse> userAConversations = conversationService.getUserConversations(userA.getId());

        // ASSERT
        assertNotNull(userAConversations);
        assertEquals(2, userAConversations.size()); // userA debe tener 2 chats

        // Buscamos el chat con userC (conv2)
        ConversationResponse chatWithC = userAConversations.stream()
                .filter(c -> c.getParticipants().stream().anyMatch(p -> p.getUserId().equals(userC.getId())))
                .findFirst()
                .orElse(null);

        assertNotNull(chatWithC);

        // Verificamos que el "último mensaje" sea el correcto
        assertNotNull(chatWithC.getLastMessage());
        assertEquals("Hola A, este es el segundo mensaje", chatWithC.getLastMessage().getText());

        // Verificamos que la clave cifrada sea la correcta para userA
        assertEquals("keyForA_3", chatWithC.getLastMessage().getEncryptedKey());
    }
    // --- NUEVO TEST PARA addParticipant (Caso Exitoso) ---
    @Test
    void addParticipant_ShouldWork_WhenRequesterIsOwner() {
        // ARRANGE
        // userA es el "owner" del chat conv1 (con userB)
        AddParticipantRequest req = new AddParticipantRequest();
        req.setUserId(userC.getId()); // Queremos añadir a userC

        // ACT
        // userA (owner) añade a userC al chat conv1
        conversationService.addParticipant(conv1.getId(), userA.getId(), req);

        // ASSERT
        // Verificamos directamente en la BD
        List<ConversationParticipant> participantsInDb = participantRepository.findByConversation_Id(conv1.getId());
        assertEquals(3, participantsInDb.size()); // Ahora debe haber 3 (A, B y C)
        assertTrue(participantsInDb.stream()
                .anyMatch(p -> p.getUserId().equals(userC.getId())));
    }

    // --- NUEVO TEST PARA addParticipant (Fallo de Seguridad) ---
    @Test
    void addParticipant_ShouldThrowAccessDenied_WhenRequesterIsMember() {
        // ARRANGE
        // userB es solo un "member" del chat conv1
        AddParticipantRequest req = new AddParticipantRequest();
        req.setUserId(userC.getId()); // Queremos añadir a userC

        // ACT & ASSERT
        // Verificamos que lanza la excepción de seguridad
        assertThrows(AccessDeniedException.class, () -> {
            // userB (member) intenta añadir a userC al chat conv1
            conversationService.addParticipant(conv1.getId(), userB.getId(), req);
        }, "Solo el dueño de la conversación puede realizar esta acción");
    }

    // --- NUEVO TEST PARA removeParticipant (Fallo de Seguridad) ---
    @Test
    void removeParticipant_ShouldThrowAccessDenied_WhenMemberTriesToRemoveAnotherMember() {
        // ARRANGE
        // En el chat conv1, userA (owner) y userB (member)
        // userB intentará eliminar a userA

        // ACT & ASSERT
        assertThrows(AccessDeniedException.class, () -> {
            // userB (member) intenta eliminar a userA (owner)
            conversationService.removeParticipant(conv1.getId(), userB.getId(), userA.getId());
        }, "No tienes permiso para eliminar a este participante");
    }

    // --- NUEVO TEST PARA removeParticipant (Caso Exitoso: Salir del chat) ---
    @Test
    void removeParticipant_ShouldWork_WhenMemberRemovesThemselves() {
        // ARRANGE
        // En el chat conv1, userA (owner) y userB (member)

        // ACT
        // userB (member) se elimina a sí mismo (abandona el chat)
        conversationService.removeParticipant(conv1.getId(), userB.getId(), userB.getId());

        // ASSERT
        List<ConversationParticipant> participantsInDb = participantRepository.findByConversation_Id(conv1.getId());
        assertEquals(1, participantsInDb.size()); // Solo debe quedar userA
        assertFalse(participantsInDb.stream()
                .anyMatch(p -> p.getUserId().equals(userB.getId())));
    }

    // --- NUEVO TEST PARA getMessageHistoryPaged ---
    @Test
    void getMessageHistoryPaged_ShouldReturnCorrectPage_WithKeys() {
        // ACT
        // Pedimos la página 0 (la más reciente), de tamaño 1, para el chat 2
        // El servicio ordena por fecha DESC, así que la página 0 es el último mensaje
        Page<MessageHistoryDto> page0 = conversationService.getMessageHistoryPaged(
                conv2.getId(), // ID del chat con C
                userA.getId(), // Como userA
                0,             // Página 0
                1              // Tamaño 1
        );

        // ASSERT
        assertNotNull(page0);
        assertEquals(2, page0.getTotalElements()); // 2 mensajes en total en este chat
        assertEquals(1, page0.getContent().size()); // 1 mensaje en esta página
        assertEquals("Hola A, este es el segundo mensaje", page0.getContent().get(0).getCiphertext());
        assertEquals("keyForA_3", page0.getContent().get(0).getEncryptedKey()); // Clave de userA

        // ACT (Pedimos la segunda página)
        Page<MessageHistoryDto> page1 = conversationService.getMessageHistoryPaged(
                conv2.getId(),
                userA.getId(),
                1,             // Página 1
                1              // Tamaño 1
        );

        // ASSERT
        assertEquals(1, page1.getContent().size()); // 1 mensaje en esta página
        assertEquals("Hola C, este es el primer mensaje", page1.getContent().get(0).getCiphertext());
        assertEquals("keyForA_2", page1.getContent().get(0).getEncryptedKey()); // Clave de userA
    }

    @Test
    void getMessageHistory_ShouldReturnAllMessages_WithKeys() {
        // ARRANGE
        // El setUp() ya creó conv2 (chat A-C) con 2 mensajes

        // ACT
        // Pedimos el historial COMPLETO de conv2 como userA
        List<MessageHistoryDto> history = conversationService.getMessageHistory(
                conv2.getId(),
                userA.getId()
        );

        // ASSERT
        assertNotNull(history);
        assertEquals(2, history.size()); // Debe devolver los 2 mensajes

        // Verifica que los mensajes estén y tengan las claves correctas para userA
        assertTrue(history.stream()
                .anyMatch(m -> m.getCiphertext().equals("Hola C, este es el primer mensaje")
                        && m.getEncryptedKey().equals("keyForA_2")));

        assertTrue(history.stream()
                .anyMatch(m -> m.getCiphertext().equals("Hola A, este es el segundo mensaje")
                        && m.getEncryptedKey().equals("keyForA_3")));
    }


    /**
     * Helper para añadir un mensaje y sus claves cifradas a la BD
     */
    private Message addTestMessage(Conversation conv, User sender, String text, Map<Long, String> keys) {
        Message msg = new Message();
        msg.setConversation(conv);
        msg.setSenderId(sender.getId());
        msg.setCiphertext(text);
        msg = messageRepository.save(msg); // Guardamos para obtener ID

        // Guardamos la clave cifrada para cada destinatario
        for (Map.Entry<Long, String> keyEntry : keys.entrySet()) {
            MessageKey mk = new MessageKey();
            mk.setMessage(msg);
            mk.setRecipientId(keyEntry.getKey());
            mk.setEncryptedKey(keyEntry.getValue());
            messageKeyRepository.save(mk);
        }
        return msg;
    }
}