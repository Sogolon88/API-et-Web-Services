package fr.efrei.chatapi.dto;

// Trame "saisie" relayée au destinataire : l'expéditeur est en train d'écrire, ou vient de s'arrêter.
public record SaisieSortante(String type, Long expediteurId, boolean enCours) {

    public SaisieSortante(Long expediteurId, boolean enCours) {
        this("saisie", expediteurId, enCours);
    }
}
