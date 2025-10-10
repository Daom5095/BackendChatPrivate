package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.AddParticipantRequest;
import com.chatprivate.messaging.dto.ConversationResponse;
import com.chatprivate.messaging.dto.CreateConversationRequest;
import com.chatprivate.messaging.dto.ParticipantDto;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import com.chatprivate.messaging.service.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;

    public ConversationController(ConversationService conversationService,
                                  UserRepository userRepository) {
        this.conversationService = conversationService;
        this.userRepository = userRepository;
    }

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
        Long requesterId = Long.valueOf(authentication.getName());
        conversationService.addParticipant(conversationId, requesterId, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<?> removeParticipant(Authentication authentication,
                                               @PathVariable("id") Long conversationId,
                                               @PathVariable("userId") Long userId) {
        Long requesterId = Long.valueOf(authentication.getName());
        conversationService.removeParticipant(conversationId, requesterId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/participants")
    public ResponseEntity<List<ParticipantDto>> getParticipants(@PathVariable("id") Long conversationId) {
        return ResponseEntity.ok(conversationService.getParticipants(conversationId));
    }
}
