package com.chatprivate.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterRequest {

    // Le digo que el username no puede ser nulo, ni vacío, ni solo espacios
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 3, max = 20, message = "El nombre de usuario debe tener entre 3 y 20 caracteres")
    private String username;

    // Verifico que sea un email válido
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    private String email;

    // Verifico la contraseña
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    // Estos pueden ser nulos si la lógica lo permite,
    // pero si se envían, no deben estar vacíos.
    @NotBlank(message = "La clave pública es obligatoria")
    private String publicKey;

    @NotBlank(message = "kekSalt es obligatorio")
    private String kekSalt;
    @NotBlank(message = "encryptedPrivateKey es obligatoria")
    private String encryptedPrivateKey;
    @NotBlank(message = "kekIv es obligatorio")
    private String kekIv;
}