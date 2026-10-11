package fr.efrei.chatapi.dto;

import fr.efrei.chatapi.model.StatutMessage;

// Trame "accuse" envoyée à l'expéditeur quand l'un de ses messages change de statut.
public record AccuseSortant(String type, Long messageId, StatutMessage statut) {

    public AccuseSortant(Long messageId, StatutMessage statut) {
        this("accuse", messageId, statut);
    }
}
