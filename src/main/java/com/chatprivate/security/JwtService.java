package com.chatprivate.security;

import com.chatprivate.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
// ¡IMPORTANTE! Ya no se usa SignatureAlgorithm, ahora es Jwts.SIG
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Servicio encargado de todas las operaciones de JSON Web Tokens (JWT).
 *
 * ACTUALIZADO para JJWT 0.12.6:
 * - Cambié parserBuilder() por parser()
 * - Cambié setSigningKey() por verifyWith()
 * - Cambié SignatureAlgorithm por Jwts.SIG
 * - Cambié el tipo de retorno de getSigningKey() de Key a SecretKey
 *
 * Funciones principales:
 * - Generar tokens JWT firmados
 * - Validar tokens (firma + expiración + usuario)
 * - Extraer información (claims) de los tokens
 */
@Service
public class JwtService {

    // Inyecto mis propiedades personalizadas desde application.yml (o .env)
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Genera la clave de firma (SecretKey) a partir de mi secreto (String).
     * Uso HMAC-SHA256 para firmar los tokens.
     *
     * CAMBIO: Ahora retorna SecretKey en lugar de Key (más específico en 0.12.6)
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // ============================================
    // GENERADORES DE TOKEN
    // ============================================

    /**
     * Sobrecarga: Acepta mi entidad User personalizada.
     */
    public String generateToken(User user) {
        return generateToken(user.getUsername());
    }

    /**
     * Sobrecarga: Acepta UserDetails de Spring Security.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails.getUsername());
    }

    /**
     * Método núcleo: Genera un token JWT desde un username.
     *
     * El token contiene:
     * - subject: el username (identificador del usuario)
     * - issuedAt: cuándo se creó el token
     * - expiration: cuándo caduca (por defecto 24 horas después)
     *
     * CAMBIO: Ahora uso Jwts.SIG.HS256 en lugar de SignatureAlgorithm.HS256
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs); // Caduca en 24h (por defecto)

        return Jwts.builder()
                .subject(username) // El "dueño" del token
                .issuedAt(now) // Cuándo se emitió
                .expiration(expiry) // Cuándo expira
                // ACTUALIZADO: signWith() ahora detecta automáticamente el algoritmo
                // basándose en el tipo de SecretKey
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // ============================================
    // EXTRACTORES DE INFORMACIÓN
    // ============================================

    /**
     * Extrae el username (el "subject") del token.
     * Este método es el que más uso: me dice "quién" es el dueño del token.
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Parsea el token y extrae todos los "claims" (información).
     *
     * CRÍTICO: Este método valida automáticamente:
     * 1. La firma del token (que sea auténtica y no manipulada)
     * 2. Que el token no esté expirado
     *
     * Si algo falla, lanza una excepción (JwtException).
     *
     * CAMBIO IMPORTANTE:
     * - Antes: Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
     * - Ahora: Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                // ACTUALIZADO: verifyWith() reemplaza a setSigningKey()
                .verifyWith(getSigningKey())
                .build()
                // ACTUALIZADO: parseSignedClaims() reemplaza a parseClaimsJws()
                .parseSignedClaims(token)
                .getPayload(); // En 0.12.6, getPayload() reemplaza a getBody()
    }

    // ============================================
    // VALIDADORES
    // ============================================

    /**
     * Helper: Extrae la fecha de expiración del token.
     */
    private Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /**
     * Helper: Comprueba si el token ha expirado.
     * Compara la fecha de expiración con la hora actual.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validación principal, usada por mi JwtAuthFilter.
     *
     * Un token es válido si cumple TODAS estas condiciones:
     * 1. El username extraído del token coincide con el del UserDetails
     * 2. El token no ha expirado
     * 3. La firma es válida (esto lo valida extractAllClaims automáticamente)
     *
     * @param token El JWT a validar
     * @param userDetails El usuario cargado de la BD
     * @return true si el token es válido, false si no lo es
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username != null
                && username.equals(userDetails.getUsername())
                && !isTokenExpired(token));
    }
}