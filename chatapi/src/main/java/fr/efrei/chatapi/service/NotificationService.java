package fr.efrei.chatapi.service;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import fr.efrei.chatapi.dto.MessageDto;
import fr.efrei.chatapi.dto.NotificationWebhook;
import lombok.extern.slf4j.Slf4j;

/**
 * Notification hors ligne par webhook : le serveur prévient un service tiers (envoi de push, de mail)
 * par une requête HTTP POST, sur le modèle événementiel du cours. Sans URL configurée,
 * le service tiers est simulé par une ligne de log, ce qui garde le projet lançable sans configuration.
 */
@Slf4j
@Service
public class NotificationService {

    private static final Duration DELAI_MAX = Duration.ofSeconds(2);

    private final String urlWebhook;
    private final RestClient restClient;

    public NotificationService(@Value("${chat.webhook.url}") String urlWebhook) {
        this.urlWebhook = urlWebhook;
        // Délais courts : un service tiers lent ne doit pas immobiliser les threads du serveur.
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(DELAI_MAX).build());
        fabrique.setReadTimeout(DELAI_MAX);
        this.restClient = RestClient.builder().requestFactory(fabrique).build();
    }

    /**
     * Prévient le service tiers qu'un message attend un destinataire hors ligne.
     * Exécutée dans un autre thread (@Async) : le canal WebSocket de l'expéditeur n'attend pas la réponse.
     * Un échec est seulement journalisé, le message restant disponible dans l'historique.
     */
    @Async
    public void notifierHorsLigne(MessageDto message, String expediteur) {
        if (urlWebhook.isBlank()) {
            log.info("Notification envoyée à l'utilisateur {} (service tiers simulé) : nouveau message de {}",
                    message.destinataireId(), expediteur);
            return;
        }
        try {
            restClient.post()
                    .uri(urlWebhook)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(NotificationWebhook.nouveauMessage(message, expediteur))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notification envoyée à l'utilisateur {} via le webhook : nouveau message de {}",
                    message.destinataireId(), expediteur);
        } catch (RestClientException e) {
            log.warn("Échec du webhook pour le message {} : {}", message.id(), e.getMessage());
        }
    }
}
