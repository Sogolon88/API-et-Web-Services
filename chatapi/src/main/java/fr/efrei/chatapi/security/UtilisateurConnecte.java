package fr.efrei.chatapi.security;

/**
 * Identité extraite d'un JWT valide. Elle sert de principal Spring Security :
 * les contrôleurs et le canal WebSocket connaissent l'id sans requête en base.
 */
public record UtilisateurConnecte(Long id, String username) {
}
