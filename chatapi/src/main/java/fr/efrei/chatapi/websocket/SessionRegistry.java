package fr.efrei.chatapi.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;

import lombok.extern.slf4j.Slf4j;

/**
 * Annuaire des connexions ouvertes : pour chaque utilisateur, ses sessions WebSocket indexées par id.
 * Un utilisateur peut être connecté depuis plusieurs appareils; chacun reçoit les messages.
 * C'est le seul état du serveur qui n'est pas en base: il disparaît avec les connexions.
 */
@Slf4j
@Component
public class SessionRegistry {

    // Limites du tampon d'envoi d'une session: au-delà, un client trop lent est déconnecté.
    private static final int DELAI_ENVOI_MS = 5_000;
    private static final int TAMPON_OCTETS = 64 * 1024;

    // Plusieurs threads (un par connexion) lisent et modifient cette table en même temps.
    private final Map<Long, Map<String, WebSocketSession>> sessionsParUtilisateur = new ConcurrentHashMap<>();

    /**
     * Enregistre une session. Elle est enveloppée dans un décorateur car une WebSocketSession
     * n'accepte pas deux envois simultanés: le décorateur les met en file d'attente.
     */
    public void ajouter(Long utilisateurId, WebSocketSession session) {
        WebSocketSession protegee = new ConcurrentWebSocketSessionDecorator(session, DELAI_ENVOI_MS, TAMPON_OCTETS);
        // compute est atomique : pas de course avec un retrait simultané de la même entrée.
        sessionsParUtilisateur.compute(utilisateurId, (id, sessions) -> {
            Map<String, WebSocketSession> resultat = sessions != null ? sessions : new ConcurrentHashMap<>();
            resultat.put(session.getId(), protegee);
            return resultat;
        });
    }

    public void retirer(Long utilisateurId, WebSocketSession session) {
        // Renvoyer null supprime l'entrée: un utilisateur sans session n'apparaît plus dans la table.
        sessionsParUtilisateur.computeIfPresent(utilisateurId, (id, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /**
     * Envoie un texte à toutes les sessions d'un utilisateur.
     * Renvoie true si au moins un appareil l'a reçu, false si l'utilisateur est hors ligne.
     */
    public boolean envoyer(Long utilisateurId, String json) {
        boolean livre = false;
        for (WebSocketSession session : sessionsParUtilisateur.getOrDefault(utilisateurId, Map.of()).values()) {
            livre |= envoyerSur(session, json);
        }
        return livre;
    }

    // Répond à la session qui a émis la requête, en passant par son décorateur.
    public void repondre(Long utilisateurId, WebSocketSession session, String json) {
        WebSocketSession protegee = sessionsParUtilisateur.getOrDefault(utilisateurId, Map.of()).get(session.getId());
        if (protegee != null) {
            envoyerSur(protegee, json);
        }
    }

    private boolean envoyerSur(WebSocketSession session, String json) {
        try {
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException | SessionLimitExceededException e) {
            // La session sera retirée par afterConnectionClosed ; le message reste en base.
            log.warn("Envoi impossible sur la session {} : {}", session.getId(), e.getMessage());
            return false;
        }
    }
}
