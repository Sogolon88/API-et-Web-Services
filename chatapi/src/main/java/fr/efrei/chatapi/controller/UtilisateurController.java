package fr.efrei.chatapi.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.efrei.chatapi.dto.UtilisateurResponse;
import fr.efrei.chatapi.service.UtilisateurService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// Annuaire des utilisateurs, exposé sous forme de DTO : l'entité et son hachage ne sortent jamais du serveur.
@Tag(name = "Utilisateurs")
@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @Operation(summary = "Lister les utilisateurs et leur statut de connexion")
    @ApiResponse(responseCode = "200", description = "Liste des utilisateurs")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide", content = @Content)
    @GetMapping
    public List<UtilisateurResponse> lister() {
        return utilisateurService.lister();
    }
}
