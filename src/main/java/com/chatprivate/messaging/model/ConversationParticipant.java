package com.chatprivate.messaging.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA para la tabla 'conversation_participants'.
 */
@Entity
@Table(
        name = "conversation_participants",
        uniqueConstraints = {
                // Restricción: Un usuario solo puede estar una vez en cada conversación
                @UniqueConstraint(columnNames = {"conversation_id", "user_id"})
        },
        indexes = {
                // Índice compuesto: conversación + usuario
                // Usado en: findByConversation_IdAndUserId, existsByConversation_IdAndUserId
                // Optimiza: Validaciones de permisos (¿es este usuario participante?)
                @Index(
                        name = "idx_conversation_user",
                        columnList = "conversation_id, user_id"
                ),

                // Índice simple: usuario
                // Usado en: findByUserId, findConversationsByUserId
                // Optimiza: Buscar todas las conversaciones de un usuario
                @Index(
                        name = "idx_user",
                        columnList = "user_id"
                ),

                // Índice simple: conversación
                // Usado en: findByConversation_Id
                // Optimiza: Buscar todos los participantes de una conversación
                @Index(
                        name = "idx_conversation",
                        columnList = "conversation_id"
                )
        }
)
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relación: Muchos participantes pertenecen a Una conversación.
     * Esta columna es parte de idx_conversation_user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * ID del usuario participante.
     * Esta columna es parte de idx_conversation_user.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Rol del participante: "owner" o "member".
     */
    @Column(nullable = false)
    private String role;

    /**
     * Fecha en que el usuario se unió a la conversación.
     */
    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    public ConversationParticipant() {}

    // ============================================
    // GETTERS Y SETTERS
    // ============================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}