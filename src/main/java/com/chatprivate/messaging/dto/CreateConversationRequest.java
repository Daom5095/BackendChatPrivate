package com.chatprivate.messaging.dto;

import java.util.List;

public class CreateConversationRequest {
    private String type;
    private String title;
    private List<Long> participantIds;

    public CreateConversationRequest() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<Long> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<Long> participantIds) { this.participantIds = participantIds; }
}
