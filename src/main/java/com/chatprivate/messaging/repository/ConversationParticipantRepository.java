package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.Conversation;
import com.chatprivate.messaging.model.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    List<ConversationParticipant> findByConversation_Id(Long conversationId);

    Optional<ConversationParticipant> findByConversation_IdAndUserId(Long conversationId, Long userId);

    boolean existsByConversation_IdAndUserId(Long conversationId, Long userId);

    List<ConversationParticipant> findByUserId(Long userId);


    // Busca todas las entidades Conversation en las que un usuario participa.
    @Query("SELECT cp.conversation FROM ConversationParticipant cp WHERE cp.userId = :userId")
    List<Conversation> findConversationsByUserId(@Param("userId") Long userId);


    // Busca todos los participantes para una lista de IDs de conversación.
    List<ConversationParticipant> findByConversation_IdIn(List<Long> conversationIds);

}
