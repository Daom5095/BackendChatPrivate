package com.chatprivate.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Usaremos @Getter, @Setter, @NoArgsConstructor en lugar de @Data
// para tener más control y evitar problemas potenciales con constructores generados
@Getter
@Setter
@NoArgsConstructor // Lombok genera constructor vacío
public class AuthResponse {

    private String token;
    // --- Campos añadidos para recuperación de clave ---
    private String kekSalt;
    private String encryptedPrivateKey;
    private String kekIv;
    // -------------------------------------------------

    // Constructor manual para el builder (incluye TODOS los campos)
    public AuthResponse(String token, String kekSalt, String encryptedPrivateKey, String kekIv) {
        this.token = token;
        this.kekSalt = kekSalt;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.kekIv = kekIv;
    }

    // Getters y Setters son generados por Lombok (@Getter, @Setter)
    // Eliminamos los manuales

    // --- Builder manual (Actualizado) ---
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String token;
        private String kekSalt;
        private String encryptedPrivateKey;
        private String kekIv;

        public Builder token(String token) { this.token = token; return this; }
        public Builder kekSalt(String kekSalt) { this.kekSalt = kekSalt; return this; }
        public Builder encryptedPrivateKey(String encryptedPrivateKey) { this.encryptedPrivateKey = encryptedPrivateKey; return this; }
        public Builder kekIv(String kekIv) { this.kekIv = kekIv; return this; }

        public AuthResponse build() {
            // Llama al constructor actualizado
            return new AuthResponse(token, kekSalt, encryptedPrivateKey, kekIv);
        }
    } // Fin clase Builder
} // Fin clase AuthResponse