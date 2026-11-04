package com.chatprivate.messaging.dto;

import java.time.Instant;

/** Esta es la información que devolvemos después de guardar un mensaje. */
public class SendMessageResponse {
    private Long messageId;
    private Long conversationId;
    private Instant createdAt;

    public SendMessageResponse() {}

    // Getters & Setters
    public Long getMessageId() {
        return messageId;
    }
    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getConversationId() {
        return conversationId;
    }
    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
