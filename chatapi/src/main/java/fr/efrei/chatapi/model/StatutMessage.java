package fr.efrei.chatapi.model;

/**
 * Cycle de vie d'un message : enregistré par le serveur, remis au destinataire connecté, puis lu.
 * Les statuts ne font qu'avancer, dans cet ordre.
 */
public enum StatutMessage {
    ENVOYE,
    LIVRE,
    LU
}
