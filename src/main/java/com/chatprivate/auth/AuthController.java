package com.chatprivate.auth;

import com.chatprivate.exception.RateLimitExceededException;
import com.chatprivate.security.RateLimitService;
import com.chatprivate.security.SecurityAuditLogger;
import com.chatprivate.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para autenticación (registro y login).
 *
 * ACTUALIZADO EN SESIÓN 2:
 * - Añadido rate limiting en login y registro
 * - Protección contra fuerza bruta y spam
 * - Logging mejorado de eventos de seguridad
 * - Integrado SecurityAuditLogger
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final RateLimitService rateLimitService;
    private final SecurityAuditLogger auditLogger; // <-- NUEVO

    /**
     * Endpoint de registro de nuevos usuarios.
     *
     * PROTECCIONES:
     * - Validación de campos (@Valid)
     * - Rate limiting: 3 registros por hora por IP
     *
     * @param request DTO con datos de registro
     * @param httpRequest Para obtener la IP del cliente
     * @return Token JWT si el registro es exitoso
     * @throws RateLimitExceededException Si se excede el límite de registros
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        // Obtengo la IP del cliente
        String clientIp = getClientIP(httpRequest);
        log.info("📝 Intento de registro desde IP: {}", clientIp);

        // 🔒 RATE LIMITING
        // Verifico si el cliente ha excedido el límite de registros
        if (!rateLimitService.tryConsumeRegister(clientIp)) {
            long remaining = rateLimitService.getRemainingRegisterAttempts(clientIp);

            log.warn("🚨 REGISTRO BLOQUEADO: IP {} ha excedido el límite (intentos restantes: {})",
                    clientIp, remaining);

            throw new RateLimitExceededException(
                    "Has excedido el límite de registros. " +
                            "Por favor, inténtalo de nuevo más tarde. " +
                            "Intentos restantes: " + remaining
            );
        }

        // Si pasa el rate limit, procedo con el registro normal
        log.debug("✅ Rate limit OK para registro desde IP: {}", clientIp);

        AuthResponse response = userService.register(request);

        log.info("✅ Registro exitoso para usuario: {}", request.getUsername());

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint de login (autenticación).
     *
     * PROTECCIONES:
     * - Validación de campos (@Valid)
     * - Rate limiting: 5 intentos por minuto por IP
     *
     * @param request DTO con username y password
     * @param httpRequest Para obtener la IP del cliente
     * @return Token JWT + datos de clave cifrada si el login es exitoso
     * @throws RateLimitExceededException Si se excede el límite de intentos
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        // Obtengo la IP del cliente
        String clientIp = getClientIP(httpRequest);
        log.info("🔐 Intento de login desde IP: {} para usuario: {}", clientIp, request.getUsername());

        // 🔒 RATE LIMITING
        // Verifico si el cliente ha excedido el límite de intentos de login
        if (!rateLimitService.tryConsumeLogin(clientIp)) {
            long remaining = rateLimitService.getRemainingLoginAttempts(clientIp);

            log.warn("🚨 LOGIN BLOQUEADO: IP {} ha excedido el límite (intentos restantes: {})",
                    clientIp, remaining);

            throw new RateLimitExceededException(
                    "Has excedido el límite de intentos de login. " +
                            "Por favor, espera un momento antes de volver a intentarlo. " +
                            "Intentos restantes: " + remaining
            );
        }

        // Si pasa el rate limit, procedo con el login normal
        log.debug("✅ Rate limit OK para login desde IP: {}", clientIp);

        AuthResponse response = userService.login(request);

        log.info("✅ Login exitoso para usuario: {}", request.getUsername());

        return ResponseEntity.ok(response);
    }

    /**
     * Helper para obtener la IP real del cliente.
     *
     * IMPORTANTE: Si tu aplicación está detrás de un proxy/load balancer
     * (como Nginx, Cloudflare, AWS ALB), la IP real viene en headers especiales.
     *
     * Este método busca la IP en este orden:
     * 1. X-Forwarded-For (estándar de facto)
     * 2. X-Real-IP (usado por Nginx)
     * 3. RemoteAddr (IP directa, si no hay proxy)
     */
    private String getClientIP(HttpServletRequest request) {
        // 1. Intento obtener la IP desde X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For puede contener múltiples IPs: "client, proxy1, proxy2"
            // La primera es la IP real del cliente
            return xForwardedFor.split(",")[0].trim();
        }

        // 2. Intento obtener la IP desde X-Real-IP
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 3. Fallback: uso la IP directa de la conexión
        String remoteAddr = request.getRemoteAddr();

        // Si estoy en localhost, uso un identificador fijo
        // (en desarrollo, todas las peticiones vienen de 127.0.0.1 o ::1)
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return "127.0.0.1"; // Normalizo IPv6 localhost a IPv4
        }

        return remoteAddr;
    }
}