package com.chatprivate.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Logger especializado para eventos de seguridad.
 *
 * PROPÓSITO:
 * Centralizar el logging de eventos críticos de seguridad en un formato
 * estructurado y consistente, facilitando el análisis y auditoría.
 *
 * En producción, estos logs deberían:
 * 1. Ir a un archivo separado (security.log)
 * 2. Ser enviados a un SIEM (Security Information and Event Management)
 *    como Splunk, ELK Stack, o Datadog
 * 3. Generar alertas automáticas en caso de patrones sospechosos
 *
 * FORMATO DE LOG:
 * [SECURITY] [EVENTO] timestamp | userId | acción | resultado | detalles
 */
@Component
@Slf4j
public class SecurityAuditLogger {

    /**
     * Loguea un intento de login (exitoso o fallido).
     *
     * @param username El nombre de usuario
     * @param success Si el login fue exitoso
     * @param ip La IP del cliente
     * @param reason Razón del fallo (si aplica)
     */
    public void logLoginAttempt(String username, boolean success, String ip, String reason) {
        String status = success ? "SUCCESS" : "FAILED";
        String details = reason != null ? reason : "N/A";

        if (success) {
            log.info("[SECURITY] [LOGIN_SUCCESS] {} | user={} | ip={}",
                    Instant.now(), username, ip);
        } else {
            log.warn("[SECURITY] [LOGIN_FAILED] {} | user={} | ip={} | reason={}",
                    Instant.now(), username, ip, details);
        }
    }

    /**
     * Loguea un intento de registro.
     */
    public void logRegistration(String username, String email, boolean success, String ip) {
        if (success) {
            log.info("[SECURITY] [REGISTRATION_SUCCESS] {} | user={} | email={} | ip={}",
                    Instant.now(), username, email, ip);
        } else {
            log.warn("[SECURITY] [REGISTRATION_FAILED] {} | user={} | email={} | ip={}",
                    Instant.now(), username, email, ip);
        }
    }

    /**
     * Loguea una violación de permisos.
     *
     * Esto es CRÍTICO: indica que alguien intentó acceder a algo que no debería.
     */
    public void logAccessDenied(Long userId, String resource, String action) {
        log.warn("[SECURITY] [ACCESS_DENIED] {} | userId={} | resource={} | action={}",
                Instant.now(), userId, resource, action);
    }

    /**
     * Loguea cuando se excede el rate limit.
     *
     * Múltiples eventos de este tipo desde la misma IP pueden indicar un ataque.
     */
    public void logRateLimitExceeded(String identifier, String endpoint, int attempts) {
        log.warn("[SECURITY] [RATE_LIMIT_EXCEEDED] {} | identifier={} | endpoint={} | attempts={}",
                Instant.now(), identifier, endpoint, attempts);
    }

    /**
     * Loguea cambios en conversaciones (añadir/eliminar participantes).
     */
    public void logConversationModification(Long userId, Long conversationId, String action, Long targetUserId) {
        log.info("[SECURITY] [CONVERSATION_MODIFICATION] {} | userId={} | conversationId={} | action={} | targetUserId={}",
                Instant.now(), userId, conversationId, action, targetUserId);
    }

    /**
     * Loguea intentos sospechosos o comportamientos anómalos.
     */
    public void logSuspiciousActivity(String description, String details) {
        log.warn("[SECURITY] [SUSPICIOUS_ACTIVITY] {} | description={} | details={}",
                Instant.now(), description, details);
    }

    /**
     * Loguea errores críticos de seguridad (ej. fallos de cifrado).
     */
    public void logSecurityError(String error, Exception exception) {
        log.error("[SECURITY] [SECURITY_ERROR] {} | error={}",
                Instant.now(), error, exception);
    }
}