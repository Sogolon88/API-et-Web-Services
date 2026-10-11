package fr.efrei.chatapi.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.efrei.chatapi.dto.MessageDto;
import fr.efrei.chatapi.model.Message;
import fr.efrei.chatapi.model.StatutMessage;
import fr.efrei.chatapi.repository.MessageRepository;
import fr.efrei.chatapi.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MessageService {

    // Même borne que la colonne en base : un contenu trop long est refusé avant l'insertion.
    private static final int CONTENU_MAX = 2000;

    private final MessageRepository messageRepository;
    private final UtilisateurRepository utilisateurRepository;

    // Les entités sont converties en DTO : le format JSON imposé par le sujet reste indépendant du schéma.
    public List<MessageDto> historique(Long utilisateurConnecteId, Long interlocuteurId) {
        return messageRepository.findMessages(utilisateurConnecteId, interlocuteurId).stream()
                .map(MessageDto::depuis)
                .toList();
    }

    /**
     * Valide et enregistre un message en statut ENVOYE, avant toute tentative de livraison :
     * si le destinataire est hors ligne ou si le relais échoue, le message n'est pas perdu.
     * L'expéditeur vient du jeton et la date de l'horloge du serveur, jamais du client.
     *
     * @throws IllegalArgumentException si le destinataire est inconnu ou le contenu invalide
     */
    public MessageDto enregistrer(Long expediteurId, Long destinataireId, String contenu) {
        if (destinataireId == null || !utilisateurRepository.existsById(destinataireId)) {
            throw new IllegalArgumentException("Destinataire inconnu");
        }
        if (contenu == null || contenu.isBlank() || contenu.length() > CONTENU_MAX) {
            throw new IllegalArgumentException("Le contenu doit faire entre 1 et " + CONTENU_MAX + " caractères");
        }
        Message message = new Message();
        message.setExpediteurId(expediteurId);
        message.setDestinataireId(destinataireId);
        message.setContenu(contenu);
        // Tronquée à la seconde pour correspondre au format du sujet ("2026-09-21T10:15:30").
        message.setDateEnvoi(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        return MessageDto.depuis(messageRepository.save(message));
    }

    @Transactional
    public void marquerLivre(Long messageId) {
        messageRepository.changerStatut(messageId, StatutMessage.LIVRE);
    }
}
