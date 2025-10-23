
package com.chatprivate.messaging.service;

import com.chatprivate.messaging.dto.*;
import com.chatprivate.messaging.model.*;
import com.chatprivate.messaging.repository.*;
import com.chatprivate.user.User;
import com.chatprivate.user.UserRepository;
import lombok.RequiredArgsConstructor; // Asegúrate de importar
import lombok.extern.slf4j.Slf4j; // Importar para logging
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Function; // Para el Map de usuarios

@Service
@RequiredArgsConstructor // Lombok se encarga del constructor
@Slf4j // Para logging
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository conversationParticipantRepository;
    private final UserRepository userRepository;

    private final MessageRepository messageRepository;
    private final MessageKeyRepository messageKeyRepository;


    // --- El constructor manual se elimina (Lombok lo maneja) ---


    // --- NUEVO MÉTODO PARA HISTORIAL DE MENSAJES ---
    @Transactional(readOnly = true)
    public List<MessageHistoryDto> getMessageHistory(Long conversationId, Long userId) {
        log.info("Cargando historial para conversación {} para usuario {}", conversationId, userId);

        // 1. Obtener todos los mensajes de la conversación
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Obtener los IDs de los mensajes
        List<Long> messageIds = messages.stream().map(Message::getId).collect(Collectors.toList());

        // 3. Obtener las MessageKeys *solo* para el usuario que hace la petición
        List<MessageKey> userKeys = messageKeyRepository.findByMessage_IdInAndRecipientId(messageIds, userId);

        // 4. Crear un mapa (MessageID -> EncryptedKey) para búsqueda rápida
        Map<Long, String> keyMap = userKeys.stream()
                .collect(Collectors.toMap(mk -> mk.getMessage().getId(), MessageKey::getEncryptedKey));

        // 5. Mapear a DTOs
        return messages.stream()
                .map(msg -> {
                    String encryptedKey = keyMap.get(msg.getId());
                    // Si no hay clave, significa que el mensaje no era para este usuario
                    // (o fue enviado antes de que se uniera). Lo filtramos.
                    if (encryptedKey == null) {
                        log.warn("Usuario {} no tiene clave para el mensaje {}", userId, msg.getId());
                        return null;
                    }
                    return new MessageHistoryDto(
                            msg.getId(),
                            msg.getSenderId(),
                            msg.getCiphertext(),
                            encryptedKey,
                            msg.getCreatedAt()
                    );
                })
                .filter(dto -> dto != null) // Filtra los nulos
                .collect(Collectors.toList());
    }

    // --- (Aquí va el resto de tus métodos: toParticipantDto, getParticipants, getUserConversations, etc. No cambian) ---
    // ...
    // ... (toParticipantDto, getParticipants, getUserConversations, createConversation, ...)
    // ... (getConversationResponseById, toResponse, isOwner, addParticipant, removeParticipant)
    // ...
    // --- MÉTODO toParticipantDto MODIFICADO ---
    // Ahora necesita acceso a los datos del usuario para obtener el username
    private ParticipantDto toParticipantDto(ConversationParticipant p, Map<Long, User> userMap) {
        ParticipantDto dto = new ParticipantDto();
        dto.setUserId(p.getUserId());
        dto.setRole(p.getRole());
        dto.setJoinedAt(p.getJoinedAt());
        // Añade el username buscando en el mapa
        User user = userMap.get(p.getUserId());
        if (user != null) {
            dto.setUsername(user.getUsername());
        } else {
            dto.setUsername("Usuario Desconocido"); // Fallback
        }
        return dto;
    }

    // --- MÉTODO getParticipants MODIFICADO ---
    @Transactional(readOnly = true)
    public List<ParticipantDto> getParticipants(Long conversationId) {
        List<ConversationParticipant> participants = conversationParticipantRepository.findByConversation_Id(conversationId);
        if (participants.isEmpty()) {
            return Collections.emptyList();
        }
        // Obtenemos los IDs de los usuarios participantes
        List<Long> userIds = participants.stream().map(ConversationParticipant::getUserId).collect(Collectors.toList());
        // Buscamos todos los usuarios de una vez para eficiencia
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // Mapeamos a DTO incluyendo el username
        return participants.stream()
                .map(p -> toParticipantDto(p, userMap))
                .collect(Collectors.toList());
    }


    // --- MÉTODO getUserConversations MODIFICADO ---
    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversations(Long userId) {
        List<Conversation> conversations = conversationParticipantRepository.findConversationsByUserId(userId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> conversationIds = conversations.stream().map(Conversation::getId).collect(Collectors.toList());

        // Obtenemos todos los participantes de todas esas conversaciones
        List<ConversationParticipant> allParticipants = conversationParticipantRepository.findByConversation_IdIn(conversationIds);
        if (allParticipants.isEmpty()) {
            // Si no hay participantes, mapeamos las conversaciones sin ellos (raro, pero posible)
            return conversations.stream()
                    .map(conv -> toResponse(conv, Collections.emptyList()))
                    .collect(Collectors.toList());
        }

        // --- Obtener datos de usuario para TODOS los participantes ---
        List<Long> allUserIds = allParticipants.stream()
                .map(ConversationParticipant::getUserId)
                .distinct() // Evita buscar el mismo usuario varias veces
                .collect(Collectors.toList());
        Map<Long, User> userMap = userRepository.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // --- Fin obtener datos de usuario ---


        // Agrupamos los DTOs de participante (ahora con username) por ID de conversación
        Map<Long, List<ParticipantDto>> participantsByConvId = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getConversation().getId(),
                        // Pasamos el userMap a toParticipantDto
                        Collectors.mapping(p -> toParticipantDto(p, userMap), Collectors.toList())
                ));

        // Mapeamos las conversaciones a ConversationResponse, incluyendo la lista de participantes con usernames
        return conversations.stream()
                .map(conv -> toResponse(conv, participantsByConvId.getOrDefault(conv.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    // --- MÉTODO createConversation SIN CAMBIOS MAYORES ---
    // (Solo asegura que llama a getConversationResponseById al final)
    @Transactional
    public ConversationResponse createConversation(CreateConversationRequest req, Long creatorId) {
        Conversation conv = new Conversation();
        conv.setType(req.getType() == null ? "direct" : req.getType()); // Default a 'direct'
        // Intenta poner un título por defecto si es chat directo y no hay título
        if ("direct".equalsIgnoreCase(conv.getType()) && (req.getTitle() == null || req.getTitle().isEmpty())) {
            if (req.getParticipantIds() != null && req.getParticipantIds().size() == 1) {
                // Podríamos intentar buscar el nombre del otro usuario aquí,
                // pero es más simple dejarlo null y que el frontend lo resuelva
                conv.setTitle(null); // O un título genérico
            }
        } else {
            conv.setTitle(req.getTitle());
        }


        final Conversation savedConv = conversationRepository.save(conv);

        // Añadir al creador como owner
        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(savedConv);
        owner.setUserId(creatorId);
        owner.setRole("owner");
        conversationParticipantRepository.save(owner);

        // Añadir otros participantes si existen
        if (req.getParticipantIds() != null && !req.getParticipantIds().isEmpty()) {
            List<Long> participantIdsToAdd = req.getParticipantIds().stream()
                    .filter(id -> id != null && !id.equals(creatorId)) // No añadir al creador de nuevo
                    .distinct() // Evitar duplicados si se envía el mismo ID varias veces
                    .collect(Collectors.toList());

            // Verifica que los usuarios existan antes de añadirlos
            List<Long> existingUserIds = userRepository.findAllById(participantIdsToAdd)
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());

            List<ConversationParticipant> newParticipants = existingUserIds.stream().map(userId -> {
                ConversationParticipant p = new ConversationParticipant();
                p.setConversation(savedConv);
                p.setUserId(userId);
                p.setRole("member"); // Rol por defecto para nuevos participantes
                return p;
            }).collect(Collectors.toList());

            if (!newParticipants.isEmpty()) {
                conversationParticipantRepository.saveAll(newParticipants);
            }
        }

        // Devuelve la respuesta completa usando el método que ya obtiene los nombres
        return getConversationResponseById(savedConv.getId());
    }


    // --- MÉTODO getConversationResponseById MODIFICADO ---
    // Llama a la versión actualizada de getParticipants
    private ConversationResponse getConversationResponseById(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada con ID: " + conversationId));
        // getParticipants ahora devuelve DTOs con usernames
        List<ParticipantDto> participants = getParticipants(conversationId);
        return toResponse(conv, participants);
    }

    // --- toResponse y isOwner SIN CAMBIOS ---
    private ConversationResponse toResponse(Conversation conv, List<ParticipantDto> participants) {
        ConversationResponse r = new ConversationResponse();
        r.setId(conv.getId());
        r.setType(conv.getType());
        r.setTitle(conv.getTitle());
        r.setCreatedAt(conv.getCreatedAt());
        r.setParticipants(participants); // Ya vienen con usernames
        return r;
    }

    private boolean isOwner(Long conversationId, Long userId) {
        return conversationParticipantRepository.findByConversation_IdAndUserId(conversationId, userId)
                .map(p -> "owner".equalsIgnoreCase(p.getRole()))
                .orElse(false);
    }

    // --- Métodos addParticipant y removeParticipant SIN CAMBIOS necesarios aquí ---
    @Transactional
    public void addParticipant(Long conversationId, Long requesterId, AddParticipantRequest req) {

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));

        // Solo el owner puede añadir (o podrías cambiar la lógica si quieres)
        if (!isOwner(conversationId, requesterId)) {
            throw new AccessDeniedException("Solo el owner puede añadir participantes");
        }

        Long userId = req.getUserId();
        // Verifica que el usuario a añadir exista
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Usuario a añadir no existe");
        }

        // Verifica que el usuario no esté ya en la conversación
        if (conversationParticipantRepository.existsByConversation_IdAndUserId(conversationId, userId)) {
            // Podrías lanzar un error o simplemente no hacer nada
            log.warn("Usuario {} ya está en la conversación {}", userId, conversationId);
            return;
        }

        ConversationParticipant p = new ConversationParticipant();
        p.setConversation(conv); // Asigna la conversación existente
        p.setUserId(userId);
        p.setRole(req.getRole() == null || req.getRole().isEmpty() ? "member" : req.getRole()); // Rol por defecto 'member'
        conversationParticipantRepository.save(p);
    }

    @Transactional
    public void removeParticipant(Long conversationId, Long requesterId, Long userIdToRemove) {
        // Verifica si el que hace la petición es el owner
        boolean requesterIsOwner = isOwner(conversationId, requesterId);

        // Permite eliminar si eres el owner O si te estás eliminando a ti mismo
        if (!requesterIsOwner && !requesterId.equals(userIdToRemove)) {
            throw new AccessDeniedException("No autorizado para eliminar a este participante");
        }

        ConversationParticipant participant = conversationParticipantRepository
                .findByConversation_IdAndUserId(conversationId, userIdToRemove)
                .orElseThrow(() -> new IllegalArgumentException("El participante no se encuentra en esta conversación"));

        // No permitir eliminar al último owner si es un grupo? (Lógica adicional si es necesaria)

        conversationParticipantRepository.delete(participant);
    }
}