package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByParticipantsContains(Long userId);
}
