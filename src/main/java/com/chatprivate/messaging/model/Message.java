package com.chatprivate.messaging.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA para la tabla 'messages'.
 * Representa un único mensaje cifrado enviado en una conversación.
 */
@Entity
@Table(name = "messages")
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación: Muchos mensajes pertenecen a Una conversación
    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false)
    private Long senderId; // id del usuario que envía

    /**
     * El contenido del mensaje, cifrado con una clave AES simétrica.
     * Almacenado como Base64.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String ciphertext; // mensaje AES cifrado (Base64)

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Message() {}

    // getters/setters...

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