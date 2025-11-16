package com.chatprivate.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de Rate Limiting (Limitación de Velocidad).
 *
 * Este servicio protege mi API contra:
 * - Ataques de fuerza bruta (intentos masivos de login)
 * - Spam (creación masiva de cuentas)
 * - DoS (Denial of Service - saturación del servidor)
 *
 * FUNCIONAMIENTO:
 * Usa el algoritmo "Token Bucket":
 * - Cada usuario/IP tiene una "cubeta" (bucket) con tokens
 * - Cada petición consume 1 token
 * - Los tokens se recargan automáticamente con el tiempo
 * - Si no hay tokens, la petición es rechazada
 *
 * EJEMPLO:
 * Login: 5 tokens/minuto
 * - El usuario puede hacer 5 intentos de login
 * - Después debe esperar ~12 segundos para cada intento adicional
 * - Esto hace IMPOSIBLE la fuerza bruta (pasarían años)
 */
@Service
@Slf4j
public class RateLimitService {

    // Mapas en memoria para almacenar los buckets por IP/usuario
    // Uso ConcurrentHashMap porque es thread-safe (múltiples usuarios simultáneos)
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();

    /**
     * Resuelve (o crea) un bucket para login basado en la IP.
     *
     * CONFIGURACIÓN:
     * - Capacidad: 5 tokens
     * - Recarga: 5 tokens cada 1 minuto
     * - Estrategia: Intervally (recarga todos los tokens de golpe después del tiempo)
     *
     * Esto significa: 5 intentos de login por minuto, luego espera 1 minuto completo.
     */
    private Bucket resolveLoginBucket(String key) {
        return loginBuckets.computeIfAbsent(key, k -> {
            // Defino el límite: 5 peticiones por minuto
            Bandwidth limit = Bandwidth.classic(
                    5, // Capacidad máxima
                    Refill.intervally(5, Duration.ofMinutes(1)) // Recarga 5 tokens cada minuto
            );

            // Creo y devuelvo el bucket
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    /**
     * Resuelve (o crea) un bucket para registro basado en la IP.
     *
     * CONFIGURACIÓN:
     * - Capacidad: 3 tokens
     * - Recarga: 3 tokens cada 1 hora
     *
     * Esto significa: 3 registros por hora. Muy restrictivo para prevenir spam.
     */
    private Bucket resolveRegisterBucket(String key) {
        return registerBuckets.computeIfAbsent(key, k -> {
            // Defino el límite: 3 peticiones por hora
            Bandwidth limit = Bandwidth.classic(
                    3, // Capacidad máxima
                    Refill.intervally(3, Duration.ofHours(1)) // Recarga 3 tokens cada hora
            );

            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    /**
     * Intenta consumir un token del bucket de login.
     *
     * @param identifier Identificador único (normalmente la IP del cliente)
     * @return true si se pudo consumir (petición permitida), false si no hay tokens (rate limit excedido)
     */
    public boolean tryConsumeLogin(String identifier) {
        Bucket bucket = resolveLoginBucket(identifier);
        boolean consumed = bucket.tryConsume(1);

        if (!consumed) {
            // Rate limit excedido
            log.warn("🚨 RATE LIMIT EXCEDIDO (LOGIN): IP {} ha superado el límite de intentos", identifier);
        } else {
            log.debug("✅ Token de login consumido para IP: {} (tokens restantes: {})",
                    identifier, bucket.getAvailableTokens());
        }

        return consumed;
    }

    /**
     * Intenta consumir un token del bucket de registro.
     *
     * @param identifier Identificador único (normalmente la IP del cliente)
     * @return true si se pudo consumir, false si no hay tokens
     */
    public boolean tryConsumeRegister(String identifier) {
        Bucket bucket = resolveRegisterBucket(identifier);
        boolean consumed = bucket.tryConsume(1);

        if (!consumed) {
            log.warn("🚨 RATE LIMIT EXCEDIDO (REGISTRO): IP {} ha superado el límite de registros", identifier);
        } else {
            log.debug("✅ Token de registro consumido para IP: {} (tokens restantes: {})",
                    identifier, bucket.getAvailableTokens());
        }

        return consumed;
    }

    /**
     * Obtiene los tokens restantes para login de un identificador.
     * Útil para mostrar al usuario cuántos intentos le quedan.
     */
    public long getRemainingLoginAttempts(String identifier) {
        Bucket bucket = resolveLoginBucket(identifier);
        return bucket.getAvailableTokens();
    }

    /**
     * Obtiene los tokens restantes para registro de un identificador.
     */
    public long getRemainingRegisterAttempts(String identifier) {
        Bucket bucket = resolveRegisterBucket(identifier);
        return bucket.getAvailableTokens();
    }
    /**
     * Limpia los buckets antiguos para liberar memoria.
     *
     * NOTA: En una aplicación real en producción, deberías:
     * 1. Usar Redis en lugar de memoria local (para múltiples instancias)
     * 2. Implementar un scheduler que limpie buckets viejos cada X horas
     *
     * Por ahora, lo dejamos simple para desarrollo.
     */
    public void cleanupOldBuckets() {
        // --- INICIO DE LA MODIFICACIÓN ---
        // Para los tests, implementamos una limpieza completa
        loginBuckets.clear();
        registerBuckets.clear();

        log.debug("🧹 Limpieza COMPLETA de buckets (para tests): {} login, {} registro",
                loginBuckets.size(), registerBuckets.size());
        // --- FIN DE LA MODIFICACIÓN ---
    }
}