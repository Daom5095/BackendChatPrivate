package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.messaging.model.*;
import com.chatprivate.messaging.repository.*;
import com.chatprivate.security.PermissionService;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * Servicio para la lógica de negocio de Conversaciones.
 *
 * ACTUALIZADO EN SESIÓN 2:
 * - Integrado PermissionService para todas las operaciones
 * - Validaciones de seguridad antes de cada operación
 * - Mejor logging de eventos de seguridad
 *
 * ACTUALIZADO AHORA:
 * - getUserConversations() ahora ordena los chats por el último mensaje.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    // Repositorios
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;

    // ¡NUEVO! Servicio de permisos
    private final PermissionService permissionService;

    /**
     * Obtiene el historial completo de mensajes para una conversación.
     * (Este método no tiene cambios)
     */
    @Transactional(readOnly = true)
    public List<MessageHistoryDto> getMessageHistory(Long conversationId, Long userId) {
        log.info("📚 Usuario {} solicitando historial de conversación {}", userId, conversationId);

        // 🔒 VALIDACIÓN DE SEGURIDAD
        permissionService.validateCanReadMessages(userId, conversationId);
        log.debug("✅ Usuario {} autorizado para leer conversación {}", userId, conversationId);

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            log.debug("📭 No hay mensajes en conversación {}", conversationId);
            return Collections.emptyList();
        }

        List<Long> messageIds = messages.stream()
                .map(Message::getId)
                .collect(Collectors.toList());

        List<MessageKey> userKeys = messageKeyRepository
                .findByMessage_IdInAndRecipientId(messageIds, userId);

        Map<Long, String> keyMap = userKeys.stream()
                .collect(Collectors.toMap(
                        mk -> mk.getMessage().getId(),
                        MessageKey::getEncryptedKey
                ));

        List<MessageHistoryDto> history = messages.stream()
                .map(msg -> {
                    String encryptedKey = keyMap.get(msg.getId());
                    if (encryptedKey == null) {
                        log.debug("⚠️ Usuario {} no tiene clave para mensaje {}", userId, msg.getId());
                        return null;
                    }
                    return new MessageHistoryDto(
                            msg.getId(),
                            msg.getSenderId(),
                            msg.getCiphertext(),
                            encryptedKey,
                            msg.getCreatedAt()
                    );
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());

        log.info("✅ Devueltos {} mensajes para usuario {} en conversación {}",
                history.size(), userId, conversationId);

        return history;
    }


    /**
     * Obtiene el historial paginado de mensajes para una conversación.
     * (Este método no tiene cambios)
     */
    @Transactional(readOnly = true)
    public Page<MessageHistoryDto> getMessageHistoryPaged(Long conversationId, Long userId, int page, int size) {
        log.info("📚 Usuario {} solicitando historial PAGINADO de conv {} (página: {}, tamaño: {})",
                userId, conversationId, page, size);

        // 1. 🔒 VALIDACIÓN DE SEGURIDAD
        permissionService.validateCanReadMessages(userId, conversationId);
        log.debug("✅ Usuario {} autorizado para leer conversación {}", userId, conversationId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Message> messagePage = messageRepository.findByConversationId(conversationId, pageable);

        if (!messagePage.hasContent()) {
            log.debug("📭 No hay mensajes en esta página para conversación {}", conversationId);
            return Page.empty(pageable);
        }

        List<Long> messageIds = messagePage.getContent().stream()
                .map(Message::getId)
                .collect(Collectors.toList());

        List<MessageKey> userKeys = messageKeyRepository
                .findByMessage_IdInAndRecipientId(messageIds, userId);

        Map<Long, String> keyMap = userKeys.stream()
                .collect(Collectors.toMap(
                        mk -> mk.getMessage().getId(),
                        MessageKey::getEncryptedKey
                ));

        List<MessageHistoryDto> dtos = messagePage.getContent().stream()
                .map(msg -> {
                    String encryptedKey = keyMap.get(msg.getId());
                    if (encryptedKey == null) {
                        log.debug("⚠️ Usuario {} no tiene clave para mensaje {}", userId, msg.getId());
                        return null;
                    }
                    return new MessageHistoryDto(
                            msg.getId(),
                            msg.getSenderId(),
                            msg.getCiphertext(),
                            encryptedKey,
                            msg.getCreatedAt()
                    );
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, messagePage.getTotalElements());
    }

    /**
     * Obtiene la lista de conversaciones de un usuario.
     *
     * --- ¡¡¡MÉTODO MODIFICADO!!! ---
     * Ahora ordena la lista de conversaciones.
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(Long userId) {
        log.info("📂 Usuario {} solicitando lista de conversaciones", userId);

        // 1. Busco todas las conversaciones en las que el usuario participa
        List<Conversation> conversations = conversationParticipantRepository
                .findConversationsByUserId(userId);

        if (conversations.isEmpty()) {
            log.debug("📭 Usuario {} no tiene conversaciones", userId);
            return Collections.emptyList();
        }

        List<Long> conversationIds = conversations.stream()
                .map(Conversation::getId)
                .collect(Collectors.toList());

        // 2. Busco TODOS los participantes de TODAS las conversaciones en UNA query
        List<ConversationParticipant> allParticipants = conversationParticipantRepository
                .findByConversation_IdIn(conversationIds);

        // 3. Busco los datos de User de TODOS los participantes en UNA query
        List<Long> allUserIds = allParticipants.stream()
                .map(ConversationParticipant::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 4. Agrupo los participantes por conversación (en memoria, sin queries)
        Map<Long, List<ParticipantDto>> participantsByConvId = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getConversation().getId(),
                        Collectors.mapping(p -> toParticipantDto(p, userMap), Collectors.toList())
                ));

        // 5. Mapeo las conversaciones a DTOs
        List<ConversationResponse> response = conversations.stream()
                .map(conv -> {
                    List<ParticipantDto> participants = participantsByConvId
                            .getOrDefault(conv.getId(), Collections.emptyList());

                    // 6. Obtengo el último mensaje
                    LastMessageDto lastMessageDto = messageRepository
                            .findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                            .map(msg -> {
                                String encryptedKey = messageKeyRepository
                                        .findByMessage_IdAndRecipientId(msg.getId(), userId)
                                        .map(MessageKey::getEncryptedKey)
                                        .orElse(null);

                                if (encryptedKey == null) {
                                    log.debug("⚠️ No hay clave para el último mensaje de conv {} y usuario {}",
                                            conv.getId(), userId);
                                }

                                return new LastMessageDto(
                                        msg.getCiphertext(),
                                        msg.getCreatedAt(),
                                        encryptedKey
                                );
                            })
                            .orElse(null);

                    return toResponse(conv, participants, lastMessageDto);
                })
                // --- ¡¡¡INICIO DEL CAMBIO!!! ---
                // 7. Ordenar la lista de DTOs
                .sorted((c1, c2) -> {
                    // Chats sin mensajes van al final
                    if (c1.getLastMessage() == null && c2.getLastMessage() == null) {
                        // Si ninguno tiene mensajes, ordenar por fecha de creación del chat
                        return c2.getCreatedAt().compareTo(c1.getCreatedAt());
                    }
                    if (c1.getLastMessage() == null) {
                        return 1; // c1 (sin mensaje) va después que c2 (con mensaje)
                    }
                    if (c2.getLastMessage() == null) {
                        return -1; // c1 (con mensaje) va antes que c2 (sin mensaje)
                    }

                    // Si ambos tienen mensajes, ordenar por fecha del último mensaje (más nuevo primero)
                    return c2.getLastMessage().getCreatedAt().compareTo(c1.getLastMessage().getCreatedAt());
                })
                // --- ¡¡¡FIN DEL CAMBIO!!! ---
                .collect(Collectors.toList());

        log.info("✅ Devueltas {} conversaciones ordenadas para usuario {} (optimizado)", response.size(), userId);
        return response;
    }

    /**
     * Crea una nueva conversación.
     * (Este método no tiene cambios)
     */
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest req, Long creatorId) {
        log.info("➕ Usuario {} creando nueva conversación tipo: {}", creatorId, req.getType());

        // --- INICIO LÓGICA MEJORADA: Buscar chat 1-a-1 existente ---
        // Si es "direct" y solo hay un participante, buscamos si ya existe
        if ("direct".equalsIgnoreCase(req.getType()) && req.getParticipantIds() != null && req.getParticipantIds().size() == 1) {
            Long otherUserId = req.getParticipantIds().get(0);
            if (!otherUserId.equals(creatorId)) { // Asegurarnos de que no es un chat consigo mismo
                List<Conversation> existing = conversationRepository
                        .findDirectConversationBetweenUsers(creatorId, otherUserId);

                if (!existing.isEmpty()) {
                    log.info("↪️ Encontrada conversación 1-a-1 existente (ID: {}). Devolviendo...", existing.get(0).getId());
                    return getConversationResponseById(existing.get(0).getId());
                }
            }
        }
        // --- FIN LÓGICA MEJORADA ---

        Conversation conv = new Conversation();
        conv.setType(req.getType() == null ? "direct" : req.getType());

        if ("direct".equalsIgnoreCase(conv.getType()) &&
                (req.getTitle() == null || req.getTitle().isEmpty())) {
            if (req.getParticipantIds() != null && req.getParticipantIds().size() == 1) {
                conv.setTitle(null);
            }
        } else {
            conv.setTitle(req.getTitle());
        }

        final Conversation savedConv = conversationRepository.save(conv);
        log.debug("💾 Conversación {} creada", savedConv.getId());

        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(savedConv);
        owner.setUserId(creatorId);
        owner.setRole("owner");
        conversationParticipantRepository.save(owner);

        if (req.getParticipantIds() != null && !req.getParticipantIds().isEmpty()) {
            List<Long> participantIdsToAdd = req.getParticipantIds().stream()
                    .filter(id -> id != null && !id.equals(creatorId))
                    .distinct()
                    .collect(Collectors.toList());

            List<Long> existingUserIds = userRepository.findAllById(participantIdsToAdd)
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());

            List<ConversationParticipant> newParticipants = existingUserIds.stream()
                    .map(uid -> {
                        ConversationParticipant p = new ConversationParticipant();
                        p.setConversation(savedConv);
                        p.setUserId(uid);
                        p.setRole("member");
                        return p;
                    })
                    .collect(Collectors.toList());

            if (!newParticipants.isEmpty()) {
                conversationParticipantRepository.saveAll(newParticipants);
                log.debug("👥 Añadidos {} participantes a conversación {}",
                        newParticipants.size(), savedConv.getId());
            }
        }

        log.info("✅ Conversación {} creada exitosamente", savedConv.getId());
        return getConversationResponseById(savedConv.getId());
    }

    /**
     * Añade un participante a una conversación.
     * (Este método no tiene cambios)
     */
    @Transactional
    public void addParticipant(Long conversationId, Long requesterId, AddParticipantRequest req) {
        log.info("👤 Usuario {} añadiendo participante {} a conversación {}",
                requesterId, req.getUserId(), conversationId);

        permissionService.validateIsOwner(requesterId, conversationId);

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));

        Long userId = req.getUserId();

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("El usuario a añadir no existe");
        }

        if (conversationParticipantRepository.existsByConversation_IdAndUserId(conversationId, userId)) {
            log.warn("⚠️ Usuario {} ya está en conversación {}", userId, conversationId);
            return;
        }

        ConversationParticipant p = new ConversationParticipant();
        p.setConversation(conv);
        p.setUserId(userId);
        p.setRole(req.getRole() == null || req.getRole().isEmpty() ? "member" : req.getRole());
        conversationParticipantRepository.save(p);

        log.info("✅ Participante {} añadido a conversación {}", userId, conversationId);
    }

    /**
     * Elimina un participante de una conversación.
     * (Este método no tiene cambios)
     */
    @Transactional
    public void removeParticipant(Long conversationId, Long requesterId, Long userIdToRemove) {
        log.info("🗑️ Usuario {} eliminando participante {} de conversación {}",
                requesterId, userIdToRemove, conversationId);

        permissionService.validateCanRemoveParticipant(requesterId, userIdToRemove, conversationId);

        ConversationParticipant participant = conversationParticipantRepository
                .findByConversation_IdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El participante no se encuentra en esta conversación"
                ));

        conversationParticipantRepository.delete(participant);
        log.info("✅ Participante {} eliminado de conversación {}", userIdToRemove, conversationId);
    }

    /**
     * Obtiene la lista de participantes de una conversación.
     * (Este método no tiene cambios)
     */
    @Transactional(readOnly = true)
    public List<ParticipantDto> getParticipants(Long conversationId) {
        List<ConversationParticipant> participants = conversationParticipantRepository
                .findByConversation_Id(conversationId);

        if (participants.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = participants.stream()
                .map(ConversationParticipant::getUserId)
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return participants.stream()
                .map(p -> toParticipantDto(p, userMap))
                .collect(Collectors.toList());
    }

    // --- Métodos Helpers (Sin cambios) ---

    private ConversationResponse toResponse(Conversation conv, List<ParticipantDto> participants,
                                            LastMessageDto lastMessage) {
        ConversationResponse r = new ConversationResponse();
        r.setId(conv.getId());
        r.setType(conv.getType());
        r.setTitle(conv.getTitle());
        r.setCreatedAt(conv.getCreatedAt());
        r.setParticipants(participants);
        r.setLastMessage(lastMessage);
        return r;
    }

    private ConversationResponse getConversationResponseById(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversación no encontrada con ID: " + conversationId
                ));

        List<ParticipantDto> participants = getParticipants(conversationId);

        // OJO: Aquí no podemos obtener la 'encryptedKey' correcta para el
        // usuario que acaba de crear el chat, porque no sabemos quién es.
        // Devolvemos 'null' en la clave. El frontend tendrá que manejarlo.
        LastMessageDto lastMessageDto = messageRepository
                .findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                .map(msg -> new LastMessageDto(msg.getCiphertext(), msg.getCreatedAt(), null))
                .orElse(null);

        return toResponse(conv, participants, lastMessageDto);
    }

    private ParticipantDto toParticipantDto(ConversationParticipant p, Map<Long, User> userMap) {
        ParticipantDto dto = new ParticipantDto();
        dto.setUserId(p.getUserId());
        dto.setRole(p.getRole());
        dto.setJoinedAt(p.getJoinedAt());

        User user = userMap.get(p.getUserId());
        if (user != null) {
            dto.setUsername(user.getUsername());
        } else {
            dto.setUsername("Usuario Desconocido");
        }

        return dto;
    }
}