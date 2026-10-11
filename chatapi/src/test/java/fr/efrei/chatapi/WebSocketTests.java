package fr.efrei.chatapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import fr.efrei.chatapi.model.Message;
import fr.efrei.chatapi.model.StatutMessage;
import fr.efrei.chatapi.model.Utilisateur;
import fr.efrei.chatapi.repository.MessageRepository;
import fr.efrei.chatapi.repository.UtilisateurRepository;
import fr.efrei.chatapi.security.JwtService;
import fr.efrei.chatapi.service.UtilisateurService;
import fr.efrei.chatapi.websocket.SessionRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test d'intégration du canal temps réel : un vrai serveur est démarré sur un port aléatoire
 * et deux clients WebSocket (Seyni et Fode) s'y connectent comme le feraient les navigateurs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketTests {

    private static final long DELAI_SECONDES = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private UtilisateurService utilisateurService;

    private final List<WebSocketSession> sessionsOuvertes = new ArrayList<>();

    private Utilisateur seyni;
    private Utilisateur fode;

    @BeforeEach
    void preparer() {
        messageRepository.deleteAll();
        utilisateurRepository.deleteAll();
        seyni = creerUtilisateur("seyni");
        fode = creerUtilisateur("fode");
    }

    // La fermeture est traitée côté serveur en différé : on attend qu'elle soit prise en compte
    // pour qu'aucun événement de présence tardif n'arrive pendant le test suivant.
    @AfterEach
    void fermerLesSessions() throws Exception {
        for (WebSocketSession session : sessionsOuvertes) {
            session.close();
        }
        attendre(() -> !sessionRegistry.estConnecte(seyni.getId()) && !sessionRegistry.estConnecte(fode.getId()));
    }

    @Test
    void presenceDiffuseeALaPremiereConnexionEtALaDerniereDeconnexion() throws Exception {
        BlockingQueue<String> recusParFode = new LinkedBlockingQueue<>();
        connecter(jwtService.genererToken(fode), recusParFode);

        WebSocketSession telephone = connecter(jwtService.genererToken(seyni), new LinkedBlockingQueue<>());
        JsonNode enLigne = jsonMapper.readTree(recusParFode.poll(DELAI_SECONDES, TimeUnit.SECONDS));
        assertThat(enLigne.path("type").asString()).isEqualTo("presence");
        assertThat(enLigne.path("utilisateurId").asLong()).isEqualTo(seyni.getId());
        assertThat(enLigne.path("enLigne").asBoolean()).isTrue();
        assertThat(utilisateurService.lister())
                .anyMatch(u -> u.id().equals(seyni.getId()) && u.enLigne());

        // Un second appareil, puis la fermeture du premier : Seyni reste en ligne, rien n'est diffusé.
        WebSocketSession ordinateur = connecter(jwtService.genererToken(seyni), new LinkedBlockingQueue<>());
        telephone.close();
        assertThat(recusParFode.poll(500, TimeUnit.MILLISECONDS)).isNull();

        ordinateur.close();
        JsonNode horsLigne = jsonMapper.readTree(recusParFode.poll(DELAI_SECONDES, TimeUnit.SECONDS));
        assertThat(horsLigne.path("utilisateurId").asLong()).isEqualTo(seyni.getId());
        assertThat(horsLigne.path("enLigne").asBoolean()).isFalse();
    }

    @Test
    void handshakeRefuseSansJetonValide() {
        assertThatThrownBy(() -> connecter("", new LinkedBlockingQueue<>()))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connecter("abc.def.ghi", new LinkedBlockingQueue<>()))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void messageEnregistrePuisRelayeAuDestinataireConnecte() throws Exception {
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), new LinkedBlockingQueue<>());
        BlockingQueue<String> recusParFode = new LinkedBlockingQueue<>();
        connecter(jwtService.genererToken(fode), recusParFode);

        // Le client tente d'usurper un autre expéditeur et d'imposer sa date : le serveur doit les ignorer.
        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","expediteurId":999,"destinataireId":%d,
                 "contenu":"Bonjour Fode","dateEnvoi":"2000-01-01T00:00:00"}
                """.formatted(fode.getId())));

        JsonNode recu = jsonMapper.readTree(recusParFode.poll(DELAI_SECONDES, TimeUnit.SECONDS));
        assertThat(recu.path("type").asString()).isEqualTo("message");
        assertThat(recu.path("expediteurId").asLong()).isEqualTo(seyni.getId());
        assertThat(recu.path("destinataireId").asLong()).isEqualTo(fode.getId());
        assertThat(recu.path("contenu").asString()).isEqualTo("Bonjour Fode");
        assertThat(LocalDateTime.parse(recu.path("dateEnvoi").asString()))
                .isAfter(LocalDateTime.now().minusMinutes(1));

        // Le statut passe à LIVRE juste après l'envoi, dans le thread du serveur : on l'attend.
        attendre(() -> messageRepository.findById(recu.path("id").asLong())
                .map(m -> m.getStatut() == StatutMessage.LIVRE).orElse(false));
    }

    @Test
    void messagePourUnDestinataireHorsLigneResteEnBase() throws Exception {
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), new LinkedBlockingQueue<>());

        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","destinataireId":%d,"contenu":"Tu es là ?"}
                """.formatted(fode.getId())));

        attendre(() -> messageRepository.count() == 1);
        Message enregistre = messageRepository.findAll().getFirst();
        assertThat(enregistre.getExpediteurId()).isEqualTo(seyni.getId());
        assertThat(enregistre.getStatut()).isEqualTo(StatutMessage.ENVOYE);
    }

    @Test
    void tramesInvalidesRenvoientUneErreurSansFermerLeCanal() throws Exception {
        BlockingQueue<String> recusParSeyni = new LinkedBlockingQueue<>();
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), recusParSeyni);

        sessionSeyni.sendMessage(new TextMessage("pas du JSON"));
        assertThat(codeErreur(recusParSeyni)).isEqualTo("FORMAT_INVALIDE");

        sessionSeyni.sendMessage(new TextMessage("{\"type\":\"inconnu\"}"));
        assertThat(codeErreur(recusParSeyni)).isEqualTo("TYPE_INCONNU");

        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","destinataireId":%d,"contenu":"   "}
                """.formatted(fode.getId())));
        assertThat(codeErreur(recusParSeyni)).isEqualTo("MESSAGE_INVALIDE");

        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","destinataireId":-1,"contenu":"Bonjour"}
                """));
        assertThat(codeErreur(recusParSeyni)).isEqualTo("MESSAGE_INVALIDE");

        assertThat(sessionSeyni.isOpen()).isTrue();
        assertThat(messageRepository.count()).isZero();
    }

    private Utilisateur creerUtilisateur(String username) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setUsername(username);
        utilisateur.setPassword(passwordEncoder.encode("secret123"));
        return utilisateurRepository.save(utilisateur);
    }

    // Ouvre une connexion dont chaque trame reçue est déposée dans la file fournie.
    private WebSocketSession connecter(String token, BlockingQueue<String> recus) throws Exception {
        TextWebSocketHandler collecteur = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                recus.add(message.getPayload());
            }
        };
        URI uri = URI.create("ws://localhost:" + port + "/ws/messages?token=" + token);
        WebSocketSession session = new StandardWebSocketClient()
                .execute(collecteur, new WebSocketHttpHeaders(), uri)
                .get(DELAI_SECONDES, TimeUnit.SECONDS);
        sessionsOuvertes.add(session);
        return session;
    }

    private String codeErreur(BlockingQueue<String> recus) throws Exception {
        JsonNode erreur = jsonMapper.readTree(recus.poll(DELAI_SECONDES, TimeUnit.SECONDS));
        assertThat(erreur.path("type").asString()).isEqualTo("erreur");
        return erreur.path("code").asString();
    }

    // Le serveur traite les trames dans ses propres threads : on interroge la base jusqu'au délai maximal.
    private static void attendre(BooleanSupplier condition) throws InterruptedException {
        long limite = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DELAI_SECONDES);
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition non atteinte à temps").isLessThan(limite);
            Thread.sleep(50);
        }
    }
}
