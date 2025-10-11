package com.chatprivate.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    // inyecta mediante setter o busca el bean estático (simplificamos aquí):
    private JwtService jwtService;
    private UserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor() {

    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> auth = accessor.getNativeHeader("Authorization");
            if (auth != null && !auth.isEmpty()) {
                String token = auth.get(0).replace("Bearer ", "");
                try {
                    String username = jwtService.extractUsername(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (jwtService.isTokenValid(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(authToken); // importante: Principal para convertAndSendToUser
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } catch (Exception e) {
                    // log y no autenticar
                }
            }
        }
        return message;
    }


    public void setJwtService(JwtService jwtService) { this.jwtService = jwtService; }
    public void setUserDetailsService(UserDetailsService uds) { this.userDetailsService = uds; }
}
