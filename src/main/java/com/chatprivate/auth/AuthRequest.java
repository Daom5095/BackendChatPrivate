package com.chatprivate.auth;


import lombok.Data;

/** Esta clase reúne el nombre y la contraseña que se usan para autenticar a un usuario. */
@Data
public class AuthRequest {
    private String username;
    private String password;
}