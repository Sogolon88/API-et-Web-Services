package fr.efrei.chatapi.dto;

// Trame "presence" diffusée aux autres utilisateurs quand quelqu'un se connecte ou se déconnecte.
public record PresenceSortante(String type, Long utilisateurId, boolean enLigne) {

    public PresenceSortante(Long utilisateurId, boolean enLigne) {
        this("presence", utilisateurId, enLigne);
    }
}
