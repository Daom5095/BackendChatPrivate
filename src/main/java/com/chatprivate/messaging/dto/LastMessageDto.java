package com.chatprivate.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Este objeto guarda el resumen del último mensaje de una conversación. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastMessageDto {

    private String text;
    private Instant createdAt;
    private String encryptedKey;
}