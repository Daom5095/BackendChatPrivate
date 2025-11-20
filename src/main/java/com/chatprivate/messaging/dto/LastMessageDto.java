package com.chatprivate.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Este DTO representa el último mensaje de una conversación
 * para mostrarlo en la lista de chats del home.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastMessageDto {

    private String text;
    private Instant createdAt;
    private String encryptedKey;
}