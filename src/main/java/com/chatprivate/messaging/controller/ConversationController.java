package com.chatprivate.messaging.controller;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.messaging.service.ConversationService;
import com.chatprivate.security.PermissionService;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para operaciones de conversaciones.
 *
 * ACTUALIZADO EN SESIÓN 2:
 * - Integrado PermissionService
 * - Validaciones de seguridad en todos los endpoints
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;
    private final PermissionService permissionService; // <-- NUEVO

    /**
     * Crea una nueva conversación.
     */
    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            Authentication authentication,
            @Valid @RequestBody CreateConversationRequest req) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        Long creatorId = user.getId();

        return ResponseEntity.ok(conversationService.createConversation(req, creatorId));
    }

    /**
     * Obtiene todas las conversaciones del usuario autenticado.
     */
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getUserConversations(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        Long userId = user.getId();

        return ResponseEntity.ok(conversationService.getUserConversations(userId));
    }

    /**
     * Añade un participante a una conversación.
     * Solo el owner puede hacerlo.
     */
    @PostMapping("/{id}/participants")
    public ResponseEntity<?> addParticipant(
            Authentication authentication,
            @PathVariable("id") Long conversationId,
            @Valid @RequestBody AddParticipantRequest req) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        Long requesterId = user.getId();

        conversationService.addParticipant(conversationId, requesterId, req);

        return ResponseEntity.ok().build();
    }

    /**
     * Elimina un participante de una conversación.
     * El owner puede eliminar a cualquiera.
     * Un member solo puede eliminarse a sí mismo.
     */
    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<?> removeParticipant(
            Authentication authentication,
            @PathVariable("id") Long conversationId,
            @PathVariable("userId") Long userId) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        Long requesterId = user.getId();

        conversationService.removeParticipant(conversationId, requesterId, userId);

        return ResponseEntity.ok().build();
    }

    /**
     * Obtiene la lista de participantes de una conversación.
     *
     * SEGURIDAD (ACTUALIZADA):
     * - Solo los participantes pueden ver la lista
     */
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<ParticipantDto>> getParticipants(
            Authentication authentication,
            @PathVariable("id") Long conversationId) {

        // Obtengo el ID del usuario autenticado
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        Long userId = user.getId();

        // 🔒 VALIDACIÓN DE SEGURIDAD
        // Solo los participantes pueden ver quiénes son los otros participantes
        permissionService.validateIsParticipant(userId, conversationId);

        return ResponseEntity.ok(conversationService.getParticipants(conversationId));
    }

    /**
     * Obtiene el historial de mensajes de una conversación.
     * Solo los participantes pueden ver los mensajes.
     */
    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageHistoryDto>> getMessageHistory(
            Authentication authentication,
            @PathVariable("id") Long conversationId) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        Long userId = user.getId();

        List<MessageHistoryDto> history = conversationService.getMessageHistory(conversationId, userId);

        return ResponseEntity.ok(history);
    }

    /**
     * Obtiene el historial de mensajes CON PAGINACIÓN.
     *
     * ¡NUEVO EN SESIÓN 3!
     *
     * USO:
     * GET /api/conversations/5/messages/paged?page=0&size=50
     *
     * PARÁMETROS:
     * - page: Número de página (empieza en 0)
     * - size: Cantidad de mensajes por página (default: 50)
     *
     * VENTAJAS:
     * - No carga miles de mensajes de golpe
     * - Permite "infinite scroll" en el frontend
     * - Mucho más rápido y eficiente
     */
    @GetMapping("/{id}/messages/paged")
    public ResponseEntity<Page<MessageHistoryDto>> getMessageHistoryPaged(
            Authentication authentication,
            @PathVariable("id") Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        Long userId = user.getId();

        // Validación de parámetros
        if (page < 0) page = 0;
        if (size < 1 || size > 200) size = 50; // Máximo 200 mensajes por request

        Page<MessageHistoryDto> history = conversationService.getMessageHistoryPaged(
                conversationId, userId, page, size);

        return ResponseEntity.ok(history);
    }
}