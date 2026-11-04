package com.chatprivate.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import java.util.Arrays;
import java.util.List;


/** Aquí configuro el canal WebSocket y las rutas que usa el chat en tiempo real. */
@Configuration
@EnableWebSocketMessageBroker // Activa el servidor WebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Mi interceptor personalizado para autenticar conexiones WebSocket
    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
    private final List<String> allowedOrigins;

    @Autowired
    public WebSocketConfig(
            WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor,
            @Value("${app.cors.allowed-origins}") String configuredOrigins) {
        this.webSocketAuthChannelInterceptor = webSocketAuthChannelInterceptor;
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    /**
     * Configuro el "broker" de mensajes.
     * El broker es el intermediario que distribuye mensajes.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Configuración de Heartbeat para mantener viva la conexión
        ThreadPoolTaskScheduler ts = new ThreadPoolTaskScheduler();
        ts.setPoolSize(1);
        ts.setThreadNamePrefix("wss-heartbeat-thread-");
        ts.initialize();

        // 1. Prefijos del Broker:
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(ts);

        // 2. Prefijo de Aplicación:
        registry.setApplicationDestinationPrefixes("/app");

        // 3. Prefijo de Usuario:
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Registro los "endpoints" STOMP.
     * El endpoint es la URL HTTP a la que el cliente se conecta inicialmente
     * para establecer la conexión WebSocket.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    /**
     * Configuro el canal de entrada (mensajes del cliente al servidor).
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    /**
     *  Ajusto límites de transporte.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8192); // Límite de 8KB por mensaje
        registration.setSendTimeLimit(15 * 1000).setSendBufferSizeLimit(512 * 1024);
    }

}
