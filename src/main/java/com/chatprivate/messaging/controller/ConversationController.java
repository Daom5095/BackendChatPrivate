package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import com.chatprivate.messaging.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor // Lombok se encarga del constructor
@Slf4j // Para logging
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;


    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(Authentication authentication,
                                                                   @RequestBody CreateConversationRequest req) {
        // obtener username del token autenticado
        String username = authentication.getName();
        // buscar usuario por username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long creatorId = user.getId();

        return ResponseEntity.ok(conversationService.createConversation(req, creatorId));
    }

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getUserConversations(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();

        return ResponseEntity.ok(conversationService.getUserConversations(userId));
    }

    @PostMapping("/{id}/participants")
    public ResponseEntity<?> addParticipant(Authentication authentication,
                                            @PathVariable("id") Long conversationId,
                                            @RequestBody AddParticipantRequest req) {

        // --- INICIO DE CORRECCIÓN DEL BUG ---
        // 'authentication.getName()' devuelve el username (String), no el ID (Long)
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long requesterId = user.getId();
        // --- FIN DE CORRECCIÓN DEL BUG ---

        conversationService.addParticipant(conversationId, requesterId, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<?> removeParticipant(Authentication authentication,
                                               @PathVariable("id") Long conversationId,
                                               @PathVariable("userId") Long userId) {

        // --- INICIO DE CORRECCIÓN DEL BUG ---
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long requesterId = user.getId();
        // --- FIN DE CORRECCIÓN DEL BUG ---

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

        // Obtenemos el ID del usuario que hace la petición
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();

        // (Aquí podrías añadir una verificación para asegurarte
        // que el 'userId' es participante de la 'conversationId' antes de devolver el historial)

        List<MessageHistoryDto> history = conversationService.getMessageHistory(conversationId, userId);
        return ResponseEntity.ok(history);
    }
}