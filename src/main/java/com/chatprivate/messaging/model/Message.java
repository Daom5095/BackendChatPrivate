package com.chatprivate.messaging.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA para la tabla 'messages'.
 * Representa un único mensaje cifrado enviado en una conversación.
 *
 * ACTUALIZADO EN SESIÓN 3:
 * - Documentados índices con @Table y @Index
 * - Optimizado para queries frecuentes
 */
@Entity
@Table(
        name = "messages",
        indexes = {
                // Índice compuesto: conversación + fecha
                // Usado en: findByConversationIdOrderByCreatedAtAsc
                // Optimiza: Buscar mensajes de un chat ordenados por fecha
                @Index(
                        name = "idx_conversation_created",
                        columnList = "conversation_id, created_at"
                ),

                // Índice simple: solo conversación
                // Usado en: findTopByConversationIdOrderByCreatedAtDesc
                // Optimiza: Buscar el último mensaje de un chat
                @Index(
                        name = "idx_conversation",
                        columnList = "conversation_id"
                ),

                // Índice simple: remitente
                // Usado en: Queries futuras (buscar mensajes por usuario)
                @Index(
                        name = "idx_sender",
                        columnList = "sender_id"
                )
        }
)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relación: Muchos mensajes pertenecen a Una conversación.
     *
     * IMPORTANTE: Esta columna está indexada (idx_conversation_created)
     * porque es la columna más usada en las búsquedas.
     */
    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /**
     * ID del usuario que envía el mensaje.
     * Indexado para búsquedas por remitente.
     */
    @Column(nullable = false)
    private Long senderId;

    /**
     * Contenido del mensaje, cifrado con AES.
     * Almacenado como TEXT (hasta ~65KB).
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String ciphertext;

    /**
     * Fecha de creación del mensaje.
     *
     * IMPORTANTE: Esta columna es parte del índice compuesto
     * (idx_conversation_created) para ordenar mensajes eficientemente.
     */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Constructor por defecto (requerido por JPA)
    public Message() {}

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

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}