package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.messaging.model.*;
import com.chatprivate.messaging.repository.*;
import com.chatprivate.security.PermissionService;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     *
     * SEGURIDAD (ACTUALIZADA):
     * - Valida que el usuario sea participante ANTES de devolver cualquier dato
     * - Solo devuelve las claves de cifrado que pertenecen al usuario
     *
     * @param conversationId ID del chat
     * @param userId         ID del usuario que pide el historial
     * @return Lista de DTOs con el historial
     */
    @Transactional(readOnly = true)
    public List<MessageHistoryDto> getMessageHistory(Long conversationId, Long userId) {
        log.info("📚 Usuario {} solicitando historial de conversación {}", userId, conversationId);

        // 🔒 VALIDACIÓN DE SEGURIDAD
        // Si el usuario NO es participante, lanza AccessDeniedException
        permissionService.validateCanReadMessages(userId, conversationId);
        log.debug("✅ Usuario {} autorizado para leer conversación {}", userId, conversationId);

        // 1. Obtener todos los mensajes del chat, ordenados por fecha
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            log.debug("📭 No hay mensajes en conversación {}", conversationId);
            return Collections.emptyList();
        }

        // 2. Obtener los IDs de todos esos mensajes
        List<Long> messageIds = messages.stream()
                .map(Message::getId)
                .collect(Collectors.toList());

        // 3. ¡Clave! Buscar SOLO las claves que pertenecen a ESE usuario
        List<MessageKey> userKeys = messageKeyRepository
                .findByMessage_IdInAndRecipientId(messageIds, userId);

        // 4. Convertir las claves en un Mapa para búsqueda rápida
        Map<Long, String> keyMap = userKeys.stream()
                .collect(Collectors.toMap(
                        mk -> mk.getMessage().getId(),
                        MessageKey::getEncryptedKey
                ));

        // 5. Construir la respuesta
        List<MessageHistoryDto> history = messages.stream()
                .map(msg -> {
                    String encryptedKey = keyMap.get(msg.getId());
                    if (encryptedKey == null) {
                        // Esto puede pasar si el usuario fue añadido DESPUÉS de que se enviara este mensaje
                        log.debug("⚠️ Usuario {} no tiene clave para mensaje {}", userId, msg.getId());
                        return null; // Lo filtraremos después
                    }
                    return new MessageHistoryDto(
                            msg.getId(),
                            msg.getSenderId(),
                            msg.getCiphertext(),
                            encryptedKey,
                            msg.getCreatedAt()
                    );
                })
                .filter(dto -> dto != null) // Quito los mensajes sin clave
                .collect(Collectors.toList());

        log.info("✅ Devueltos {} mensajes para usuario {} en conversación {}",
                history.size(), userId, conversationId);

        return history;
    }

    /**
     * Obtiene la lista de conversaciones de un usuario.
     *
     * SEGURIDAD:
     * - Solo devuelve las conversaciones donde el usuario ES participante
     * - Incluye el último mensaje con la clave cifrada específica para el usuario
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

        // 2. Busco todos los participantes de esas conversaciones
        List<ConversationParticipant> allParticipants = conversationParticipantRepository
                .findByConversation_IdIn(conversationIds);

        // 3. Busco los datos de User de todos los participantes
        List<Long> allUserIds = allParticipants.stream()
                .map(ConversationParticipant::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 4. Agrupo los participantes por conversación
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

                    // 6. Obtengo el último mensaje (con su clave específica para el usuario)
                    LastMessageDto lastMessageDto = messageRepository
                            .findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                            .map(msg -> {
                                // Busco la clave específica para el usuario actual
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
                .collect(Collectors.toList());

        log.info("✅ Devueltas {} conversaciones para usuario {}", response.size(), userId);
        return response;
    }

    /**
     * Crea una nueva conversación.
     */
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest req, Long creatorId) {
        log.info("➕ Usuario {} creando nueva conversación tipo: {}", creatorId, req.getType());

        Conversation conv = new Conversation();
        conv.setType(req.getType() == null ? "direct" : req.getType());

        // Lógica para no poner título en chats directos (1 a 1)
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

        // Añado al creador como "owner"
        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(savedConv);
        owner.setUserId(creatorId);
        owner.setRole("owner");
        conversationParticipantRepository.save(owner);

        // Añado al resto de participantes
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
     *
     * SEGURIDAD (ACTUALIZADA):
     * - Solo el owner puede añadir participantes
     */
    @Transactional
    public void addParticipant(Long conversationId, Long requesterId, AddParticipantRequest req) {
        log.info("👤 Usuario {} añadiendo participante {} a conversación {}",
                requesterId, req.getUserId(), conversationId);

        // 🔒 VALIDACIÓN DE SEGURIDAD
        permissionService.validateIsOwner(requesterId, conversationId);

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));

        Long userId = req.getUserId();

        // Valido que el usuario a añadir exista
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("El usuario a añadir no existe");
        }

        // Evito duplicados
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
     *
     * SEGURIDAD (ACTUALIZADA):
     * - Usa el PermissionService para validar permisos
     */
    @Transactional
    public void removeParticipant(Long conversationId, Long requesterId, Long userIdToRemove) {
        log.info("🗑️ Usuario {} eliminando participante {} de conversación {}",
                requesterId, userIdToRemove, conversationId);

        // 🔒 VALIDACIÓN DE SEGURIDAD
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
     *
     * SEGURIDAD (ACTUALIZADA):
     * - Solo los participantes pueden ver quiénes son los otros participantes
     */
    @Transactional(readOnly = true)
    public List<ParticipantDto> getParticipants(Long conversationId) {
        // NOTA: Esta validación se hace en el Controller
        // porque aquí no tengo el userId directamente

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

    // --- Métodos Helpers ---

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