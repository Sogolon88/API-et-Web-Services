package fr.efrei.chatapi.dto;

import java.time.LocalDateTime;

/**
 * Corps JSON envoyé au service tiers quand un message arrive pour un destinataire hors ligne.
 * Le contenu du message n'en fait pas partie : une conversation privée ne sort pas du serveur.
 */
public record NotificationWebhook(String evenement, Long messageId, Long destinataireId, Long expediteurId,
                                  String expediteur, LocalDateTime dateEnvoi) {

    public static NotificationWebhook nouveauMessage(MessageDto message, String expediteur) {
        return new NotificationWebhook("nouveau_message", message.id(), message.destinataireId(),
                message.expediteurId(), expediteur, message.dateEnvoi());
    }
}
