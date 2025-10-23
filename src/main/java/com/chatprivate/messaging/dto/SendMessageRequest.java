package com.chatprivate.messaging.dto;

import java.util.Map;

public class SendMessageRequest {

    private Long conversationId;
    private String ciphertext;
    // --- CORRECCIÓN ---
    // JSON siempre tiene claves String. Cambiamos Long a String.
    private Map<String, String> encryptedKeys;

    public SendMessageRequest() {}

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

    // --- CORRECCIÓN ---
    public Map<String, String> getEncryptedKeys() {
        return encryptedKeys;
    }

    // --- CORRECCIÓN ---
    public void setEncryptedKeys(Map<String, String> encryptedKeys) {
        this.encryptedKeys = encryptedKeys;
    }
}