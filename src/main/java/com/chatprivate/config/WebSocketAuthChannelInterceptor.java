package com.chatprivate.config; // O com.chatprivate.security, asegúrate que sea el correcto

import com.chatprivate.security.JwtService;
import com.chatprivate.user.CustomUserDetails; // Necesario si StompChatController lo usa
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
// Quita import java.security.Principal si no se usa directamente aquí

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService,
                                           UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username = jwtService.extractUsername(token);
                    // Solo si hay username y no hay autenticación previa en el contexto
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        if (jwtService.isTokenValid(token, userDetails)) {
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            // Establecer en el contexto de seguridad (buena práctica)
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            // --- CLAVE: Asociar la Authentication completa a la sesión STOMP ---
                            accessor.setUser(authentication);
                            // Spring usará authentication.getName() (username) para registrar la sesión internamente
                            System.out.println("WebSocket CONNECT - Authentication asociada a STOMP para: " + authentication.getName());
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Error autenticando token WebSocket: " + ex.getMessage());
                }
            } else {
                System.err.println("WebSocket CONNECT - Cabecera Authorization ausente o inválida.");
            }
        }
        return message;
    }
}