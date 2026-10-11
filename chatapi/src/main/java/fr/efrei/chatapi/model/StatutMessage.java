package fr.efrei.chatapi.model;

import java.util.Arrays;
import java.util.List;

/**
 * Cycle de vie d'un message : enregistré par le serveur, remis au destinataire connecté, puis lu.
 * Les statuts ne font qu'avancer, dans l'ordre de déclaration.
 */
public enum StatutMessage {
    ENVOYE,
    LIVRE,
    LU;

    // Statuts depuis lesquels on peut passer à celui-ci : ceux déclarés avant lui.
    public List<StatutMessage> precedents() {
        return Arrays.stream(values()).filter(statut -> statut.ordinal() < ordinal()).toList();
    }
}
