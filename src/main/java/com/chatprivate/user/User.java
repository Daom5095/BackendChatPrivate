package com.chatprivate.user;

import jakarta.persistence.*;
import lombok.Getter; // Importar
import lombok.NoArgsConstructor; // Importar
import lombok.Setter; // Importar

import java.time.Instant;


@Entity
@Table(name = "users")
@Getter // Añadido
@Setter // Añadido
@NoArgsConstructor // Añadido
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private Instant createdAt = Instant.now();

    // --- Constructor para el Builder ---
    // Dejamos este constructor manual porque lo usa el builder
    public User(Long id, String username, String email, String password, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.createdAt = createdAt;
    }

    // --- Getters y Setters eliminados (manejados por Lombok) ---

    // --- Builder manual (se queda igual) ---
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String username;
        private String email;
        private String password;
        private Instant createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public User build() {
            return new User(id, username, email, password,
                    createdAt != null ? createdAt : Instant.now());
        }
    }
}