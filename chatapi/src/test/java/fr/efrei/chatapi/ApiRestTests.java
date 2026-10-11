package fr.efrei.chatapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import fr.efrei.chatapi.model.Message;
import fr.efrei.chatapi.model.StatutMessage;
import fr.efrei.chatapi.repository.MessageRepository;
import fr.efrei.chatapi.repository.UtilisateurRepository;

/**
 * Tests d'intégration du contrat REST. Le contexte Spring complet est démarré pour que
 * chaque requête traverse la vraie chaîne de sécurité (filtre JWT, réponse 401) sur une base H2 en mémoire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiRestTests {

    private static final String MOT_DE_PASSE = "secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private MessageRepository messageRepository;

    // Base vidée avant chaque test : aucun test ne dépend des données laissées par un autre.
    @BeforeEach
    void viderLaBase() {
        messageRepository.deleteAll();
        utilisateurRepository.deleteAll();
    }

    @Test
    void creationDeCompteRepond201SansExposerLeMotDePasse() throws Exception {
        creerCompte("seyni", MOT_DE_PASSE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("seyni"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // Le mot de passe est stocké sous forme de hachage BCrypt, reconnaissable à son préfixe "$2".
        String stocke = utilisateurRepository.findByUsername("seyni").orElseThrow().getPassword();
        assertThat(stocke).startsWith("$2").isNotEqualTo(MOT_DE_PASSE);
    }

    @Test
    void creationDeCompteEnDoubleRepond409() throws Exception {
        creerCompte("seyni", MOT_DE_PASSE).andExpect(status().isCreated());
        creerCompte("seyni", MOT_DE_PASSE).andExpect(status().isConflict());
    }

    @Test
    void creationDeCompteInvalideRepond400() throws Exception {
        creerCompte("", MOT_DE_PASSE).andExpect(status().isBadRequest());
        creerCompte("seyni", "123").andExpect(status().isBadRequest());
    }

    @Test
    void connexionValideRenvoieUnJeton() throws Exception {
        creerCompte("seyni", MOT_DE_PASSE);

        connecter("seyni", MOT_DE_PASSE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("seyni"));
    }

    @Test
    void connexionInvalideRepond401() throws Exception {
        creerCompte("seyni", MOT_DE_PASSE);

        connecter("seyni", "mauvais-mot-de-passe").andExpect(status().isUnauthorized());
        connecter("inconnu", MOT_DE_PASSE).andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsProtegesRepondent401SansJetonValide() throws Exception {
        mockMvc.perform(get("/api/utilisateurs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/messages/1")).andExpect(status().isUnauthorized());

        // Forme de JWT correcte mais signature invalide : le filtre ne l'accepte pas.
        mockMvc.perform(get("/api/utilisateurs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientWebAccessibleSansJeton() throws Exception {
        for (String fichier : new String[] {"index.html", "mobile.html", "desktop.html",
                "chat.js", "chat.css", "mobile.css", "desktop.css"}) {
            mockMvc.perform(get("/" + fichier)).andExpect(status().isOk());
        }
    }

    @Test
    void documentationOpenApiAccessibleSansJeton() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/comptes'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/utilisateurs'].get").exists())
                // L'utilisateur connecté vient du jeton : il ne doit pas apparaître comme paramètre.
                .andExpect(jsonPath("$.paths['/api/messages/{utilisateurId}'].get.parameters", hasSize(1)))
                .andExpect(jsonPath("$.components.securitySchemes.jwt.scheme").value("bearer"));
    }

    @Test
    void listeDesUtilisateursAvecJeton() throws Exception {
        creerCompte("seyni", MOT_DE_PASSE);
        creerCompte("fode", MOT_DE_PASSE);
        String jeton = jeton("seyni");

        mockMvc.perform(get("/api/utilisateurs").header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].password").isEmpty())
                .andExpect(jsonPath("$[0].enLigne").value(false));
    }

    @Test
    void historiqueIdentiqueVuDesDeuxCotesEtTrieParDate() throws Exception {
        Long seyniId = idDepuis(creerCompte("seyni", MOT_DE_PASSE));
        Long fodeId = idDepuis(creerCompte("fode", MOT_DE_PASSE));

        // L'envoi passe par le WebSocket (v2) : les messages sont insérés directement en base.
        // Le plus récent est enregistré en premier pour vérifier que le tri vient bien de la requête.
        LocalDateTime debut = LocalDateTime.of(2026, 9, 21, 10, 15, 30);
        enregistrerMessage(fodeId, seyniId, "Salut Seyni", debut.plusMinutes(1));
        enregistrerMessage(seyniId, fodeId, "Bonjour Fode", debut);
        assertThat(messageRepository.findAll()).allMatch(m -> m.getStatut() == StatutMessage.ENVOYE);

        for (String utilisateur : new String[] {"seyni", "fode"}) {
            Long interlocuteurId = utilisateur.equals("seyni") ? fodeId : seyniId;
            mockMvc.perform(get("/api/messages/" + interlocuteurId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton(utilisateur)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].contenu").value("Bonjour Fode"))
                    .andExpect(jsonPath("$[0].expediteurId").value(seyniId))
                    .andExpect(jsonPath("$[0].destinataireId").value(fodeId))
                    .andExpect(jsonPath("$[0].dateEnvoi").value("2026-09-21T10:15:30"))
                    .andExpect(jsonPath("$[1].contenu").value("Salut Seyni"));
        }
    }

    private ResultActions creerCompte(String username, String password) throws Exception {
        return envoyerIdentifiants("/api/comptes", username, password);
    }

    private ResultActions connecter(String username, String password) throws Exception {
        return envoyerIdentifiants("/api/auth/login", username, password);
    }

    private ResultActions envoyerIdentifiants(String url, String username, String password) throws Exception {
        String corps = """
                {"username": "%s", "password": "%s"}
                """.formatted(username, password);
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private String jeton(String username) throws Exception {
        String reponse = connecter(username, MOT_DE_PASSE).andReturn().getResponse().getContentAsString();
        return JsonPath.read(reponse, "$.token");
    }

    private Long idDepuis(ResultActions reponse) throws Exception {
        Number id = JsonPath.read(reponse.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private void enregistrerMessage(Long expediteurId, Long destinataireId, String contenu, LocalDateTime date) {
        Message message = new Message();
        message.setExpediteurId(expediteurId);
        message.setDestinataireId(destinataireId);
        message.setContenu(contenu);
        message.setDateEnvoi(date);
        messageRepository.save(message);
    }
}
