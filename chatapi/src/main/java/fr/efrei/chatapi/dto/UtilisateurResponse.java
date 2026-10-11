package fr.efrei.chatapi.dto;

import fr.efrei.chatapi.model.Utilisateur;

// Vue publique d'un utilisateur : le hash du mot de passe n'en fait jamais partie.
public record UtilisateurResponse(Long id, String username, boolean enLigne) {

    public static UtilisateurResponse depuis(Utilisateur utilisateur, boolean enLigne) {
        return new UtilisateurResponse(utilisateur.getId(), utilisateur.getUsername(), enLigne);
    }
}
