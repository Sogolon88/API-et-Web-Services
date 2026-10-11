package fr.efrei.chatapi.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import fr.efrei.chatapi.dto.IdentifiantsRequest;
import fr.efrei.chatapi.dto.UtilisateurResponse;
import fr.efrei.chatapi.model.Utilisateur;
import fr.efrei.chatapi.repository.UtilisateurRepository;
import fr.efrei.chatapi.websocket.SessionRegistry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;

    /**
     * Inscrit un utilisateur. Le doublon est détecté ici pour répondre 409 avec un message clair ;
     * la contrainte d'unicité en base reste la garantie finale.
     */
    public UtilisateurResponse creer(IdentifiantsRequest identifiants) {
        if (utilisateurRepository.existsByUsername(identifiants.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom d'utilisateur déjà pris");
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setUsername(identifiants.username());
        // Le hachage est fait ici, au plus près de l'écriture en base, pour qu'aucun chemin ne l'oublie.
        utilisateur.setPassword(passwordEncoder.encode(identifiants.password()));
        // Un compte qui vient d'être créé n'a encore aucune connexion WebSocket ouverte.
        return UtilisateurResponse.depuis(utilisateurRepository.save(utilisateur), false);
    }

    // État de présence à l'instant de la requête; les changements suivants arrivent par le WebSocket.
    public List<UtilisateurResponse> lister() {
        return utilisateurRepository.findAll().stream()
                .map(u -> UtilisateurResponse.depuis(u, sessionRegistry.estConnecte(u.getId())))
                .toList();
    }

    public Utilisateur trouverParUsername(String username) {
        return utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur inconnu"));
    }
}
