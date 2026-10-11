package fr.efrei.chatapi.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.efrei.chatapi.dto.UtilisateurResponse;
import fr.efrei.chatapi.service.UtilisateurService;
import lombok.RequiredArgsConstructor;

// Annuaire des utilisateurs, exposé sous forme de DTO : l'entité et son hachage ne sortent jamais du serveur.
@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @GetMapping
    public List<UtilisateurResponse> lister() {
        return utilisateurService.lister();
    }
}
