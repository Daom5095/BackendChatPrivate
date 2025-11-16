package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * Controlador STOMP para mensajes de chat en tiempo real.
 *
 * ACTUALIZADO EN SESIÓN 2:
 * - Añadida validación del payload con @Valid
 * - Mejor manejo de errores (delegado a WebSocketExceptionHandler)
 * - Logging mejorado
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class StompChatController {

    private final MessageService messageService;

    /**
     * Maneja los mensajes enviados por los clientes al destino "/app/chat.send".
     *
     * FLUJO DE SEGURIDAD:
     * 1. Spring valida el payload con @Valid (campos obligatorios, tamaños, etc.)
     * 2. Extrae el usuario autenticado del Authentication
     * 3. Delega al MessageService, que valida permisos y guarda el mensaje
     * 4. Si hay algún error, WebSocketExceptionHandler lo captura y notifica al cliente
     *
     * @param payload El DTO del mensaje (validado automáticamente por @Valid)
     * @param authentication El objeto de autenticación del usuario (inyectado por Spring)
     */
    @MessageMapping("/chat.send")
    public void receiveMessage(@Valid @Payload StompMessagePayload payload,
                               Authentication authentication) {

        // ============================================
        // VALIDACIÓN DE AUTENTICACIÓN
        // ============================================

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            log.error("❌ Error crítico: No se pudo obtener CustomUserDetails de la autenticación en WebSocket");
            // Lanzo excepción que será manejada por WebSocketExceptionHandler
            throw new IllegalStateException("Sesión de autenticación inválida");
        }

        // Extraigo el ID del usuario autenticado
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long senderId = userDetails.getUser().getId();

        // ============================================
        // LOGGING DE AUDITORÍA
        // ============================================

        log.info("📨 WebSocket - Mensaje recibido: senderId={}, conversationId={}, tamañoCiphertext={}, destinatarios={}",
                senderId,
                payload.getConversationId(),
                payload.getCiphertext() != null ? payload.getCiphertext().length() : 0,
                payload.getEncryptedKeys() != null ? payload.getEncryptedKeys().size() : 0
        );

        // ============================================
        // VALIDACIÓN DE SEGURIDAD ADICIONAL
        // ============================================

        // Verifico que el senderId del payload (si viene) coincida con el usuario autenticado
        // Esto previene que un cliente malicioso intente suplantar a otro usuario
        if (payload.getSenderId() != null && !payload.getSenderId().equals(senderId)) {
            log.warn("🚨 INTENTO DE SUPLANTACIÓN: El cliente intentó enviar un mensaje con senderId={} " +
                            "pero el usuario autenticado es senderId={}",
                    payload.getSenderId(), senderId);

            throw new SecurityException("No puedes enviar mensajes en nombre de otro usuario");
        }

        // ============================================
        // DELEGACIÓN AL SERVICIO
        // ============================================

        // Delego toda la lógica de guardado y reenvío al MessageService
        // Si el usuario NO es participante, MessageService lanzará AccessDeniedException
        // que será capturada por WebSocketExceptionHandler
        messageService.sendAndStoreMessage(
                senderId,
                payload.getConversationId(),
                payload.getCiphertext(),
                payload.getEncryptedKeys()
        );

        log.debug("✅ Mensaje procesado exitosamente para conversationId={}", payload.getConversationId());
    }
}