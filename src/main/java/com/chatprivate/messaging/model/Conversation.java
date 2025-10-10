package com.chatprivate.messaging.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // tipo: "direct" o "group"
    @Column(nullable = false)
    private String type;

    @Column
    private String title;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Lista de participantes (solo guardamos IDs de usuario)
    @ElementCollection
    @CollectionTable(
            name = "conversation_participants",
            joinColumns = @JoinColumn(name = "conversation_id")
    )
    @Column(name = "user_id")
    private Set<Long> participants = new HashSet<>();

    public Conversation() {}

    // Getters y setters...
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Set<Long> getParticipants() {
        return participants;
    }
    public void setParticipants(Set<Long> participants) {
        this.participants = participants;
    }
}
