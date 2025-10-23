package com.chatprivate.messaging.dto;

import java.util.Map;

public class StompMessagePayload {

    private Long conversationId;
    private String ciphertext;
    private Long senderId;
    // --- CORRECCIÓN ---
    // JSON siempre tiene claves String. Cambiamos Long a String.
    private Map<String, String> encryptedKeys;

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

    // --- CORRECCIÓN ---
    public Map<String, String> getEncryptedKeys() {
        return encryptedKeys;
    }

    // --- CORRECCIÓN ---
    public void setEncryptedKeys(Map<String, String> encryptedKeys) {
        this.encryptedKeys = encryptedKeys;
    }
}