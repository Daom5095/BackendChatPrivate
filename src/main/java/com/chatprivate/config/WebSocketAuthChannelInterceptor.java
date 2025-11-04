package com.chatprivate.config; // O com.chatprivate.security

import com.chatprivate.security.JwtService;
import com.chatprivate.user.CustomUserDetails;
import lombok.RequiredArgsConstructor; // ¡Añadir esta!
import lombok.extern.slf4j.Slf4j; // ¡Añadir esta!
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// Quita la importación de SecurityContextHolder si no se usa en otro lado
// import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Mi "guardia" de seguridad para el canal de WebSocket.
 * Implemento ChannelInterceptor para inspeccionar los mensajes STOMP
 * ANTES de que lleguen al broker o a mis controladores.
 *
 * Su trabajo principal es autenticar al usuario en el momento
 * de la conexión (CONNECT).
 */
@Component
@RequiredArgsConstructor // Uso Lombok para inyectar mis dependencias finales
@Slf4j // Añado el logger de SLF4J
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    // Mis dependencias (final para que Lombok las inyecte)
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService; // Mi CustomUserDetailsService

    /**
     * Este método se ejecuta justo antes de que un mensaje (como un CONNECT) se procese.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Obtengo el "accessor" que me da acceso fácil a las cabeceras STOMP
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Solo me interesa validar en el momento de la conexión (StompCommand.CONNECT)
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("Interceptando comando CONNECT de WebSocket...");

            // Intento sacar el token de la cabecera 'Authorization'
            // El cliente debe enviar el token JWT aquí (ej. en stompClient.connect(...))
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7); // Quito el "Bearer "
                try {
                    String username = jwtService.extractUsername(token);

                    // Si hay username y el token es válido
                    if (username != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        if (jwtService.isTokenValid(token, userDetails)) {
                            // Creo el objeto de autenticación de Spring Security
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                            // --- ¡LA PARTE MÁS IMPORTANTE! ---
                            // Asocio esta autenticación completa a la sesión STOMP.
                            // Esto le dice a Spring Security "este usuario está autenticado
                            // para esta sesión de WebSocket".
                            // Ahora, en mis @MessageMapping, puedo recibir 'Authentication'
                            // o 'Principal' como parámetro.
                            accessor.setUser(authentication);
                            // --- FIN ACCIÓN ---

                            log.info("WebSocket CONNECT - Autenticación asociada a STOMP para: {}", authentication.getName());
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error autenticando token WebSocket: {}", ex.getMessage());
                    // Podría lanzar una excepción aquí para denegar la conexión si soy estricto
                }
            } else {
                log.warn("WebSocket CONNECT - Cabecera Authorization ausente o inválida.");
                // Aquí también podría denegar la conexión
            }
        }

        // Dejo que el mensaje continúe su camino
        return message;
    }
}