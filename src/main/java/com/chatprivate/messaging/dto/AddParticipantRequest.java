package com.chatprivate.messaging.dto;

public class AddParticipantRequest {
    private Long userId;
    private String role; // "member" por defecto

    public AddParticipantRequest() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
