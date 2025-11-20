package com.chatprivate.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class AuthResponse {

    private String token;
    private String kekSalt;
    private String encryptedPrivateKey;
    private String kekIv;
    // -------------------------------------------------

    public AuthResponse(String token, String kekSalt, String encryptedPrivateKey, String kekIv) {
        this.token = token;
        this.kekSalt = kekSalt;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.kekIv = kekIv;
    }

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
    }
}