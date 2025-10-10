package com.chatprivate.messaging.dto;

import java.util.Map;

public class SendMessageRequest {

    private Long conversationId;
    private String ciphertext;
    private Map<Long, String> encryptedKeys;

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

    public Map<Long, String> getEncryptedKeys() {
        return encryptedKeys;
    }

    public void setEncryptedKeys(Map<Long, String> encryptedKeys) {
        this.encryptedKeys = encryptedKeys;
    }
}
