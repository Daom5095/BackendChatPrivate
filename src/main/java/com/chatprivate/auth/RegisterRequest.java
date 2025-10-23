package com.chatprivate.auth;

import lombok.Data;

@Data // @Data se encarga de todos los getters y setters
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String publicKey;
}