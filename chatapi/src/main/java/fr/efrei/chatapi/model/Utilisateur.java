package fr.efrei.chatapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

// @Getter/@Setter plutôt que @Data : equals/hashCode générés sur une entité JPA sont source de bugs.
@Entity
@Getter
@Setter
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unicité garantie par la base, pas seulement par une vérification applicative.
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // Contient toujours un hash BCrypt, jamais le mot de passe en clair.
    @Column(nullable = false)
    private String password;
}
