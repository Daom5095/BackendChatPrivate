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
    // El frontend espera "text" pero el modelo lo llama "ciphertext"
    // Usamos el mismo nombre que el modelo (ciphertext) para ser consistentes
    // y lo renombraremos en el servicio si es necesario,
    // o simplemente podemos llamarlo 'text' aquí.

    // Vamos a llamarlo 'text' para que coincida con el frontend
    private String text;
    private Instant createdAt;
    private String encryptedKey;
}