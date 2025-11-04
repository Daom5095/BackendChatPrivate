package com.chatprivate.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Mi entidad JPA para la tabla 'users'.
 * Representa a un usuario en la base de datos.
 */
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
    // Estos campos los genera el cliente y yo solo los almaceno.
    // Son necesarios para que el cliente pueda descifrar su clave
    // privada en un nuevo dispositivo usando solo su contraseña.

    @Column(length = 40)
    private String kekSalt; // Salt (Base64) usado para derivar la KEK (Key Encryption Key)

    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey; // Clave privada RSA cifrada con KEK (Base64)

    @Column(length = 25)
    private String kekIv; // IV (Base64) usado con KEK para cifrar la clave privada
    // -------------------------------------------------------

    @Column(nullable = false, updatable = false) // Aseguro que createdAt no se actualice
    private Instant createdAt = Instant.now();

    // --- Constructor manual para el Builder ---
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
        // Aseguro que createdAt tenga un valor por defecto si es null
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
    }


    // Uso un Builder manual para tener control total
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String username;
        private String email;
        private String password; // Hash de contraseña
        private String kekSalt;
        private String encryptedPrivateKey;
        private String kekIv;
        private Instant createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder password(String password) { this.password = password; return this; } // Recibe el hash
        public Builder kekSalt(String kekSalt) { this.kekSalt = kekSalt; return this; }
        public Builder encryptedPrivateKey(String encryptedPrivateKey) { this.encryptedPrivateKey = encryptedPrivateKey; return this; }
        public Builder kekIv(String kekIv) { this.kekIv = kekIv; return this; }
        // ------------------------------------

        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

        public User build() {
            // Llama al constructor actualizado
            return new User(id, username, email, password,
                    kekSalt, encryptedPrivateKey, kekIv,
                    createdAt); // createdAt ya tiene default en el constructor
        }
    }
}