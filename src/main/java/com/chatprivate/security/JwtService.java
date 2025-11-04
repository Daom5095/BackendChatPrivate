package com.chatprivate.security;

import com.chatprivate.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Servicio encargado de todas las operaciones de JSON Web Tokens (JWT).
 * - Generar tokens.
 * - Validar tokens.
 * - Extraer información (claims) de los tokens.
 */
@Service
public class JwtService {

    // Inyecto mis propiedades personalizadas desde application.yml

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expirationMs}")
    private long jwtExpirationMs;

    /**
     * Genera la clave de firma (Key) a partir de mi secreto (String).
     * Uso HMAC-SHA para firmar los tokens.
     */
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // --- Generadores de token ---

    /** Sobrecarga para que acepte mi entidad User */
    public String generateToken(User user) {
        return generateToken(user.getUsername());
    }

    /** Sobrecarga para que acepte UserDetails de Spring Security */
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails.getUsername());
    }

    /**
     * Núcleo: generar token desde un username.
     * El username se guarda como el "subject" del token.
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs); // Caduca en 24h

        return Jwts.builder()
                .setSubject(username) // El "dueño" del token
                .setIssuedAt(now) // Cuándo se emitió
                .setExpiration(expiry) // Cuándo expira
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // Firmo el token
                .compact();
    }

    // --- Extractores de información ---

    /**
     * Extrae el username (el "subject") del token.
     */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Parsea el token y extrae todos los "claims" (información).
     * Esto valida la firma automáticamente. Si la firma es inválida, lanza excepción.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // --- Validadores ---

    /** Extrae la fecha de expiración */
    private Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /** Comprueba si el token ha expirado */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validación principal, usada por mi JwtAuthFilter.
     * Un token es válido si:
     * 1. El username extraído coincide con el username del UserDetails.
     * 2. El token no ha expirado.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username != null && username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}