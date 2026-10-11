package fr.efrei.chatapi.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long expediteurId;

    @Column(nullable = false)
    private Long destinataireId;

    @Column(nullable = false, length = 2000)
    private String contenu;

    // Horodatage fixé par le serveur, jamais par le client.
    @Column(nullable = false)
    private LocalDateTime dateEnvoi;

    // Stocké en texte et non en rang : la base reste lisible et l'ordre de l'enum peut évoluer.
    // Tout message est d'abord ENVOYE, car il est persisté avant toute tentative de livraison.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatutMessage statut = StatutMessage.ENVOYE;
}
