package com.chatprivate.messaging.dto;

import jakarta.validation.constraints.NotBlank; // Importar
import jakarta.validation.constraints.NotEmpty; // Importar
import jakarta.validation.constraints.NotNull; // Importar
import java.util.Map;

public class SendMessageRequest {

    @NotNull(message = "El ID de la conversación es obligatorio")
    private Long conversationId;

    @NotBlank(message = "El texto cifrado (ciphertext) no puede estar vacío")
    private String ciphertext;

    // El mapa de claves no puede ser nulo ni estar vacío
    @NotEmpty(message = "El mapa de claves cifradas (encryptedKeys) es obligatorio y no puede estar vacío")
    private Map<String, String> encryptedKeys;

    public SendMessageRequest() {}

    // ... (getters y setters sin cambios) ...

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

    public Map<String, String> getEncryptedKeys() {
        return encryptedKeys;
    }

    public void setEncryptedKeys(Map<String, String> encryptedKeys) {
        this.encryptedKeys = encryptedKeys;
    }
}