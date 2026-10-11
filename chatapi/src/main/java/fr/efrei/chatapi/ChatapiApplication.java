package fr.efrei.chatapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée du serveur de messagerie. L'application suit une architecture en couches :
 * contrôleurs (HTTP), services (règles métier), dépôts (accès aux données) et entités.
 */
@SpringBootApplication
public class ChatapiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatapiApplication.class, args);
    }
}
