package fr.efrei.chatapi.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import fr.efrei.chatapi.model.Message;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Conversation entre deux utilisateurs, dans les deux sens, du plus ancien au plus récent.
     * Requête JPQL : elle porte sur l'entité Message et non sur la table, ce qui la rend
     * indépendante du SGBD (H2 ou PostgreSQL).
     */
    @Query("""
            SELECT m
            FROM Message m
            WHERE (m.expediteurId = :currentUserId AND m.destinataireId = :utilisateurId)
               OR (m.expediteurId = :utilisateurId AND m.destinataireId = :currentUserId)
            ORDER BY m.dateEnvoi ASC
            """)
    List<Message> findMessages(@Param("currentUserId") Long currentUserId,
                               @Param("utilisateurId") Long utilisateurId);
}
