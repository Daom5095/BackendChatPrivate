package com.chatprivate.user;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
}