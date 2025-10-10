package com.chatprivate.messaging.model;

import jakarta.persistence.*;

@Entity
@Table(name = "message_keys",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"message_id","recipient_id"})})
public class MessageKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

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
