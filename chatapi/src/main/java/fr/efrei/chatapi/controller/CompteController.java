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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/comptes")
@RequiredArgsConstructor
public class CompteController {

    private final UtilisateurService utilisateurService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UtilisateurResponse creer(@Valid @RequestBody IdentifiantsRequest identifiants) {
        return utilisateurService.creer(identifiants);
    }
}
