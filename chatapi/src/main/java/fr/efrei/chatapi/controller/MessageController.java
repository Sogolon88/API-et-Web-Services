package fr.efrei.chatapi.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.efrei.chatapi.dto.MessageDto;
import fr.efrei.chatapi.security.UtilisateurConnecte;
import fr.efrei.chatapi.service.MessageService;
import lombok.RequiredArgsConstructor;

/**
 * Historique des conversations. Il relève de REST et non du WebSocket : c'est une ressource
 * identifiable par une URL, consultable sans canal ouvert, par exemple au retour d'un utilisateur hors ligne.
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    // L'utilisateur courant vient du jeton : impossible de lire la conversation de quelqu'un d'autre.
    @GetMapping("/{utilisateurId}")
    public List<MessageDto> historique(@PathVariable Long utilisateurId,
                                       @AuthenticationPrincipal UtilisateurConnecte moi) {
        return messageService.historique(moi.id(), utilisateurId);
    }
}
