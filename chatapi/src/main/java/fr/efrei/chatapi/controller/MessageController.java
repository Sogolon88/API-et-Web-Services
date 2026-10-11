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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Historique des conversations. Il relève de REST et non du WebSocket : c'est une ressource
 * identifiable par une URL, consultable sans canal ouvert, par exemple au retour d'un utilisateur hors ligne.
 */
@Tag(name = "Messages")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    // L'utilisateur courant vient du jeton : impossible de lire la conversation de quelqu'un d'autre.
    @Operation(summary = "Historique de la conversation avec un utilisateur, du plus ancien au plus récent")
    @ApiResponse(responseCode = "200", description = "Messages échangés dans les deux sens")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide", content = @Content)
    @GetMapping("/{utilisateurId}")
    public List<MessageDto> historique(@PathVariable Long utilisateurId,
                                       @AuthenticationPrincipal UtilisateurConnecte moi) {
        return messageService.historique(moi.id(), utilisateurId);
    }
}
