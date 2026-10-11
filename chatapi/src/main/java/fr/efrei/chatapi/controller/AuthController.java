package fr.efrei.chatapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import fr.efrei.chatapi.dto.ConnexionResponse;
import fr.efrei.chatapi.dto.IdentifiantsRequest;
import fr.efrei.chatapi.model.Utilisateur;
import fr.efrei.chatapi.security.JwtService;
import fr.efrei.chatapi.service.UtilisateurService;
import lombok.RequiredArgsConstructor;

/**
 * Connexion : échange des identifiants contre un JWT que le client joindra ensuite
 * à chaque requête protégée et à l'ouverture du WebSocket.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UtilisateurService utilisateurService;
    private final JwtService jwtService;

    // Pas de @Valid ici : un identifiant mal formé est simplement un échec d'authentification (401).
    @PostMapping("/login")
    public ConnexionResponse login(@RequestBody IdentifiantsRequest identifiants) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifiants.username(), identifiants.password()));
        } catch (AuthenticationException e) {
            // Même message que l'utilisateur existe ou non, pour ne pas révéler les comptes existants.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        }
        Utilisateur utilisateur = utilisateurService.trouverParUsername(identifiants.username());
        return new ConnexionResponse(jwtService.genererToken(utilisateur), "Bearer",
                utilisateur.getId(), utilisateur.getUsername());
    }
}
