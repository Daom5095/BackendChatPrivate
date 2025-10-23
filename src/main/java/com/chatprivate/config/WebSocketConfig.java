package com.chatprivate.config;

// Asegúrate de que solo estén estas importaciones (o las necesarias para las clases usadas)
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;


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

        registry.enableSimpleBroker("/topic", "/queue") // Para broadcasts y colas
                .setHeartbeatValue(new long[]{10000, 10000}) // Heartbeats
                .setTaskScheduler(ts);

        registry.setApplicationDestinationPrefixes("/app"); // Prefijo para @MessageMapping

        registry.setUserDestinationPrefix("/user"); // Prefijo estándar para mensajes a usuarios
        // --- FIN CONFIGURACIÓN ESTÁNDAR ---
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // --- ENDPOINT ESTÁNDAR ---
        registry.addEndpoint("/ws") // El endpoint de conexión
                .setAllowedOriginPatterns("*"); // Permitir cualquier origen
        // --- FIN ENDPOINT ESTÁNDAR ---
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Aplica el interceptor para autenticar
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    // Opcional: Configuración de transporte (puedes comentarla si no es necesaria)
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8192);
        registration.setSendTimeLimit(15 * 1000).setSendBufferSizeLimit(512 * 1024);
    }

    // Bean estándar para SimpMessagingTemplate
    @Bean
    public SimpMessagingTemplate simpMessagingTemplate(MessageChannel clientOutboundChannel) {
        return new SimpMessagingTemplate(clientOutboundChannel);
    }
}