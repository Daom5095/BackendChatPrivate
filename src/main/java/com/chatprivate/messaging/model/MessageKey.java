package com.chatprivate.messaging.model;

import jakarta.persistence.*;

/**
 * Entidad JPA para la tabla 'message_keys'.
 * Esta es una entidad CRUCIAL para mi E2EE.
 *
 * Cuando se envía un Mensaje (cifrado con una clave AES, ej. K_AES),
 * creo una entrada en esta tabla POR CADA DESTINATARIO.
 *
 * Ejemplo:
 * 1. Alice envía "Hola" a Bob y Carol.
 * 2. Alice cifra "Hola" con K_AES -> ciphertext.
 * 3. Alice cifra K_AES con la ClavePública_Bob -> K_Bob
 * 4. Alice cifra K_AES con la ClavePública_Carol -> K_Carol
 * 5. Se guarda 1 'Message' (con 'ciphertext').
 * 6. Se guardan 2 'MessageKey':
 * - (Message_ID, Bob_ID, K_Bob)
 * - (Message_ID, Carol_ID, K_Carol)
 */
@Entity
@Table(name = "message_keys",
        // Restricción: Un destinatario solo puede tener una clave por mensaje
        uniqueConstraints = {@UniqueConstraint(columnNames = {"message_id","recipient_id"})})
public class MessageKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación: Muchas claves pertenecen a Un mensaje
    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId; // El ID del usuario al que pertenece esta clave

    /**
     * La clave AES (usada para cifrar el Message.ciphertext),
     * cifrada con la clave pública (RSA) del 'recipientId'.
     * Almacenada como Base64.
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedKey; // AES key cifrada con RSA del recipient (Base64)

    public MessageKey() {}

    // getters/setters...

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