package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.CustomUserDetails;
import lombok.RequiredArgsConstructor; // Importar
import lombok.extern.slf4j.Slf4j; // Importar
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;


@Controller
@RequiredArgsConstructor // Para inyección
@Slf4j // Para logging
public class StompChatController {

    private final MessageService messageService;

    @MessageMapping("/chat.send") // destino /app/chat.send
    public void receiveMessage(@Payload StompMessagePayload payload, Authentication authentication) {
        // --- OBTENER SENDER ID DE FORMA ROBUSTA ---
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            // Reemplazamos System.err por log.error
            log.error("Error en StompChatController: No se pudo obtener CustomUserDetails de la autenticación.");
            return;
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long senderId = userDetails.getUser().getId();
        // --- FIN OBTENER SENDER ID ---

        // Reemplazamos System. out por log.info
        log.info("Mensaje recibido de senderId: {} para convId: {}", senderId, payload.getConversationId());

        messageService.sendAndStoreMessage(senderId, payload.getConversationId(),
                payload.getCiphertext(), payload.getEncryptedKeys());
    }
}