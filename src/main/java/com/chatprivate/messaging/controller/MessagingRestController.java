package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.UserRepository;
import com.chatprivate.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messaging")
public class MessagingRestController {

    private final UserPublicKeyRepository userPublicKeyRepository;
    private final UserRepository userRepository; // Eliminable si no se usa más
    private final MessageService messageService;
    private final UserService userService; // ¡AÑADIR!

    public MessagingRestController(UserPublicKeyRepository userPublicKeyRepository,
                                   MessageService messageService,
                                   UserRepository userRepository,
                                   UserService userService) { // ¡AÑADIR!
        this.userPublicKeyRepository = userPublicKeyRepository;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.userService = userService; // ¡AÑADIR!
    }

    // --- ENDPOINT REFACTORIZADO ---
    @PostMapping("/public-key")
    public ResponseEntity<?> uploadPublicKey(Authentication authentication,
                                             @RequestBody String publicKeyPem) {
        // La lógica de buscar al usuario y guardar está ahora en el servicio
        userService.uploadPublicKey(authentication.getName(), publicKeyPem);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/public-key/{userId}")
    public ResponseEntity<?> getPublicKey(@PathVariable Long userId) {
        // Esta lógica de solo lectura es simple y puede quedarse,
        // aunque también podría moverse a un servicio si se vuelve más compleja.
        return userPublicKeyRepository.findById(userId)
                .map(u -> ResponseEntity.ok(u.getPublicKeyPem()))
                .orElse(ResponseEntity.notFound().build());
    }


    /**
     * --- ¡ENDPOINT ELIMINADO! ---
     * Este endpoint es redundante. El envío de mensajes debe hacerse
     * a través del WebSocket (@MessageMapping("/chat.send") en StompChatController)
     * para mantener una única vía de comunicación para el envío de chats.
     */
    /*
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(Authentication authentication,
                                         @Valid @RequestBody SendMessageRequest req) {
        String senderUsername = authentication.getName();
        User user = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Long senderId = user.getId();

        messageService.sendAndStoreMessage(senderId, req.getConversationId(),
                req.getCiphertext(), req.getEncryptedKeys());

        return ResponseEntity.ok().build();
    }
    */
}