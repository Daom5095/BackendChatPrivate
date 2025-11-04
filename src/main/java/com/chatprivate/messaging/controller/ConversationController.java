package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import com.chatprivate.messaging.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;

    /**
     * Añado @Valid aquí.
     * Aunque CreateConversationRequest no tiene validaciones AHORA,
     * si se las añado en el futuro (ej. @Size para el título),
     * ya estará protegido. Es buena práctica.
     */
    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(Authentication authentication,
                                                                   @Valid @RequestBody CreateConversationRequest req) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                // ¡CAMBIO!
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        Long creatorId = user.getId();
        return ResponseEntity.ok(conversationService.createConversation(req, creatorId));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getUserConversations(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        Long userId = user.getId();
        return ResponseEntity.ok(conversationService.getUserConversations(userId));
    }


    /**
     * Añado @Valid aquí, para validar AddParticipantRequest.
     */
    @PostMapping("/{id}/participants")
    public ResponseEntity<?> addParticipant(Authentication authentication,
                                            @PathVariable("id") Long conversationId,
                                            @Valid @RequestBody AddParticipantRequest req) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                // ¡CAMBIO!
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        Long requesterId = user.getId();

        conversationService.addParticipant(conversationId, requesterId, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<?> removeParticipant(Authentication authentication,
                                               @PathVariable("id") Long conversationId,
                                               @PathVariable("userId") Long userId) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        Long requesterId = user.getId();
        conversationService.removeParticipant(conversationId, requesterId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/participants")
    public ResponseEntity<List<ParticipantDto>> getParticipants(@PathVariable("id") Long conversationId) {
        return ResponseEntity.ok(conversationService.getParticipants(conversationId));
    }


    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageHistoryDto>> getMessageHistory(
            Authentication authentication,
            @PathVariable("id") Long conversationId) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                // ¡CAMBIO!
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        Long userId = user.getId();
        List<MessageHistoryDto> history = conversationService.getMessageHistory(conversationId, userId);
        return ResponseEntity.ok(history);
    }
}