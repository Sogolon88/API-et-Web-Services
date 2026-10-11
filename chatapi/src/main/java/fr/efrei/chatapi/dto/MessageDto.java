package fr.efrei.chatapi.dto;

import java.time.LocalDateTime;

import fr.efrei.chatapi.model.Message;
import fr.efrei.chatapi.model.StatutMessage;

// Noms de champs imposés par le sujet, complétés du statut ; le DTO découple ce format JSON du schéma de l'entité.
public record MessageDto(Long id, Long expediteurId, Long destinataireId, String contenu, LocalDateTime dateEnvoi,
                         StatutMessage statut) {

    public static MessageDto depuis(Message message) {
        return new MessageDto(message.getId(), message.getExpediteurId(), message.getDestinataireId(),
                message.getContenu(), message.getDateEnvoi(), message.getStatut());
    }
}
