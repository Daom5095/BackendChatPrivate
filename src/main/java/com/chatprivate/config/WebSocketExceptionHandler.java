package com.chatprivate.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para WebSocket/STOMP.
 *

 * Este handler captura las excepciones y envía un mensaje de error
 * al cliente a través del canal "/queue/errors".
 *
 * El cliente debe subscribirse a "/user/queue/errors" para recibir
 * estos mensajes.
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class WebSocketExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Maneja errores de permisos (AccessDeniedException).
     *
     * Se lanza cuando un usuario intenta enviar un mensaje a una
     * conversación donde NO es participante.
     *
     * @param exception La excepción de acceso denegado
     * @param principal El usuario que causó el error
     * @return Un mapa con información del error
     */
    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleAccessDenied(AccessDeniedException exception, Principal principal) {
        String username = (principal != null) ? principal.getName() : "unknown";

        log.warn("WebSocket - Acceso denegado para usuario {}: {}", username, exception.getMessage());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("type", "ACCESS_DENIED");
        errorResponse.put("message", exception.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        return errorResponse;
    }

    /**
     * Maneja errores de argumentos ilegales.
     *
     * Se lanza cuando el cliente envía datos inválidos (ej. mapa de
     * claves vacío, IDs inválidos, etc.)
     *
     * @param exception La excepción de argumento ilegal
     * @param principal El usuario que causó el error
     * @return Un mapa con información del error
     */
    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException exception, Principal principal) {
        String username = (principal != null) ? principal.getName() : "unknown";

        log.warn("⚠️ WebSocket - Argumento inválido de usuario {}: {}", username, exception.getMessage());

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("type", "INVALID_ARGUMENT");
        errorResponse.put("message", exception.getMessage());
        errorResponse.put("timestamp", System.currentTimeMillis());

        return errorResponse;
    }

    /**
     * Catch-all: Maneja cualquier otra excepción no prevista.
     *
     * @param exception La excepción inesperada
     * @param principal El usuario que causó el error
     * @return Un mapa con información del error
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> handleGenericException(Exception exception, Principal principal) {
        String username = (principal != null) ? principal.getName() : "unknown";

        // Este es un error inesperado, lo logueo como ERROR con stack trace
        log.error("WebSocket - Error inesperado de usuario {}: {}",
                username, exception.getMessage(), exception);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("type", "INTERNAL_ERROR");
        errorResponse.put("message", "Ocurrió un error al procesar tu mensaje. Por favor, inténtalo de nuevo.");
        errorResponse.put("timestamp", System.currentTimeMillis());

        return errorResponse;
    }
}