package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.CustomUserDetails; // <-- IMPORTANTE: Importar
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication; // <-- IMPORTANTE: Importar
import org.springframework.stereotype.Controller;
// Quitamos la importación de java.security.Principal

@Controller
public class StompChatController {

    private final MessageService messageService;

    public StompChatController(MessageService messageService) {
        this.messageService = messageService;
    }

    @MessageMapping("/chat.send") // destino /app/chat.send
    public void receiveMessage(@Payload StompMessagePayload payload, Authentication authentication) { // <-- CAMBIO: Usar Authentication
        // --- OBTENER SENDER ID DE FORMA ROBUSTA ---
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            System.err.println("Error en StompChatController: No se pudo obtener CustomUserDetails de la autenticación.");
            // Considera lanzar una excepción o manejar el error adecuadamente
            return;
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long senderId = userDetails.getUser().getId();
        // --- FIN OBTENER SENDER ID ---

        System.out.println("Mensaje recibido de senderId: " + senderId + " para convId: " + payload.getConversationId()); // Log para confirmar

        messageService.sendAndStoreMessage(senderId, payload.getConversationId(),
                payload.getCiphertext(), payload.getEncryptedKeys());
    }
}