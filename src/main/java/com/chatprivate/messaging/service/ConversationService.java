package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.messaging.model.*;
import com.chatprivate.messaging.repository.*;
// Asumo que tu entidad User está en este paquete, ajusta si es necesario
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationParticipantRepository conversationParticipantRepository,
                               UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest req, Long creatorId) {
        Conversation conv = new Conversation();
        conv.setType(req.getType() == null ? "direct" : req.getType());
        conv.setTitle(req.getTitle());


        final Conversation savedConv = conversationRepository.save(conv);

        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(savedConv);
        owner.setUserId(creatorId);
        owner.setRole("owner");
        conversationParticipantRepository.save(owner);

        if (req.getParticipantIds() != null && !req.getParticipantIds().isEmpty()) {
            List<Long> participantIdsToAdd = req.getParticipantIds().stream()
                    .filter(id -> id != null && !id.equals(creatorId))
                    .collect(Collectors.toList());

            List<Long> existingUserIds = userRepository.findAllById(participantIdsToAdd)
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());

            List<ConversationParticipant> newParticipants = existingUserIds.stream().map(userId -> {
                ConversationParticipant p = new ConversationParticipant();
                // Usamos la variable 'final' que no cambia.
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
            return;
        }

        ConversationParticipant p = new ConversationParticipant();
        // Asignamos el objeto 'Conversation' que ya obtuvimos.
        p.setConversation(conv);
        p.setUserId(userId);
        p.setRole(req.getRole() == null ? "member" : req.getRole());
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

    @Transactional(readOnly = true)
    public List<ParticipantDto> getParticipants(Long conversationId) {
        return conversationParticipantRepository.findByConversation_Id(conversationId)
                .stream()
                .map(this::toParticipantDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(Long userId) {
        List<Conversation> conversations = conversationParticipantRepository.findConversationsByUserId(userId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> conversationIds = conversations.stream().map(Conversation::getId).collect(Collectors.toList());

        List<ConversationParticipant> allParticipants = conversationParticipantRepository.findByConversation_IdIn(conversationIds);

        Map<Long, List<ParticipantDto>> participantsByConvId = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getConversation().getId(),
                        Collectors.mapping(this::toParticipantDto, Collectors.toList())
                ));

        return conversations.stream()
                .map(conv -> toResponse(conv, participantsByConvId.getOrDefault(conv.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    private ConversationResponse getConversationResponseById(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));
        List<ParticipantDto> participants = getParticipants(conversationId);
        return toResponse(conv, participants);
    }

    private boolean isOwner(Long conversationId, Long userId) {
        return conversationParticipantRepository.findByConversation_IdAndUserId(conversationId, userId)
                .map(p -> "owner".equalsIgnoreCase(p.getRole()))
                .orElse(false);
    }

    private ConversationResponse toResponse(Conversation conv, List<ParticipantDto> participants) {
        ConversationResponse r = new ConversationResponse();
        r.setId(conv.getId());
        r.setType(conv.getType());
        r.setTitle(conv.getTitle());
        r.setCreatedAt(conv.getCreatedAt());
        r.setParticipants(participants);
        return r;
    }

    private ParticipantDto toParticipantDto(ConversationParticipant p) {
        ParticipantDto dto = new ParticipantDto();
        dto.setUserId(p.getUserId());
        dto.setRole(p.getRole());
        dto.setJoinedAt(p.getJoinedAt());
        return dto;
    }
}