package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.StompMessagePayload;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.security.JwtService;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // Arranca en un puerto aleatorio
@ActiveProfiles("test")
class StompChatControllerTest {

    @LocalServerPort
    private int port; // El puerto aleatorio en el que corre la app

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    // Usamos @SpyBean en lugar de @MockBean
    // @SpyBean nos da un objeto MessageService *real*, pero nos
    // permite "espiar" sus métodos (verificar si fueron llamados).
    @MockitoSpyBean
    private MessageService messageService;

    private WebSocketStompClient stompClient;
    private String jwtToken;
    private User testUser;
    private String wsUrl;

    // Una "cola" para recibir mensajes de error del WebSocket
    private BlockingQueue<String> errorQueue;

    @BeforeEach
    void setUp() {
        // ARRANGE (Preparar)
        // 1. Creamos el cliente STOMP
        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        this.wsUrl = String.format("ws://localhost:%d/ws", port);
        this.errorQueue = new LinkedBlockingDeque<>(1);

        // 2. Creamos un usuario de prueba en la BD
        testUser = userRepository.save(User.builder()
                .username("wsUser")
                .email("ws@test.com")
                .password("pass")
                .build());

        // 3. Generamos un token JWT válido para este usuario
        jwtToken = jwtService.generateToken(testUser);

        // 4. Le decimos a nuestro "espía" que cuando se llame
        // a 'sendAndStoreMessage', no haga nada (para no
        // complicar el test), solo queremos saber que *fue llamado*.
        doNothing().when(messageService).sendAndStoreMessage(any(), any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        // Limpiamos al usuario creado para no afectar otros tests
        if (testUser != null) {
            userRepository.deleteById(testUser.getId());
        }
    }

    @Test
    void receiveMessage_ShouldCallMessageService_WhenMessageIsSent() throws Exception {
        // ARRANGE
        // 1. Configuramos las cabeceras STOMP con nuestro token JWT
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtToken);

        // 2. Creamos el payload del mensaje
        StompMessagePayload payload = new StompMessagePayload(
                1L, // conversationId
                "ciphertext-de-prueba",
                testUser.getId(),
                Map.of("1", "key-para-1")
        );

        // ACT
        // 3. Nos conectamos al WebSocket
        StompSession stompSession = stompClient.connectAsync(wsUrl, (org.springframework.web.socket.WebSocketHttpHeaders) null, connectHeaders, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS); // Esperamos 3 seg. para conectar

        assertNotNull(stompSession);
        assertTrue(stompSession.isConnected());

        // 4. Suscribimos al usuario a su cola de errores
        // (para probar el manejo de excepciones de WebSocket)
        stompSession.subscribe("/user/queue/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                boolean ignored = errorQueue.offer((String) payload);
            }
        });

        // 5. Enviamos el mensaje al endpoint del controlador
        stompSession.send("/app/chat.send", payload);

        // 6. Damos tiempo al servidor para procesar el mensaje
        Thread.sleep(1000);

        // ASSERT
        // 7. ¡La prueba de oro!
        // Verificamos que el método 'sendAndStoreMessage' en nuestro
        // 'MessageService' fue llamado exactamente 1 vez con los datos correctos.
        verify(messageService, times(1)).sendAndStoreMessage(
                eq(testUser.getId()),
                eq(1L),
                eq("ciphertext-de-prueba"),
                any(Map.class)
        );

        // 8. Verificamos que no hubo errores
        String error = errorQueue.poll(500, TimeUnit.MILLISECONDS);
        assertNull(error, "Se recibió un error inesperado del WebSocket: " + error);

        stompSession.disconnect();
    }
}