package com.chatprivate.config;

// Quita importaciones no usadas (HandshakeHandler, UserDestinationResolver, etc.)
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
// Quita WebSocketTransportRegistration si no la necesitas más

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    @Autowired
    public WebSocketConfig(WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor) {
        this.webSocketAuthChannelInterceptor = webSocketAuthChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // --- CONFIGURACIÓN ESTÁNDAR ---
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(1);
        ts.setThreadNamePrefix("wss-heartbeat-thread-");
        ts.initialize();

        registry.enableSimpleBroker("/topic", "/queue") // Para broadcasts y colas generales/específicas
                .setHeartbeatValue(new long[]{10000, 10000}) // Heartbeats recomendados
                .setTaskScheduler(ts);

        registry.setApplicationDestinationPrefixes("/app"); // Prefijo para @MessageMapping

        registry.setUserDestinationPrefix("/user"); // Prefijo estándar para mensajes a usuarios
        // --- FIN CONFIGURACIÓN ESTÁNDAR ---
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // --- ENDPOINT ESTÁNDAR ---
        registry.addEndpoint("/ws") // El endpoint al que se conecta el cliente
                .setAllowedOriginPatterns("*"); // Permitir cualquier origen (ajusta en producción)
        // --- FIN ENDPOINT ESTÁNDAR ---
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Aplica nuestro interceptor para autenticar
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    // Opcional: Quita configureWebSocketTransport si no necesitas ajustes específicos
    // @Override
    // public void configureWebSocketTransport(WebSocketTransportRegistration registration) { ... }

    @Bean
    public SimpMessagingTemplate simpMessagingTemplate(MessageChannel clientOutboundChannel) { // Usa MessageChannel (más general)
        return new SimpMessagingTemplate(clientOutboundChannel);
    }
}