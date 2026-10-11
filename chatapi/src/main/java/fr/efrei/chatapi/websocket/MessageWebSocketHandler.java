package fr.efrei.chatapi.websocket;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import fr.efrei.chatapi.dto.AccuseSortant;
import fr.efrei.chatapi.dto.ErreurSortante;
import fr.efrei.chatapi.dto.MessageDto;
import fr.efrei.chatapi.dto.MessageSortant;
import fr.efrei.chatapi.dto.PresenceSortante;
import fr.efrei.chatapi.dto.SaisieSortante;
import fr.efrei.chatapi.dto.TrameEntrante;
import fr.efrei.chatapi.model.StatutMessage;
import fr.efrei.chatapi.security.UtilisateurConnecte;
import fr.efrei.chatapi.service.MessageService;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Point d'entrée des trames WebSocket. Un message reçu est d'abord enregistré en base,
 * puis relayé au destinataire s'il est connecté; sinon il attend en base et sera lu par l'historique REST.
 * Chaque changement de statut (LIVRE, LU) est signalé à l'expéditeur par une trame "accuse".
 */
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final MessageService messageService;
    private final JsonMapper jsonMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long id = utilisateur(session).id();
        // Seule la première connexion change la présence: un second appareil ne déclenche rien.
        if (sessionRegistry.ajouter(id, session)) {
            diffuserPresence(id, true);
        }
        // Les messages reçus hors ligne sont maintenant livrables : ils seront affichés via l'historique REST.
        accuser(messageService.livrerEnAttente(id), StatutMessage.LIVRE);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long id = utilisateur(session).id();
        if (sessionRegistry.retirer(id, session)) {
            diffuserPresence(id, false);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage texte) {
        UtilisateurConnecte moi = utilisateur(session);
        TrameEntrante trame;
        try {
            trame = jsonMapper.readValue(texte.getPayload(), TrameEntrante.class);
        } catch (JacksonException e) {
            repondreErreur(moi, session, "FORMAT_INVALIDE", "La trame doit être un objet JSON valide");
            return;
        }
        switch (String.valueOf(trame.type())) {
            case "message" -> traiterMessage(moi, session, trame);
            case "lu" -> traiterLu(moi, session, trame);
            case "saisie" -> traiterSaisie(moi, session, trame);
            default -> repondreErreur(moi, session, "TYPE_INCONNU",
                    "Type de trame non pris en charge : " + trame.type());
        }
    }

    /**
     * Ordre imposé : enregistrement, écho à l'expéditeur, relais, puis accusé LIVRE.
     * L'écho part avant le relais pour que l'expéditeur connaisse l'id du message
     * avant de pouvoir recevoir un accusé qui s'y rapporte.
     */
    private void traiterMessage(UtilisateurConnecte moi, WebSocketSession session, TrameEntrante trame) {
        MessageDto message;
        try {
            // L'expéditeur est celui du jeton vérifié au handshake, jamais celui du JSON reçu.
            message = messageService.enregistrer(moi.id(), trame.destinataireId(), trame.contenu());
        } catch (IllegalArgumentException e) {
            repondreErreur(moi, session, "MESSAGE_INVALIDE", e.getMessage());
            return;
        }

        String json = jsonMapper.writeValueAsString(MessageSortant.depuis(message));
        // L'écho apporte l'id et la date du serveur, et synchronise les autres appareils de l'expéditeur.
        sessionRegistry.envoyer(moi.id(), json);
        if (sessionRegistry.envoyer(message.destinataireId(), json) && messageService.marquerLivre(message.id())) {
            accuser(List.of(message), StatutMessage.LIVRE);
        }
    }

    // Le lecteur a ouvert la conversation : tout ce que l'interlocuteur lui a envoyé passe en LU.
    private void traiterLu(UtilisateurConnecte moi, WebSocketSession session, TrameEntrante trame) {
        if (trame.interlocuteurId() == null) {
            repondreErreur(moi, session, "MESSAGE_INVALIDE", "interlocuteurId manquant");
            return;
        }
        accuser(messageService.marquerLus(moi.id(), trame.interlocuteurId()), StatutMessage.LU);
    }

    // Simple relais sans accès à la base : l'indicateur est éphémère et envoyé à chaque frappe.
    private void traiterSaisie(UtilisateurConnecte moi, WebSocketSession session, TrameEntrante trame) {
        if (trame.destinataireId() == null) {
            repondreErreur(moi, session, "MESSAGE_INVALIDE", "destinataireId manquant");
            return;
        }
        sessionRegistry.envoyer(trame.destinataireId(),
                jsonMapper.writeValueAsString(new SaisieSortante(moi.id(), Boolean.TRUE.equals(trame.enCours()))));
    }

    // Prévient l'expéditeur de chaque message ; s'il est hors ligne, il verra le statut dans l'historique.
    private void accuser(List<MessageDto> messages, StatutMessage statut) {
        for (MessageDto message : messages) {
            sessionRegistry.envoyer(message.expediteurId(),
                    jsonMapper.writeValueAsString(new AccuseSortant(message.id(), statut)));
        }
    }

    // La présence est un événement éphémère: diffusée aux connectés, jamais enregistrée en base.
    private void diffuserPresence(Long utilisateurId, boolean enLigne) {
        sessionRegistry.diffuser(jsonMapper.writeValueAsString(new PresenceSortante(utilisateurId, enLigne)), utilisateurId);
    }

    private void repondreErreur(UtilisateurConnecte moi, WebSocketSession session, String code, String detail) {
        sessionRegistry.repondre(moi.id(), session, jsonMapper.writeValueAsString(new ErreurSortante(code, detail)));
    }

    // Toujours présent : une session n'est ouverte qu'après la vérification du jeton par JwtHandshakeInterceptor.
    private static UtilisateurConnecte utilisateur(WebSocketSession session) {
        return (UtilisateurConnecte) session.getAttributes().get(JwtHandshakeInterceptor.ATTRIBUT_UTILISATEUR);
    }
}
