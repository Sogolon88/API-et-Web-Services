package fr.efrei.chatapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.efrei.chatapi.dto.IdentifiantsRequest;
import fr.efrei.chatapi.dto.UtilisateurResponse;
import fr.efrei.chatapi.service.UtilisateurService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Création de compte. POST sur une collection crée une nouvelle ressource : le serveur
 * répond 201 Created, 400 si les données sont invalides, 409 si le nom est déjà pris.
 */
@Tag(name = "Comptes")
@RestController
@RequestMapping("/api/comptes")
@RequiredArgsConstructor
public class CompteController {

    private final UtilisateurService utilisateurService;

    @Operation(summary = "Créer un compte")
    @SecurityRequirements
    @ApiResponse(responseCode = "201", description = "Compte créé")
    @ApiResponse(responseCode = "400", description = "Nom ou mot de passe invalide", content = @Content)
    @ApiResponse(responseCode = "409", description = "Nom d'utilisateur déjà pris", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UtilisateurResponse creer(@Valid @RequestBody IdentifiantsRequest identifiants) {
        return utilisateurService.creer(identifiants);
    }
}
