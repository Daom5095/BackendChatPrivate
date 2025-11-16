package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication; // <-- AÑADIR IMPORTACIÓN
import org.springframework.stereotype.Controller;
// import java.security.Principal; // <-- ELIMINAR IMPORTACIÓN

@Controller
@RequiredArgsConstructor
@Slf4j
public class StompChatController {

    private final MessageService messageService;

    /**
     * Maneja los mensajes enviados por los clientes al destino "/app/chat.send".
     *
     * @param payload        El cuerpo del mensaje STOMP (mi DTO StompMessagePayload).
     * @param authentication El objeto de autenticación del usuario, inyectado
     * automáticamente gracias a mi WebSocketAuthChannelInterceptor.
     */
    @MessageMapping("/chat.send")
    public void receiveMessage(@Payload StompMessagePayload payload, Authentication authentication) { // <-- PARÁMETRO REVERTIDO

        // --- OBTENER SENDER ID DE FORMA ROBUSTA ---

        // El 'authentication' que recibimos es nuestro 'UsernamePasswordAuthenticationToken'
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) { // <-- LÓGICA REVERTIDA
            log.error("Error en StompChatController: No se pudo obtener CustomUserDetails de la autenticación.");
            return;
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal(); // <-- CAST REVERTIDO
        Long senderId = userDetails.getUser().getId();
        // --- FIN OBTENER SENDER ID ---

        log.info("Mensaje recibido de senderId: {} para convId: {}", senderId, payload.getConversationId());

        // Delega toda la lógica de guardado y reenvío al MessageService
        messageService.sendAndStoreMessage(senderId, payload.getConversationId(),
                payload.getCiphertext(), payload.getEncryptedKeys());
    }
}