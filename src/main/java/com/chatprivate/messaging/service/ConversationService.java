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

@Service
@RequiredArgsConstructor // Lombok se encarga del constructor
@Slf4j // Para logging
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final UserRepository userRepository;

    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;


    // --- MÉTODO getMessageHistory (SIN CAMBIOS) ---
    @Transactional(readOnly = true)
    public List<MessageHistoryDto> getMessageHistory(Long conversationId, Long userId) {
        log.info("Cargando historial para conversación {} para usuario {}", conversationId, userId);
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> messageIds = messages.stream().map(Message::getId).collect(Collectors.toList());
        List<MessageKey> userKeys = messageKeyRepository.findByMessage_IdInAndRecipientId(messageIds, userId);
        Map<Long, String> keyMap = userKeys.stream()
                .collect(Collectors.toMap(mk -> mk.getMessage().getId(), MessageKey::getEncryptedKey));

        return messages.stream()
                .map(msg -> {
                    String encryptedKey = keyMap.get(msg.getId());
                    if (encryptedKey == null) {
                        log.warn("Usuario {} no tiene clave para el mensaje {}", userId, msg.getId());
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
    }


    // --- MÉTODO toParticipantDto (SIN CAMBIOS) ---
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

    // --- MÉTODO getParticipants (SIN CAMBIOS) ---
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


    // --- ★★★ CAMBIO PRINCIPAL AQUÍ ★★★ ---
    // --- MÉTODO getUserConversations MODIFICADO ---
    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(Long userId) {
        List<Conversation> conversations = conversationParticipantRepository.findConversationsByUserId(userId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> conversationIds = conversations.stream().map(Conversation::getId).collect(Collectors.toList());
        List<ConversationParticipant> allParticipants = conversationParticipantRepository.findByConversation_IdIn(conversationIds);

        if (allParticipants.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> allUserIds = allParticipants.stream()
                .map(ConversationParticipant::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, List<ParticipantDto>> participantsByConvId = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getConversation().getId(),
                        Collectors.mapping(p -> toParticipantDto(p, userMap), Collectors.toList())
                ));

        // Mapeamos las conversaciones a ConversationResponse
        return conversations.stream()
                .map(conv -> {
                    List<ParticipantDto> participants = participantsByConvId.getOrDefault(conv.getId(), Collections.emptyList());

                    // --- LÓGICA MODIFICADA PARA OBTENER EL ÚLTIMO MENSAJE Y SU CLAVE ---
                    LastMessageDto lastMessageDto = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                            .map(msg -> {
                                // Ahora que tengo el último mensaje (msg), busco su clave para el usuario actual (userId)
                                String encryptedKey = messageKeyRepository.findByMessage_IdAndRecipientId(msg.getId(), userId)
                                        .map(MessageKey::getEncryptedKey)
                                        .orElse(null); // Si no hay clave, encryptedKey será null

                                if (encryptedKey == null) {
                                    log.warn("No se encontró MessageKey para el último mensaje (msgId: {}) y el usuario (userId: {}) en la conv {}", msg.getId(), userId, conv.getId());
                                }

                                // Devolvemos el DTO completo
                                return new LastMessageDto(msg.getCiphertext(), msg.getCreatedAt(), encryptedKey);
                            })
                            .orElse(null); // Si no hay mensajes, 'lastMessage' será null

                    return toResponse(conv, participants, lastMessageDto);
                })
                .collect(Collectors.toList());
    }

    // --- MÉTODO createConversation (SIN CAMBIOS) ---
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest req, Long creatorId) {
        Conversation conv = new Conversation();
        conv.setType(req.getType() == null ? "direct" : req.getType());
        if ("direct".equalsIgnoreCase(conv.getType()) && (req.getTitle() == null || req.getTitle().isEmpty())) {
            if (req.getParticipantIds() != null && req.getParticipantIds().size() == 1) {
                conv.setTitle(null);
            }
        } else {
            conv.setTitle(req.getTitle());
        }

        final Conversation savedConv = conversationRepository.save(conv);

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

            List<ConversationParticipant> newParticipants = existingUserIds.stream().map(userId -> {
                ConversationParticipant p = new ConversationParticipant();
                p.setConversation(savedConv);
                p.setUserId(userId);
                p.setRole("member");
                return p;
            }).collect(Collectors.toList());

            if (!newParticipants.isEmpty()) {
                conversationParticipantRepository.saveAll(newParticipants);
            }
        }

        return getConversationResponseById(savedConv.getId());
    }


    // --- MÉTODO getConversationResponseById MODIFICADO ---
    private ConversationResponse getConversationResponseById(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada con ID: " + conversationId));

        List<ParticipantDto> participants = getParticipants(conversationId);

        // --- LÓGICA MODIFICADA ---
        LastMessageDto lastMessageDto = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                .map(msg -> {
                    // Busca la clave. Asumimos que si estamos creando/obteniendo por ID, el usuario actual
                    // (que no tenemos aquí) será el que la pida.
                    // Para createConversation, el lastMessage será null de todos modos.
                    // Para getConversation (si existiera), necesitaríamos el userId.
                    // Por ahora, para 'create', lastMessageDto siempre será null.
                    return new LastMessageDto(msg.getCiphertext(), msg.getCreatedAt(), null);
                })
                .orElse(null);

        return toResponse(conv, participants, lastMessageDto);
    }

    // --- MÉTODO toResponse MODIFICADO ---
    private ConversationResponse toResponse(Conversation conv, List<ParticipantDto> participants, LastMessageDto lastMessage) {
        ConversationResponse r = new ConversationResponse();
        r.setId(conv.getId());
        r.setType(conv.getType());
        r.setTitle(conv.getTitle());
        r.setCreatedAt(conv.getCreatedAt());
        r.setParticipants(participants);
        r.setLastMessage(lastMessage); // <-- AÑADIDO
        return r;
    }

    // --- isOwner (SIN CAMBIOS) ---
    private boolean isOwner(Long conversationId, Long userId) {
        return conversationParticipantRepository.findByConversation_IdAndUserId(conversationId, userId)
                .map(p -> "owner".equalsIgnoreCase(p.getRole()))
                .orElse(false);
    }

    // --- Métodos addParticipant y removeParticipant (SIN CAMBIOS) ---
    @Transactional
    public void addParticipant(Long conversationId, Long requesterId, AddParticipantRequest req) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));

        if (!isOwner(conversationId, requesterId)) {
            throw new AccessDeniedException("Solo el owner puede añadir participantes");
        }
        Long userId = req.getUserId();
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Usuario a añadir no existe");
        }
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

    @Transactional
    public void removeParticipant(Long conversationId, Long requesterId, Long userIdToRemove) {
        boolean requesterIsOwner = isOwner(conversationId, requesterId);
        if (!requesterIsOwner && !requesterId.equals(userIdToRemove)) {
            throw new AccessDeniedException("No autorizado para eliminar a este participante");
        }
        ConversationParticipant participant = conversationParticipantRepository
                .findByConversation_IdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new IllegalArgumentException("El participante no se encuentra en esta conversación"));

        conversationParticipantRepository.delete(participant);
    }
}