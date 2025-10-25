package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.SendMessageRequest;
import com.chatprivate.messaging.model.UserPublicKey;
import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import com.chatprivate.messaging.service.MessageService;
import com.chatprivate.user.UserRepository;
import jakarta.validation.Valid; // ¡Importar!
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.chatprivate.user.User;

@RestController
@RequestMapping("/api/messaging")
public class MessagingRestController {

    // ... (dependencias y constructor sin cambios) ...
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

    // ... (endpoint /public-key sin cambios) ...
    @PostMapping("/public-key")
    public ResponseEntity<?> uploadPublicKey(Authentication authentication,
                                             @RequestBody String publicKeyPem) {
        // ... (lógica sin cambios) ...
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserPublicKey upk = new UserPublicKey();
        upk.setUserId(user.getId());
        upk.setPublicKeyPem(publicKeyPem);
        userPublicKeyRepository.save(upk);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/public-key/{userId}")
    public ResponseEntity<?> getPublicKey(@PathVariable Long userId) {
        // ... (lógica sin cambios) ...
        return userPublicKeyRepository.findById(userId)
                .map(u -> ResponseEntity.ok(u.getPublicKeyPem()))
                .orElse(ResponseEntity.notFound().build());
    }


    /**
     * Añado @Valid aquí, para validar el SendMessageRequest.
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(Authentication authentication,
                                         @Valid @RequestBody SendMessageRequest req) {
        String senderUsername = authentication.getName();
        User user = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long senderId = user.getId();

        // Si req no es válido, esto lanzará MethodArgumentNotValidException
        // y mi GlobalExceptionHandler lo atrapará.
        messageService.sendAndStoreMessage(senderId, req.getConversationId(),
                req.getCiphertext(), req.getEncryptedKeys());

        return ResponseEntity.ok().build();
    }

}