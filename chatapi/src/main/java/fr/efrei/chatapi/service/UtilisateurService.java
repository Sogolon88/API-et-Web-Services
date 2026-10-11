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
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public UtilisateurResponse creer(IdentifiantsRequest identifiants) {
        if (utilisateurRepository.existsByUsername(identifiants.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nom d'utilisateur déjà pris");
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setUsername(identifiants.username());
        // Le hachage est fait ici, au plus près de l'écriture en base, pour qu'aucun chemin ne l'oublie.
        utilisateur.setPassword(passwordEncoder.encode(identifiants.password()));
        return UtilisateurResponse.depuis(utilisateurRepository.save(utilisateur));
    }

    public List<UtilisateurResponse> lister() {
        return utilisateurRepository.findAll().stream()
                .map(UtilisateurResponse::depuis)
                .toList();
    }

    public Utilisateur trouverParUsername(String username) {
        return utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur inconnu"));
    }
}
