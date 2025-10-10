package com.chatprivate.messaging.dto;

import java.time.Instant;

public class ParticipantDto {
    private Long userId;
    private String role;
    private Instant joinedAt;

    public ParticipantDto() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
