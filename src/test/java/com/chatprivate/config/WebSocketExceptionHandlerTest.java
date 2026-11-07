package com.chatprivate.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Aquí compruebo los mensajes de error que devuelve el chat por WebSocket. */
class WebSocketExceptionHandlerTest {

    private final WebSocketExceptionHandler handler = new WebSocketExceptionHandler(null);

    // Si hay usuario identificado, el error de permisos conserva su mensaje.
    @Test
    void handleAccessDenied_ShouldReturnErrorForKnownUser() {
        Map<String, Object> response = handler.handleAccessDenied(
                new AccessDeniedException("No tienes permiso"), principal("diego"));

        assertEquals("ACCESS_DENIED", response.get("type"));
        assertEquals("No tienes permiso", response.get("message"));
        assertTrue(response.get("timestamp") instanceof Long);
    }

    // Si no se conoce el usuario, el manejador debe usar el valor por defecto.
    @Test
    void handleAccessDenied_ShouldUseUnknownWhenPrincipalIsMissing() {
        Map<String, Object> response = handler.handleAccessDenied(
                new AccessDeniedException("Acceso denegado"), null);

        assertEquals("ACCESS_DENIED", response.get("type"));
        assertEquals("Acceso denegado", response.get("message"));
    }

    // Los datos inválidos se identifican como error de argumento.
    @Test
    void handleIllegalArgument_ShouldReturnInvalidArgumentForKnownUser() {
        Map<String, Object> response = handler.handleIllegalArgument(
                new IllegalArgumentException("ID inválido"), principal("diego"));

        assertEquals("INVALID_ARGUMENT", response.get("type"));
        assertEquals("ID inválido", response.get("message"));
        assertTrue(response.get("timestamp") instanceof Long);
    }

    // También debe responder correctamente cuando no llega el usuario.
    @Test
    void handleIllegalArgument_ShouldUseUnknownWhenPrincipalIsMissing() {
        Map<String, Object> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Dato inválido"), null);

        assertEquals("INVALID_ARGUMENT", response.get("type"));
        assertEquals("Dato inválido", response.get("message"));
    }

    // Los errores inesperados no deben mostrar detalles internos al cliente.
    @Test
    void handleGenericException_ShouldReturnSafeMessageForKnownUser() {
        Map<String, Object> response = handler.handleGenericException(
                new RuntimeException("detalle interno"), principal("diego"));

        assertEquals("INTERNAL_ERROR", response.get("type"));
        assertEquals("Ocurrió un error al procesar tu mensaje. Por favor, inténtalo de nuevo.",
                response.get("message"));
        assertFalse(response.get("message").toString().contains("detalle interno"));
        assertTrue(response.get("timestamp") instanceof Long);
    }

    // El error inesperado también debe funcionar si no hay usuario identificado.
    @Test
    void handleGenericException_ShouldUseUnknownWhenPrincipalIsMissing() {
        Map<String, Object> response = handler.handleGenericException(
                new RuntimeException("detalle interno"), null);

        assertEquals("INTERNAL_ERROR", response.get("type"));
        assertFalse(response.get("message").toString().contains("detalle interno"));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
