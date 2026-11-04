package com.chatprivate.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Este objeto reúne los datos de un mensaje que se muestra en el historial. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageHistoryDto {
    private Long messageId;
    private Long senderId;
    private String ciphertext;
    private String encryptedKey; // La clave AES cifrada, específica para el usuario que pide el historial
    private Instant createdAt;
}