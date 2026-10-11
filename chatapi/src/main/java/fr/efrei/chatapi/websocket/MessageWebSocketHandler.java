package fr.efrei.chatapi.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import fr.efrei.chatapi.dto.ErreurSortante;
import fr.efrei.chatapi.dto.MessageDto;
import fr.efrei.chatapi.dto.MessageEntrant;
import fr.efrei.chatapi.dto.MessageSortant;
import fr.efrei.chatapi.security.UtilisateurConnecte;
import fr.efrei.chatapi.service.MessageService;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Point d'entrée des trames WebSocket. Un message reçu est d'abord enregistré en base,
 * puis relayé au destinataire s'il est connecté; sinon il attend en base et sera lu par l'historique REST.
 */
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final MessageService messageService;
    private final JsonMapper jsonMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.ajouter(utilisateur(session).id(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.retirer(utilisateur(session).id(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage trame) {
        UtilisateurConnecte moi = utilisateur(session);
        MessageEntrant entrant;
        try {
            entrant = jsonMapper.readValue(trame.getPayload(), MessageEntrant.class);
        } catch (JacksonException e) {
            repondreErreur(moi, session, "FORMAT_INVALIDE", "La trame doit être un objet JSON valide");
            return;
        }
        if (!"message".equals(entrant.type())) {
            repondreErreur(moi, session, "TYPE_INCONNU", "Type de trame non pris en charge : " + entrant.type());
            return;
        }

        MessageDto message;
        try {
            // L'expéditeur est celui du jeton vérifié au handshake, jamais celui du JSON reçu.
            message = messageService.enregistrer(moi.id(), entrant.destinataireId(), entrant.contenu());
        } catch (IllegalArgumentException e) {
            repondreErreur(moi, session, "MESSAGE_INVALIDE", e.getMessage());
            return;
        }

        String json = jsonMapper.writeValueAsString(MessageSortant.depuis(message));
        if (sessionRegistry.envoyer(message.destinataireId(), json)) {
            messageService.marquerLivre(message.id());
        }
    }

    private void repondreErreur(UtilisateurConnecte moi, WebSocketSession session, String code, String detail) {
        sessionRegistry.repondre(moi.id(), session, jsonMapper.writeValueAsString(new ErreurSortante(code, detail)));
    }

    // Toujours présent : une session n'est ouverte qu'après la vérification du jeton par JwtHandshakeInterceptor.
    private static UtilisateurConnecte utilisateur(WebSocketSession session) {
        return (UtilisateurConnecte) session.getAttributes().get(JwtHandshakeInterceptor.ATTRIBUT_UTILISATEUR);
    }
}
