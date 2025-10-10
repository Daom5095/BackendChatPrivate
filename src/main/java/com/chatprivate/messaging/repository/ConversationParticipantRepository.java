package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    List<ConversationParticipant> findByConversation_Id(Long conversationId);

    Optional<ConversationParticipant> findByConversation_IdAndUserId(Long conversationId, Long userId);

    boolean existsByConversation_IdAndUserId(Long conversationId, Long userId);

    List<ConversationParticipant> findByUserId(Long userId);
}
