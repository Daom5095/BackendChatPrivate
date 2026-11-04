package com.chatprivate.user;

import lombok.Data;
import lombok.AllArgsConstructor;

/** Este objeto contiene los datos del usuario que se pueden enviar al cliente. */
@Data
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
}