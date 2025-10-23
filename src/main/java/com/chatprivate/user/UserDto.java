package com.chatprivate.user;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data // Se encarga de getters y setters
@AllArgsConstructor // Genera el constructor (Long id, String username)
public class UserDto {
    private Long id;
    private String username;
}