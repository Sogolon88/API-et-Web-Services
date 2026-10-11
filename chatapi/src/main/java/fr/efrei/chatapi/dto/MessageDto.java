package fr.efrei.chatapi.dto;

import java.time.LocalDateTime;

import fr.efrei.chatapi.model.Message;

// Noms de champs imposés par le sujet ; l'entité garde ses noms de colonnes d'origine.
public record MessageDto(Long id, Long expediteurId, Long destinataireId, String contenu, LocalDateTime dateEnvoi) {

    public static MessageDto depuis(Message message) {
        return new MessageDto(message.getId(), message.getSenderId(), message.getReceiverId(),
                message.getContent(), message.getDateSent());
    }
}
