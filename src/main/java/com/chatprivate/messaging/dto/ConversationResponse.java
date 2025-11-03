package com.chatprivate.messaging.dto;

import java.time.Instant;
import java.util.List;

public class ConversationResponse {
    private Long id;
    private String type;
    private String title;
    private Instant createdAt;
    private List<ParticipantDto> participants;

    // --- CAMBIO: AÑADIDO ESTE CAMPO ---
    private LastMessageDto lastMessage;
    // --- FIN DEL CAMBIO ---

    public ConversationResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<ParticipantDto> getParticipants() { return participants; }
    public void setParticipants(List<ParticipantDto> participants) { this.participants = participants; }

    // --- CAMBIO: AÑADIDO GETTER Y SETTER ---
    public LastMessageDto getLastMessage() { return lastMessage; }
    public void setLastMessage(LastMessageDto lastMessage) { this.lastMessage = lastMessage; }
    // --- FIN DEL CAMBIO ---
}