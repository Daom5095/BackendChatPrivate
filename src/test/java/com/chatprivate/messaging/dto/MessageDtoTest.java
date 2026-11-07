package com.chatprivate.messaging.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Aquí reviso que los DTO de envío representen y validen los datos esperados. */
class MessageDtoTest {

    @Test
    void sendMessageRequest_rejectsMissingRequiredFields() {
        // Compruebo que no se acepte un mensaje sin conversación, texto cifrado ni claves.
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var violations = validator.validate(new SendMessageRequest());

            assertEquals(3, violations.size());
            assertEquals(Set.of("conversationId", "ciphertext", "encryptedKeys"),
                    violations.stream()
                            .map(violation -> violation.getPropertyPath().toString())
                            .collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void sendMessageRequest_acceptsValidEncryptedPayload() {
        // Reviso que un payload completo no tenga errores de validación.
        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(81L);
        request.setCiphertext("texto-cifrado");
        request.setEncryptedKeys(Map.of("12", "clave-cifrada"));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }

    @Test
    void sendMessageResponse_keepsValuesAssignedByTheService() {
        // Compruebo que el DTO conserve los datos del mensaje creado.
        Instant createdAt = Instant.parse("2026-11-06T12:00:00Z");
        SendMessageResponse response = new SendMessageResponse();
        response.setMessageId(100L);
        response.setConversationId(81L);
        response.setCreatedAt(createdAt);

        assertEquals(100L, response.getMessageId());
        assertEquals(81L, response.getConversationId());
        assertEquals(createdAt, response.getCreatedAt());
    }
}
