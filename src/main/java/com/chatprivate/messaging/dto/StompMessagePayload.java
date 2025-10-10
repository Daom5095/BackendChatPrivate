package com.chatprivate.messaging.dto;

import java.util.Map;

public class StompMessagePayload {

    private Long conversationId;
    private String ciphertext;
    private Long senderId;
    private Map<Long, String> encryptedKeys;

    // Constructor vacío
    public StompMessagePayload() {}

    // Getters y setters
    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Map<Long, String> getEncryptedKeys() {
        return encryptedKeys;
    }

    public void setEncryptedKeys(Map<Long, String> encryptedKeys) {
        this.encryptedKeys = encryptedKeys;
    }
}
