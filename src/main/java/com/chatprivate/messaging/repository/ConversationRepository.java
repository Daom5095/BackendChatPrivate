package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.Conversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para operaciones de BD sobre Conversaciones.
 *
 * ACTUALIZADO EN SESIÓN 3 - PARTE B:
 * - Añadido @EntityGraph para eliminar N+1 queries
 * - Queries optimizadas con fetch joins
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Busca una conversación por ID.
     *
     * OPTIMIZACIÓN: Usa @EntityGraph para cargar participantes en la misma query.
     * Sin esto, Hibernate haría:
     *   1 query para Conversation
     *   + N queries para cada participante (N+1 problem)
     *
     * Con @EntityGraph:
     *   1 query con LEFT JOIN
     */
    @EntityGraph(attributePaths = {"participants"})
    Optional<Conversation> findById(Long id);

    /**
     * Busca todas las conversaciones.
     *
     * NOTA: Este método generalmente NO se usa en producción.
     * Es mejor buscar por usuario con findByUserId.
     */
    @EntityGraph(attributePaths = {"participants"})
    List<Conversation> findAll();

    /**
     * Busca conversaciones directas (1-a-1) entre dos usuarios.
     *
     * Esta query es compleja porque necesita:
     * 1. Conversaciones de tipo 'direct'
     * 2. Que tengan EXACTAMENTE 2 participantes
     * 3. Que ambos userIds estén en esos participantes
     *
     * OPTIMIZACIÓN: Usamos subqueries para evitar traer datos innecesarios.
     */
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'direct'
        AND (
            SELECT COUNT(cp) FROM ConversationParticipant cp
            WHERE cp.conversation.id = c.id
        ) = 2
        AND EXISTS (
            SELECT 1 FROM ConversationParticipant cp1
            WHERE cp1.conversation.id = c.id AND cp1.userId = :userId1
        )
        AND EXISTS (
            SELECT 1 FROM ConversationParticipant cp2
            WHERE cp2.conversation.id = c.id AND cp2.userId = :userId2
        )
        """)
    List<Conversation> findDirectConversationBetweenUsers(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );
}