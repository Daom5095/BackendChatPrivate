package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.SendMessageRequest;
import com.chatprivate.messaging.model.UserPublicKey;
import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.chatprivate.user.User;

@RestController
@RequestMapping("/api/messaging")
public class MessagingRestController {

    private final UserPublicKeyRepository userPublicKeyRepository;

    private final UserRepository userRepository;

    private final MessageService messageService;

    public MessagingRestController(UserPublicKeyRepository userPublicKeyRepository,
                                   MessageService messageService,
                                   UserRepository userRepository) {
        this.userPublicKeyRepository = userPublicKeyRepository;
        this.messageService = messageService;
        this.userRepository = userRepository;
    }
    @PostMapping("/public-key")
    public ResponseEntity<?> uploadPublicKey(Authentication authentication,
                                             @RequestBody String publicKeyPem) {
        String username = authentication.getName(); // obtienes el username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserPublicKey upk = new UserPublicKey();
        upk.setUserId(user.getId()); // aquí sí usas id numérico
        upk.setPublicKeyPem(publicKeyPem);
        userPublicKeyRepository.save(upk);
        return ResponseEntity.ok().build();
    }


    @GetMapping("/public-key/{userId}")
    public ResponseEntity<?> getPublicKey(@PathVariable Long userId) {
        return userPublicKeyRepository.findById(userId)
                .map(u -> ResponseEntity.ok(u.getPublicKeyPem()))
                .orElse(ResponseEntity.notFound().build());
    }

    // endpoint para enviar mensaje por REST (útil para Postman)
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(Authentication authentication,
                                         @RequestBody SendMessageRequest req) {
        String senderUsername = authentication.getName();
        User user = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long senderId = user.getId();
        messageService.sendAndStoreMessage(senderId, req.getConversationId(),
                req.getCiphertext(), req.getEncryptedKeys());
        return ResponseEntity.ok().build();
    }

}
