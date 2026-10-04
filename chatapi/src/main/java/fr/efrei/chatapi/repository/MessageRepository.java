package fr.efrei.chatapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import fr.efrei.chatapi.model.Message;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
        @Query("""
        SELECT m
        FROM Message m
        WHERE (m.senderId = :currentUserId
               AND m.receiverId = :utilisateurId)
           OR (m.senderId = :utilisateurId
               AND m.receiverId = :currentUserId)
        ORDER BY m.dateSent ASC
    """)
        List<Message> findMessages(
            @Param("currentUserId") Long currentUserId,
            @Param("utilisateurId") Long utilisateurId
    );
}