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

    @Column(nullable = false)
    private String type; // "direct" o "group"

    @Column
    private String title;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // --- CORRECCIÓN ---
    // Se elimina el @ElementCollection y se reemplaza con una relación @OneToMany
    // Esto le dice a Hibernate que la lista de participantes se gestiona a través
    // de la entidad ConversationParticipant.
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ConversationParticipant> participants = new HashSet<>();

    public Conversation() {}

    // Constructor para facilitar la creación en MessageService
    public Conversation(Long id) {
        this.id = id;
    }

    // Getters y setters...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Set<ConversationParticipant> getParticipants() { return participants; }
    public void setParticipants(Set<ConversationParticipant> participants) { this.participants = participants; }
}