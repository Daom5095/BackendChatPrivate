package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.messaging.model.*;
import com.chatprivate.messaging.repository.*;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import lombok.RequiredArgsConstructor; // Asegúrate de importar
import lombok.extern.slf4j.Slf4j; // Importar para logging
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Function; // Para el Map de usuarios

/**
 * Servicio para la lógica de negocio de Conversaciones.
 * Maneja la creación de chats, obtención de historial,
 * gestión de participantes, etc.
 */
@Service
@RequiredArgsConstructor // Lombok se encarga del constructor
@Slf4j // Para logging
public class ConversationService {

    // Repositorios
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;


    /**
     * Obtiene el historial completo de mensajes para una conversación,
     * pero solo incluye las claves de cifrado (encryptedKey) que
     * pertenecen al usuario que realiza la petición.
     *
     * @param conversationId ID del chat.
     * @param userId         ID del usuario que pide el historial.
     * @return Lista de DTOs con el historial.
     */
    @Transactional(readOnly = true)
    public List<MessageHistoryDto> getMessageHistory(Long conversationId, Long userId) {
        log.info("Cargando historial para conversación {} para usuario {}", conversationId, userId);

        // 1. Obtener todos los mensajes del chat, ordenados por fecha
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Obtener los IDs de todos esos mensajes
        List<Long> messageIds = messages.stream().map(Message::getId).collect(Collectors.toList());

        // 3. ¡Clave! Buscar en MessageKey SÓLO las claves que pertenecen
        // a esos mensajes Y a ESE usuario.
        List<MessageKey> userKeys = messageKeyRepository.findByMessage_IdInAndRecipientId(messageIds, userId);

        // 4. Convierto las claves en un Mapa (MessageID -> EncryptedKey) para búsqueda rápida
        Map<Long, String> keyMap = userKeys.stream()
                .collect(Collectors.toMap(mk -> mk.getMessage().getId(), MessageKey::getEncryptedKey));

        // 5. Construyo la respuesta
        return messages.stream()
                .map(msg -> {
                    String encryptedKey = keyMap.get(msg.getId());
                    if (encryptedKey == null) {
                        // Esto podría pasar si el usuario fue añadido al chat *después*
                        // de que se envió este mensaje. No tiene clave para él.
                        log.warn("Usuario {} no tiene clave para el mensaje {}", userId, msg.getId());
                        return null; // Lo filtramos
                    }
                    return new MessageHistoryDto(
                            msg.getId(),
                            msg.getSenderId(),
                            msg.getCiphertext(),
                            encryptedKey, // La clave específica para este usuario
                            msg.getCreatedAt()
                    );
                })
                .filter(dto -> dto != null) // Quito los mensajes para los que no tenía clave
                .collect(Collectors.toList());
    }


