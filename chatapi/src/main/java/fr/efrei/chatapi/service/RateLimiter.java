package fr.efrei.chatapi.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Limitation de débit par fenêtre glissante : un utilisateur peut envoyer au plus maxMessages
 * pendant toute période de fenetreMs. Contrairement à une fenêtre fixe (remise à zéro chaque minute),
 * elle empêche d'envoyer deux rafales de part et d'autre d'une remise à zéro.
 * Mémoire bornée : au plus maxMessages horodatages conservés par utilisateur.
 */
@Service
public class RateLimiter {

    private final int maxMessages;
    private final long fenetreMs;

    // Horodatages des derniers envois autorisés, du plus ancien au plus récent.
    private final Map<Long, Deque<Long>> envoisParUtilisateur = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${chat.limite.messages}") int maxMessages,
                       @Value("${chat.limite.fenetre-ms}") long fenetreMs) {
        this.maxMessages = maxMessages;
        this.fenetreMs = fenetreMs;
    }

    /**
     * Enregistre une tentative d'envoi.
     *
     * @return true si l'envoi est autorisé, false si la limite est atteinte
     */
    public boolean autoriser(Long utilisateurId) {
        long maintenant = System.currentTimeMillis();
        Deque<Long> envois = envoisParUtilisateur.computeIfAbsent(utilisateurId, id -> new ArrayDeque<>());
        // Verrou par utilisateur : deux appareils du même compte ne peuvent pas dépasser la limite ensemble,
        // et les autres utilisateurs ne sont jamais bloqués.
        synchronized (envois) {
            while (!envois.isEmpty() && envois.peekFirst() <= maintenant - fenetreMs) {
                envois.pollFirst();
            }
            if (envois.size() >= maxMessages) {
                return false;
            }
            envois.addLast(maintenant);
            return true;
        }
    }
}
