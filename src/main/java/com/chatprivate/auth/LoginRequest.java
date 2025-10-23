package com.chatprivate.auth;

import lombok.Data;
import lombok.NoArgsConstructor; // Añadido
import lombok.AllArgsConstructor; // Añadido

@Data
@NoArgsConstructor // Genera constructor vacío
@AllArgsConstructor // Genera constructor con todos los campos
public class LoginRequest {
    private String username;
    private String password;
}