package fr.efrei.chatapi.dto;

import java.time.LocalDateTime;

import fr.efrei.chatapi.model.Message;

// Noms de champs imposés par le sujet ; le DTO découple ce format JSON du schéma de l'entité.
public record MessageDto(Long id, Long expediteurId, Long destinataireId, String contenu, LocalDateTime dateEnvoi) {

    public static MessageDto depuis(Message message) {
        return new MessageDto(message.getId(), message.getExpediteurId(), message.getDestinataireId(),
                message.getContenu(), message.getDateEnvoi());
    }
}
