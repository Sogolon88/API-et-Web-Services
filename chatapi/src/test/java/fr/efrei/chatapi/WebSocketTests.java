package fr.efrei.chatapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.sun.net.httpserver.HttpServer;

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

    // Service tiers simulé : un mini serveur HTTP du JDK qui conserve le corps de chaque webhook reçu.
    private static final BlockingQueue<String> WEBHOOKS_RECUS = new LinkedBlockingQueue<>();
    private static HttpServer serviceTiers;

    private final List<WebSocketSession> sessionsOuvertes = new ArrayList<>();

    private Utilisateur seyni;
    private Utilisateur fode;

    @DynamicPropertySource
    static void configurerWebhook(DynamicPropertyRegistry proprietes) throws IOException {
        serviceTiers = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serviceTiers.createContext("/notifications", echange -> {
            WEBHOOKS_RECUS.add(new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            echange.sendResponseHeaders(204, -1);
            echange.close();
        });
        serviceTiers.start();
        proprietes.add("chat.webhook.url",
                () -> "http://localhost:" + serviceTiers.getAddress().getPort() + "/notifications");
    }

    @AfterAll
    static void arreterServiceTiers() {
        serviceTiers.stop(0);
    }

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
    void messageEnregistreRenvoyeALExpediteurPuisRelayeEtAccuseLivre() throws Exception {
        BlockingQueue<String> recusParSeyni = new LinkedBlockingQueue<>();
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), recusParSeyni);
        BlockingQueue<String> recusParFode = new LinkedBlockingQueue<>();
        connecter(jwtService.genererToken(fode), recusParFode);

        // Le client tente d'usurper un autre expéditeur et d'imposer sa date : le serveur doit les ignorer.
        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","expediteurId":999,"destinataireId":%d,
                 "contenu":"Bonjour Fode","dateEnvoi":"2000-01-01T00:00:00"}
                """.formatted(fode.getId())));

        JsonNode recu = prochaine(recusParFode, "message");
        assertThat(recu.path("expediteurId").asLong()).isEqualTo(seyni.getId());
        assertThat(recu.path("destinataireId").asLong()).isEqualTo(fode.getId());
        assertThat(recu.path("contenu").asString()).isEqualTo("Bonjour Fode");
        assertThat(LocalDateTime.parse(recu.path("dateEnvoi").asString()))
                .isAfter(LocalDateTime.now().minusMinutes(1));

        // L'expéditeur reçoit d'abord l'écho (id et date du serveur), puis l'accusé de livraison.
        JsonNode echo = prochaine(recusParSeyni, "message");
        assertThat(echo.path("id").asLong()).isEqualTo(recu.path("id").asLong());
        assertThat(echo.path("statut").asString()).isEqualTo("ENVOYE");
        JsonNode accuse = prochaine(recusParSeyni, "accuse");
        assertThat(accuse.path("messageId").asLong()).isEqualTo(recu.path("id").asLong());
        assertThat(accuse.path("statut").asString()).isEqualTo("LIVRE");
        assertThat(messageRepository.findById(recu.path("id").asLong()).orElseThrow().getStatut())
                .isEqualTo(StatutMessage.LIVRE);
    }

    @Test
    void messagePourUnDestinataireHorsLigneLivreASaConnexion() throws Exception {
        BlockingQueue<String> recusParSeyni = new LinkedBlockingQueue<>();
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), recusParSeyni);

        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","destinataireId":%d,"contenu":"Tu es là ?"}
                """.formatted(fode.getId())));

        long id = prochaine(recusParSeyni, "message").path("id").asLong();
        Message enregistre = messageRepository.findById(id).orElseThrow();
        assertThat(enregistre.getExpediteurId()).isEqualTo(seyni.getId());
        assertThat(enregistre.getStatut()).isEqualTo(StatutMessage.ENVOYE);

        connecter(jwtService.genererToken(fode), new LinkedBlockingQueue<>());
        JsonNode accuse = prochaine(recusParSeyni, "accuse");
        assertThat(accuse.path("messageId").asLong()).isEqualTo(id);
        assertThat(accuse.path("statut").asString()).isEqualTo("LIVRE");
    }

    @Test
    void lectureDeLaConversationAccuseeLu() throws Exception {
        BlockingQueue<String> recusParSeyni = new LinkedBlockingQueue<>();
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), recusParSeyni);
        BlockingQueue<String> recusParFode = new LinkedBlockingQueue<>();
        WebSocketSession sessionFode = connecter(jwtService.genererToken(fode), recusParFode);

        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","destinataireId":%d,"contenu":"Bonjour Fode"}
                """.formatted(fode.getId())));
        long id = prochaine(recusParFode, "message").path("id").asLong();

        sessionFode.sendMessage(new TextMessage("""
                {"type":"lu","interlocuteurId":%d}
                """.formatted(seyni.getId())));

        // Selon l'ordre des threads, l'accusé LIVRE peut précéder l'accusé LU ou ne pas être envoyé,
        // mais il ne doit jamais le suivre : le statut ne recule pas.
        JsonNode accuse = prochaine(recusParSeyni, "accuse");
        if (accuse.path("statut").asString().equals("LIVRE")) {
            accuse = prochaine(recusParSeyni, "accuse");
        }
        assertThat(accuse.path("messageId").asLong()).isEqualTo(id);
        assertThat(accuse.path("statut").asString()).isEqualTo("LU");
        assertThat(recusParSeyni.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(messageRepository.findById(id).orElseThrow().getStatut()).isEqualTo(StatutMessage.LU);
    }

    @Test
    void saisieRelayeeAuDestinataireSansEtreEnregistree() throws Exception {
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), new LinkedBlockingQueue<>());
        BlockingQueue<String> recusParFode = new LinkedBlockingQueue<>();
        connecter(jwtService.genererToken(fode), recusParFode);

        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"saisie","expediteurId":999,"destinataireId":%d,"enCours":true}
                """.formatted(fode.getId())));

        JsonNode saisie = prochaine(recusParFode, "saisie");
        assertThat(saisie.path("expediteurId").asLong()).isEqualTo(seyni.getId());
        assertThat(saisie.path("enCours").asBoolean()).isTrue();
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void webhookAppeleQuandLeDestinataireEstHorsLigne() throws Exception {
        BlockingQueue<String> recusParSeyni = new LinkedBlockingQueue<>();
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), recusParSeyni);

        sessionSeyni.sendMessage(new TextMessage("""
                {"type":"message","destinataireId":%d,"contenu":"Message privé"}
                """.formatted(fode.getId())));
        long id = prochaine(recusParSeyni, "message").path("id").asLong();

        // D'autres tests envoient aussi à des destinataires hors ligne : on cherche le webhook de ce message.
        JsonNode notification = null;
        long limite = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DELAI_SECONDES);
        while (notification == null || notification.path("messageId").asLong() != id) {
            String corps = WEBHOOKS_RECUS.poll(limite - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            assertThat(corps).as("aucun webhook reçu pour le message %d", id).isNotNull();
            notification = jsonMapper.readTree(corps);
        }
        assertThat(notification.path("evenement").asString()).isEqualTo("nouveau_message");
        assertThat(notification.path("destinataireId").asLong()).isEqualTo(fode.getId());
        assertThat(notification.path("expediteur").asString()).isEqualTo("seyni");
        assertThat(notification.has("contenu")).isFalse();
    }

    @Test
    void envoiEnRafaleLimite() throws Exception {
        BlockingQueue<String> recusParSeyni = new LinkedBlockingQueue<>();
        WebSocketSession sessionSeyni = connecter(jwtService.genererToken(seyni), recusParSeyni);

        // Limite de 5 messages dans le profil de test : le sixième est refusé et n'est pas enregistré.
        for (int i = 1; i <= 6; i++) {
            sessionSeyni.sendMessage(new TextMessage("""
                    {"type":"message","destinataireId":%d,"contenu":"Message %d"}
                    """.formatted(fode.getId(), i)));
        }

        assertThat(prochaine(recusParSeyni, "erreur").path("code").asString()).isEqualTo("LIMITE_DEPASSEE");
        assertThat(messageRepository.count()).isEqualTo(5);
        assertThat(sessionSeyni.isOpen()).isTrue();
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

        sessionSeyni.sendMessage(new TextMessage("{\"type\":\"lu\"}"));
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

    // Prochaine trame du type attendu ; les autres (présence, écho) sont ignorées.
    private JsonNode prochaine(BlockingQueue<String> recus, String type) throws Exception {
        long limite = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DELAI_SECONDES);
        while (true) {
            String texte = recus.poll(limite - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            assertThat(texte).as("aucune trame \"%s\" reçue à temps", type).isNotNull();
            JsonNode trame = jsonMapper.readTree(texte);
            if (trame.path("type").asString().equals(type)) {
                return trame;
            }
        }
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
