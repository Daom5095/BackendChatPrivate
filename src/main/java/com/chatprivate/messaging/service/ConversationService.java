package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.messaging.model.*;
import com.chatprivate.messaging.repository.*;
import com.chatprivate.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final UserRepository userRepository;

    /**
     * Crea conversación, agrega al creador como owner y opcionalmente otros participantes.
     */
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest req, Long creatorId) {
        Conversation conv = new Conversation();
        conv.setType(req.getType() == null ? "direct" : req.getType());
        conv.setTitle(req.getTitle());
        conv = conversationRepository.save(conv);

        // Owner (creador)
        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(conv);
        owner.setUserId(creatorId);
        owner.setRole("owner");
        participantRepository.save(owner);

        // Agregar participantes iniciales (si se envían)
        if (req.getParticipantIds() != null) {
            for (Long uId : req.getParticipantIds()) {
                if (uId == null || uId.equals(creatorId)) continue;
                if (!userRepository.existsById(uId)) continue; // ignorar usuarios no existentes
                if (!participantRepository.existsByConversation_IdAndUserId(conv.getId(), uId)) {
                    ConversationParticipant p = new ConversationParticipant();
                    p.setConversation(conv);
                    p.setUserId(uId);
                    p.setRole("member");
                    participantRepository.save(p);
                }
            }
        }

        return toResponse(conv);
    }

    /**
     * Añadir participante: solo el owner puede añadir.
     */
    @Transactional
    public void addParticipant(Long conversationId, Long requesterId, AddParticipantRequest req) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));

        // solo owner puede añadir
        if (!isOwner(conversationId, requesterId)) {
            throw new AccessDeniedException("Solo el owner puede añadir participantes");
        }

        Long userId = req.getUserId();
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Usuario a añadir no existe");
        }

        if (participantRepository.existsByConversation_IdAndUserId(conversationId, userId)) {
            return; // ya está agregado (no falla)
        }

        ConversationParticipant p = new ConversationParticipant();
        p.setConversation(conv);
        p.setUserId(userId);
        p.setRole(req.getRole() == null ? "member" : req.getRole());
        participantRepository.save(p);
    }

    /**
     * Eliminar participante: lo puede hacer el owner o el propio usuario (retirarse).
     */
    @Transactional
    public void removeParticipant(Long conversationId, Long requesterId, Long userIdToRemove) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));

        boolean requesterIsOwner = isOwner(conversationId, requesterId);
        if (!requesterIsOwner && !requesterId.equals(userIdToRemove)) {
            throw new AccessDeniedException("No autorizado para eliminar a ese participante");
        }

        Optional<ConversationParticipant> opt = participantRepository
                .findByConversation_IdAndUserId(conversationId, userIdToRemove);
        opt.ifPresent(participantRepository::delete);
    }

    /**
     * Listo participante de una conversación
     */
    @Transactional(readOnly = true)
    public List<ParticipantDto> getParticipants(Long conversationId) {
        return participantRepository.findByConversation_Id(conversationId)
                .stream()
                .map(this::toParticipantDto)
                .collect(Collectors.toList());
    }


     //lista de conversaciones en las que está el Usuario

    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(Long userId) {
        return participantRepository.findByUserId(userId)
                .stream()
                .map(cp -> toResponse(cp.getConversation()))
                .collect(Collectors.toList());
    }

    // helpers
    private boolean isOwner(Long conversationId, Long userId) {
        return participantRepository.findByConversation_IdAndUserId(conversationId, userId)
                .map(p -> "owner".equalsIgnoreCase(p.getRole()))
                .orElse(false);
    }

    private ConversationResponse toResponse(Conversation conv) {
        ConversationResponse r = new ConversationResponse();
        r.setId(conv.getId());
        r.setType(conv.getType());
        r.setTitle(conv.getTitle());
        r.setCreatedAt(conv.getCreatedAt());
        List<ParticipantDto> parts = participantRepository.findByConversation_Id(conv.getId())
                .stream().map(this::toParticipantDto).collect(Collectors.toList());
        r.setParticipants(parts);
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
