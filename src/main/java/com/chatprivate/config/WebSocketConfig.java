package com.chatprivate.config;


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


/**
 * Configuración de mis WebSockets usando STOMP.
 * Habilito el broker de mensajería para la comunicación en tiempo real.
 */
@Configuration
@EnableWebSocketMessageBroker // Activa el servidor WebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Mi interceptor personalizado para autenticar conexiones WebSocket
    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;

    @Autowired
    public WebSocketConfig(WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor) {
        this.webSocketAuthChannelInterceptor = webSocketAuthChannelInterceptor;
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
        // Habilito un broker simple en memoria para los destinos que empiezan con:
        // "/topic": Para chats grupales/broadcasts (todos los suscritos reciben)
        // "/queue": Para mensajes privados (uno a uno)
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(ts);

        // 2. Prefijo de Aplicación:
        // Los mensajes enviados por el cliente a destinos que empiecen con "/app"
        // serán enrutados a métodos @MessageMapping en mis controladores (ej. StompChatController).
        // Ejemplo: Cliente envía a "/app/chat.send" -> @MessageMapping("/chat.send")
        registry.setApplicationDestinationPrefixes("/app");

        // 3. Prefijo de Usuario:
        // Define el prefijo para destinos específicos de usuario.
        // Esto me permite enviar mensajes a un usuario concreto usando
        // simpMessagingTemplate.convertAndSendToUser(username, "/queue/messages", payload);
        // El cliente se suscribe a: "/user/queue/messages"
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Registro los "endpoints" STOMP.
     * El endpoint es la URL HTTP a la que el cliente se conecta inicialmente
     * para establecer la conexión WebSocket.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registro el endpoint "/ws".
        // Los clientes (móvil, web) se conectarán a "ws://mi-servidor:8080/ws"
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*"); // Permito conexiones de cualquier origen (CORS)
        // Nota: En producción, sería mejor restringir esto a los dominios de mi frontend.
    }

    /**
     * Configuro el canal de entrada (mensajes del cliente al servidor).
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // ¡Clave! Añado mi interceptor de autenticación.
        // Cada mensaje que entra (especialmente el CONNECT) pasará por
        // WebSocketAuthChannelInterceptor antes de ser procesado.
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    /**
     * Opcional: Ajusto límites de transporte.
     * Aumento el tamaño máximo del mensaje si es necesario.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8192); // Límite de 8KB por mensaje
        registration.setSendTimeLimit(15 * 1000).setSendBufferSizeLimit(512 * 1024);
    }

    /**
     * Bean estándar para poder inyectar SimpMessagingTemplate en mis servicios
     * y enviar mensajes programáticamente.
     */
    @Bean
    public SimpMessagingTemplate simpMessagingTemplate(MessageChannel clientOutboundChannel) {
        return new SimpMessagingTemplate(clientOutboundChannel);
    }
}