package com.chatprivate.messaging.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_public_keys")
public class UserPublicKey {
    @Id
    private Long userId;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String publicKeyPem; // clave pública en formato PEM/Base64

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public UserPublicKey() {}
    // getters/setters...

    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }
    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
