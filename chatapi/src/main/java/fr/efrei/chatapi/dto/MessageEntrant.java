package fr.efrei.chatapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Trame reçue d'un client sur le WebSocket. Seuls le type, le destinataire et le contenu sont lus :
 * un expediteurId ou une dateEnvoi envoyés par le client sont ignorés, le serveur les fixe lui-même.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEntrant(String type, Long destinataireId, String contenu) {
}
