package com.chatprivate.config;

import com.chatprivate.security.JwtService;
import com.chatprivate.user.CustomUserDetails;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder; // <-- 1. IMPORTANTE
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;

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
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) { // <-- 2. VERIFICACIÓN AÑADIDA
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        if (userDetails != null && jwtService.isTokenValid(token, userDetails)) {

                            // --- INICIO DE LA CORRECCIÓN ---

                            // 3. Creamos el token de autenticación para el contexto de seguridad
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                            // 4. Lo establecemos en el contexto de seguridad de Spring
                            SecurityContextHolder.getContext().setAuthentication(auth);

                            // 5. Mantenemos la lógica para identificar al usuario por su ID para los mensajes
                            CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
                            Long userId = customUserDetails.getUser().getId();
                            final Principal userPrincipal = () -> userId.toString();
                            accessor.setUser(userPrincipal);

                            // --- FIN DE LA CORRECCIÓN ---
                        }
                    }
                } catch (Exception ex) {
                    // Log y no autenticar
                }
            }
        }
        return message;
    }
}