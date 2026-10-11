package fr.efrei.chatapi.dto;

import java.time.LocalDateTime;

// Trame "message" poussée au destinataire, au format imposé par le sujet, complétée de l'id.
public record MessageSortant(String type, Long id, Long expediteurId, Long destinataireId, String contenu,
                             LocalDateTime dateEnvoi) {

    public static MessageSortant depuis(MessageDto message) {
        return new MessageSortant("message", message.id(), message.expediteurId(), message.destinataireId(),
                message.contenu(), message.dateEnvoi());
    }
}
