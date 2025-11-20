package com.chatprivate.messaging.dto;

import jakarta.validation.constraints.NotNull;
public class AddParticipantRequest {

    @NotNull(message = "El ID del usuario (userId) a añadir es obligatorio")
    private Long userId;

    private String role;

    public AddParticipantRequest() {}


    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}