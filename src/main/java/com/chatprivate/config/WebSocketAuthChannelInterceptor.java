package com.chatprivate.config; // O com.chatprivate.security

import com.chatprivate.security.JwtService;
// import com.chatprivate.user.CustomUserDetails; // No se necesita aquí
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // <-- AÑADIR IMPORTACIÓN
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
// import java.security.Principal; // <-- ELIMINAR IMPORTACIÓN

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("Interceptando comando CONNECT de WebSocket...");

            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username = jwtService.extractUsername(token);

                    if (username != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        if (jwtService.isTokenValid(token, userDetails)) {

                            // --- ¡CORRECCIÓN FINAL! ---
                            // Creamos el token de autenticación de Spring Security
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                            // Establecemos ESTE token como el 'user' de la sesión STOMP.
                            // Spring SÍ sabe cómo manejar este objeto.
                            accessor.setUser(authentication); // <-- LÍNEA MODIFICADA

                            log.info("WebSocket CONNECT - Authentication (UsernamePasswordAuthenticationToken) asociado a STOMP para: {}", authentication.getName());
                            // --- FIN CORRECCIÓN ---
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error autenticando token WebSocket: {}", ex.getMessage());
                }
            } else {
                log.warn("WebSocket CONNECT - Cabecera Authorization ausente o inválida.");
            }
        }
        return message;
    }
}