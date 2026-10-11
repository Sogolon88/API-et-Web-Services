package fr.efrei.chatapi.dto;

// Trame "erreur" renvoyée à l'émetteur : le canal reste ouvert, seule la trame fautive est rejetée.
public record ErreurSortante(String type, String code, String detail) {

    public ErreurSortante(String code, String detail) {
        this("erreur", code, detail);
    }
}