    /**
     * Obtiene la lista de todas las conversaciones en las que participa un usuario.
     * Incluye los participantes y el *último mensaje* de cada chat.
     *
     * @param userId ID del usuario.
     * @return Lista de DTOs de conversaciones.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(Long userId) {
        // 1. Busco todas las conversaciones en las que el usuario es participante
        List<Conversation> conversations = conversationParticipantRepository.findConversationsByUserId(userId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> conversationIds = conversations.stream().map(Conversation::getId).collect(Collectors.toList());

        // 2. Busco *todos* los participantes de *todas* esas conversaciones
        List<ConversationParticipant> allParticipants = conversationParticipantRepository.findByConversation_IdIn(conversationIds);
        if (allParticipants.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Busco los datos (User) de todos los participantes de una vez
        List<Long> allUserIds = allParticipants.stream()
                .map(ConversationParticipant::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 4. Agrupo los participantes (ya como DTOs) por ID de conversación
        Map<Long, List<ParticipantDto>> participantsByConvId = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getConversation().getId(),
                        Collectors.mapping(p -> toParticipantDto(p, userMap), Collectors.toList())
                ));

        // 5. Mapeo las conversaciones a ConversationResponse
        return conversations.stream()
                .map(conv -> {
                    List<ParticipantDto> participants = participantsByConvId.getOrDefault(conv.getId(), Collections.emptyList());

                    // --- 6. LÓGICA CLAVE: Obtener el último mensaje y su clave ---
                    LastMessageDto lastMessageDto = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                            .map(msg -> {
                                // 6a. Tengo el último mensaje (msg), ahora busco su clave
                                //     específica para el usuario actual (userId)
                                String encryptedKey = messageKeyRepository.findByMessage_IdAndRecipientId(msg.getId(), userId)
                                        .map(MessageKey::getEncryptedKey)
                                        .orElse(null); // Si no hay clave, será null

                                if (encryptedKey == null) {
                                    log.warn("No se encontró MessageKey para el último mensaje (msgId: {}) y el usuario (userId: {}) en la conv {}", msg.getId(), userId, conv.getId());
                                }

                                // 6b. Devuelvo el DTO del último mensaje
                                return new LastMessageDto(msg.getCiphertext(), msg.getCreatedAt(), encryptedKey);
                            })
                            .orElse(null); // Si no hay mensajes, 'lastMessage' será null

                    // 7. Construyo la respuesta final para esta conversación
                    return toResponse(conv, participants, lastMessageDto);
                })
                .collect(Collectors.toList());
    }

    /**
     * Crea una nueva conversación.
     *
     * @param req       DTO con tipo, título e IDs de participantes.
     * @param creatorId ID del usuario que crea el chat (se convierte en "owner").
     * @return El DTO de la conversación recién creada.
     */
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest req, Long creatorId) {
        Conversation conv = new Conversation();
        conv.setType(req.getType() == null ? "direct" : req.getType());

        // Lógica para no poner título en chats directos (1 a 1)
        if ("direct".equalsIgnoreCase(conv.getType()) && (req.getTitle() == null || req.getTitle().isEmpty())) {
            if (req.getParticipantIds() != null && req.getParticipantIds().size() == 1) {
                conv.setTitle(null); // El frontend se encargará de poner el nombre del otro usuario
            }
        } else {
            conv.setTitle(req.getTitle());
        }

        final Conversation savedConv = conversationRepository.save(conv);

        // Añado al creador como "owner"
        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(savedConv);
        owner.setUserId(creatorId);
        owner.setRole("owner");
        conversationParticipantRepository.save(owner);

        // Añado al resto de participantes (si los hay)
        if (req.getParticipantIds() != null && !req.getParticipantIds().isEmpty()) {
            List<Long> participantIdsToAdd = req.getParticipantIds().stream()
                    .filter(id -> id != null && !id.equals(creatorId)) // Me filtro a mi mismo
                    .distinct()
                    .collect(Collectors.toList());

            // Verifico que los usuarios existan
            List<Long> existingUserIds = userRepository.findAllById(participantIdsToAdd)
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());

            List<ConversationParticipant> newParticipants = existingUserIds.stream().map(userId -> {
                ConversationParticipant p = new ConversationParticipant();
                p.setConversation(savedConv);
                p.setUserId(userId);
                p.setRole("member"); // Por defecto son "member"
                return p;
            }).collect(Collectors.toList());

            if (!newParticipants.isEmpty()) {
                conversationParticipantRepository.saveAll(newParticipants);
            }
        }

        // Devuelvo la conversación recién creada
        return getConversationResponseById(savedConv.getId());
    }

    // --- Métodos de gestión de participantes ---

    /**
     * Añade un nuevo participante a un chat.
     * Solo el "owner" puede hacer esto.
     */
    @Transactional
    public void addParticipant(Long conversationId, Long requesterId, AddParticipantRequest req) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));

        // Verificación de permisos
        if (!isOwner(conversationId, requesterId)) {
            throw new AccessDeniedException("Solo el owner puede añadir participantes");
        }
        Long userId = req.getUserId();
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Usuario a añadir no existe");
        }
        // Evitar duplicados
        if (conversationParticipantRepository.existsByConversation_IdAndUserId(conversationId, userId)) {
            log.warn("Usuario {} ya está en la conversación {}", userId, conversationId);
            return;
        }
        ConversationParticipant p = new ConversationParticipant();
        p.setConversation(conv);
        p.setUserId(userId);
        p.setRole(req.getRole() == null || req.getRole().isEmpty() ? "member" : req.getRole());
        conversationParticipantRepository.save(p);
    }

    /**
     * Elimina a un participante de un chat.
     * Reglas:
     * 1. El "owner" puede eliminar a cualquiera (incluido a sí mismo).
     * 2. Un "member" solo puede eliminarse a sí mismo (abandonar el chat).
     */
    @Transactional
    public void removeParticipant(Long conversationId, Long requesterId, Long userIdToRemove) {
        boolean requesterIsOwner = isOwner(conversationId, requesterId);

        // Verificación de permisos
        if (!requesterIsOwner && !requesterId.equals(userIdToRemove)) {
            // No soy owner Y estoy intentando eliminar a alguien que no soy yo
            throw new AccessDeniedException("No autorizado para eliminar a este participante");
        }

        ConversationParticipant participant = conversationParticipantRepository
                .findByConversation_IdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new IllegalArgumentException("El participante no se encuentra en esta conversación"));

        // (Faltaría lógica para reasignar "owner" si el owner se va,
        // pero por ahora solo lo elimino)
        conversationParticipantRepository.delete(participant);
    }

    /**
     * Obtiene la lista de participantes (con sus usernames) de un chat.
     */
    @Transactional(readOnly = true)
    public List<ParticipantDto> getParticipants(Long conversationId) {
        List<ConversationParticipant> participants = conversationParticipantRepository.findByConversation_Id(conversationId);
        if (participants.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = participants.stream().map(ConversationParticipant::getUserId).collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return participants.stream()
                .map(p -> toParticipantDto(p, userMap))
                .collect(Collectors.toList());
    }


    // --- Métodos Helpers Privados ---

    /** Helper para saber si un usuario es "owner" de un chat */
    private boolean isOwner(Long conversationId, Long userId) {
        return conversationParticipantRepository.findByConversation_IdAndUserId(conversationId, userId)
                .map(p -> "owner".equalsIgnoreCase(p.getRole()))
                .orElse(false);
    }

    /** Helper para convertir una entidad Conversation a un DTO de respuesta. */
    private ConversationResponse toResponse(Conversation conv, List<ParticipantDto> participants, LastMessageDto lastMessage) {
        ConversationResponse r = new ConversationResponse();
        r.setId(conv.getId());
        r.setType(conv.getType());
        r.setTitle(conv.getTitle());
        r.setCreatedAt(conv.getCreatedAt());
        r.setParticipants(participants);
        r.setLastMessage(lastMessage); // <-- Añado el último mensaje
        return r;
    }

    /** Helper para obtener una ConversationResponse completa por su ID */
    private ConversationResponse getConversationResponseById(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada con ID: " + conversationId));

        List<ParticipantDto> participants = getParticipants(conversationId);

        // Busco el último mensaje.
        LastMessageDto lastMessageDto = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                .map(msg -> {
                    // Nota: Al crear un chat, el último mensaje es null.
                    // Si ya hay mensajes, aquí faltaría la lógica para
                    // buscar la 'encryptedKey' para el usuario actual.
                    // Pero para 'createConversation', 'lastMessageDto' será null.
                    return new LastMessageDto(msg.getCiphertext(), msg.getCreatedAt(), null);
                })
                .orElse(null);

        return toResponse(conv, participants, lastMessageDto);
    }

    /** Helper para convertir un Participante (entidad) a un DTO (con username) */
    private ParticipantDto toParticipantDto(ConversationParticipant p, Map<Long, User> userMap) {
        ParticipantDto dto = new ParticipantDto();
        dto.setUserId(p.getUserId());
        dto.setRole(p.getRole());
        dto.setJoinedAt(p.getJoinedAt());
        User user = userMap.get(p.getUserId());
        if (user != null) {
            dto.setUsername(user.getUsername());
        } else {
            dto.setUsername("Usuario Desconocido"); // Fallback
        }
        return dto;
    }
}