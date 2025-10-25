package com.chatprivate.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // Este será el HASH de la contraseña

    // --- Nuevos campos para recuperación de clave privada ---
    @Column(length = 40) // Ajusta longitud si usas Base64 de salts más largos
    private String kekSalt; // Salt usado para derivar la KEK (Base64)

    @Column(columnDefinition = "TEXT") // Clave privada RSA cifrada con KEK (Base64)
    private String encryptedPrivateKey;

    @Column(length = 25) // Ajusta longitud (IV AES-GCM suele ser 12 bytes = 16 chars Base64)
    private String kekIv; // IV usado con KEK para cifrar la clave privada (Base64)
    // -------------------------------------------------------

    @Column(nullable = false, updatable = false) // Asegurar que createdAt no se actualice
    private Instant createdAt = Instant.now();

    // --- Constructor manual para el Builder (Actualizado) ---
    // Incluye los nuevos campos. Pueden ser null inicialmente si no se usan siempre.
    public User(Long id, String username, String email, String password,
                String kekSalt, String encryptedPrivateKey, String kekIv,
                Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password; // Hash de contraseña
        this.kekSalt = kekSalt;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.kekIv = kekIv;
        // Asegurar que createdAt tenga un valor por defecto si es null
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
    }

    // --- Builder manual (Actualizado) ---
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String username;
        private String email;
        private String password; // Hash de contraseña
        private String kekSalt; // Nuevos campos
        private String encryptedPrivateKey;
        private String kekIv;
        private Instant createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder password(String password) { this.password = password; return this; } // Recibe el hash

        // --- Métodos para los nuevos campos ---
        public Builder kekSalt(String kekSalt) { this.kekSalt = kekSalt; return this; }
        public Builder encryptedPrivateKey(String encryptedPrivateKey) { this.encryptedPrivateKey = encryptedPrivateKey; return this; }
        public Builder kekIv(String kekIv) { this.kekIv = kekIv; return this; }
        // ------------------------------------

        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public User build() {
            // Llama al constructor actualizado
            return new User(id, username, email, password,
                    kekSalt, encryptedPrivateKey, kekIv, // Pasar nuevos campos
                    createdAt); // createdAt ya tiene default en el constructor
        }
    } // Fin clase Builder
} // Fin clase User