package com.chatprivate.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO para mensajes enviados por WebSocket/STOMP.

 * Este objeto representa un mensaje cifrado que el cliente
 * envía a través de WebSocket.
 */
@Data
@NoArgsConstructor
public class StompMessagePayload {

    /**
     * ID de la conversación a la que pertenece el mensaje.
     * OBLIGATORIO.
     */
    @NotNull(message = "El ID de conversación es obligatorio")
    private Long conversationId;

    /**
     * Contenido del mensaje cifrado con AES (en Base64).
     *
     * VALIDACIONES:
     * - No puede estar vacío
     * - Máximo 10KB (10240 caracteres en Base64)
     *   Esto equivale a ~7.5KB de datos originales
     */
    @NotBlank(message = "El mensaje cifrado no puede estar vacío")
    @Size(max = 10240, message = "El mensaje es demasiado largo (máximo 10KB)")
    private String ciphertext;

    /**
     * ID del usuario que envía el mensaje.
     *
     * NOTA: Este campo es REDUNDANTE porque ya tengo el usuario
     * en el Principal de la autenticación WebSocket.
     * Lo dejo por compatibilidad con el cliente, pero NO lo uso
     * en el servidor (uso el Principal para seguridad).
     */
    private Long senderId;

    /**
     * Mapa de claves AES cifradas para cada destinatario.
     *
     * Formato: { "recipientId": "clave_AES_cifrada_con_RSA_del_recipient" }
     *
     * VALIDACIONES:
     * - No puede estar vacío (cada mensaje necesita al menos un destinatario)
     * - Máximo 100 destinatarios (para prevenir ataques DoS)
     *
     * IMPORTANTE: El servidor validará que TODOS los destinatarios
     * sean participantes de la conversación.
     */
    @NotEmpty(message = "Debe haber al menos un destinatario para el mensaje")
    @Size(max = 100, message = "Demasiados destinatarios (máximo 100)")
    private Map<String, String> encryptedKeys;

    /**
     * Constructor completo (útil para tests).
     */
    public StompMessagePayload(Long conversationId, String ciphertext,
                               Long senderId, Map<String, String> encryptedKeys) {
        this.conversationId = conversationId;
        this.ciphertext = ciphertext;
        this.senderId = senderId;
        this.encryptedKeys = encryptedKeys;
    }
}