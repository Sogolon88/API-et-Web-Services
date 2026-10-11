package fr.efrei.chatapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Trame reçue d'un client sur le WebSocket. Chaque type n'utilise que ses champs :
 * "message" lit destinataireId et contenu, "lu" lit interlocuteurId, "saisie" lit destinataireId et enCours.
 * Un expediteurId ou une dateEnvoi envoyés par le client sont ignorés, le serveur les fixe lui-même.
 * Tous les champs sont des objets (Boolean et non boolean) : Jackson 3 refuse par défaut
 * une trame à laquelle il manque un champ primitif, ce qui rejetterait les autres types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrameEntrante(String type, Long destinataireId, String contenu, Long interlocuteurId,
                            Boolean enCours) {
}
