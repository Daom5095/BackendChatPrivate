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
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final UserService userService; // ¡AÑADIR!

    public MessagingRestController(UserPublicKeyRepository userPublicKeyRepository,
                                   MessageService messageService,
                                   UserRepository userRepository,
                                   UserService userService) {
        this.userPublicKeyRepository = userPublicKeyRepository;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.userService = userService;
    }


    @PostMapping("/public-key")
    public ResponseEntity<?> uploadPublicKey(Authentication authentication,
                                             @RequestBody String publicKeyPem) {
        userService.uploadPublicKey(authentication.getName(), publicKeyPem);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/public-key/{userId}")
    public ResponseEntity<?> getPublicKey(@PathVariable Long userId) {
        return userPublicKeyRepository.findById(userId)
                .map(u -> ResponseEntity.ok(u.getPublicKeyPem()))
                .orElse(ResponseEntity.notFound().build());
    }

}