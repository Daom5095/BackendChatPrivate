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


/**
 * Controlador para manejar mensajes STOMP (WebSocket).
 * A diferencia de @RestController, este usa @Controller
 * y responde a destinos de @MessageMapping.
 */
@Controller
@RequiredArgsConstructor // Para inyección
@Slf4j // Para logging
public class StompChatController {

    private final MessageService messageService;

    /**
     * Maneja los mensajes enviados por los clientes al destino "/app/chat.send".
     *
     * @param payload        El cuerpo del mensaje STOMP (mi DTO StompMessagePayload).
     * @param authentication El objeto de autenticación del usuario, inyectado
     * automáticamente gracias a mi WebSocketAuthChannelInterceptor.
     */
    @MessageMapping("/chat.send") // destino /app/chat.send
    public void receiveMessage(@Payload StompMessagePayload payload, Authentication authentication) {

        // --- OBTENER SENDER ID DE FORMA ROBUSTA ---
        // Nunca confío en el senderId que viene en el payload.
        // Siempre lo obtengo del principal de seguridad asociado a la conexión WebSocket.
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("Error en StompChatController: No se pudo obtener CustomUserDetails de la autenticación.");
            // Si esto falla, algo está muy mal con la configuración de seguridad del interceptor.
            return;
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long senderId = userDetails.getUser().getId();
        // --- FIN OBTENER SENDER ID ---

        log.info("Mensaje recibido de senderId: {} para convId: {}", senderId, payload.getConversationId());

        // Delega toda la lógica de guardado y reenvío al MessageService
        messageService.sendAndStoreMessage(senderId, payload.getConversationId(),
                payload.getCiphertext(), payload.getEncryptedKeys());
    }
}