package com.chatprivate.messaging.model;

import jakarta.persistence.*;

/**
 * Entidad JPA para la tabla 'message_keys'.
 *
 * ACTUALIZADO EN SESIÓN 3:
 * - Añadido índice compuesto (message_id, recipient_id)
 * - Optimizado para búsquedas de claves específicas
 */
@Entity
@Table(
        name = "message_keys",
        uniqueConstraints = {
                // Restricción: Un destinatario solo puede tener una clave por mensaje
                @UniqueConstraint(columnNames = {"message_id", "recipient_id"})
        },
        indexes = {
                // Índice compuesto: mensaje + destinatario
                // Usado en: findByMessage_IdAndRecipientId
                // Optimiza: Buscar la clave de un mensaje para un usuario específico
                @Index(
                        name = "idx_message_recipient",
                        columnList = "message_id, recipient_id"
                ),

                // Índice simple: destinatario
                // Usado en: findByRecipientId (queries futuras)
                @Index(
                        name = "idx_recipient",
                        columnList = "recipient_id"
                )
        }
)
public class MessageKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Relación: Muchas claves pertenecen a Un mensaje.
     * Esta columna es parte del índice idx_message_recipient.
     */
    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    /**
     * ID del usuario al que pertenece esta clave.
     * Esta columna es parte del índice idx_message_recipient.
     */
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    /**
     * Clave AES cifrada con la clave pública RSA del destinatario.
     * Almacenada como TEXT (Base64).
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedKey;

    public MessageKey() {}

    // ============================================
    // GETTERS Y SETTERS
    // ============================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public String getEncryptedKey() {
        return encryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        this.encryptedKey = encryptedKey;
    }
}