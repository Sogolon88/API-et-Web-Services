package fr.efrei.chatapi.dto;

import java.time.LocalDateTime;

import fr.efrei.chatapi.model.StatutMessage;

// Trame "message" poussée au destinataire et renvoyée à l'expéditeur, au format du sujet complété de l'id et du statut.
public record MessageSortant(String type, Long id, Long expediteurId, Long destinataireId, String contenu,
                             LocalDateTime dateEnvoi, StatutMessage statut) {

    public static MessageSortant depuis(MessageDto message) {
        return new MessageSortant("message", message.id(), message.expediteurId(), message.destinataireId(),
                message.contenu(), message.dateEnvoi(), message.statut());
    }
}
