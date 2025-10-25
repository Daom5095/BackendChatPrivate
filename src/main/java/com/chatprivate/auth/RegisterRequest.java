package com.chatprivate.auth;

import lombok.Data;
import lombok.NoArgsConstructor; // Es bueno añadirlo para flexibilidad

@Data // @Data incluye @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor
@NoArgsConstructor // Añadir constructor sin argumentos
public class RegisterRequest {
    private String username;
    private String email;
    private String password; // Contraseña en texto plano enviada por el frontend
    private String publicKey; // Clave pública RSA del usuario (PEM)

    // --- Campos añadidos para recuperación de clave ---
    private String kekSalt; // Salt usado para derivar KEK (Base64)
    private String encryptedPrivateKey; // Clave privada RSA cifrada con KEK (Base64)
    private String kekIv; // IV usado con KEK (Base64)
    // -------------------------------------------------


}