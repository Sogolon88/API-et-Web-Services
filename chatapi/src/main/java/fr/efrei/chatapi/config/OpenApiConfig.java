package fr.efrei.chatapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentation OpenAPI de l'API REST, consultable sur /swagger-ui.html.
 * Le schéma "bearer" permet de coller un JWT une fois dans Swagger UI pour tester les endpoints protégés.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEMA_JWT = "jwt";

    // OpenAPI ne décrit que HTTP : le protocole WebSocket est résumé dans la description.
    // Swagger UI l'affiche en Markdown : les chevrons sont placés dans du code, sinon ils seraient lus comme du HTML.
    private static final String DESCRIPTION = """
            API REST de la messagerie : comptes, connexion, utilisateurs et historique.
            Sauf la création de compte et la connexion, chaque requête exige l'en-tête
            `Authorization: Bearer <jeton>`, sinon le serveur répond 401.

            Le temps réel passe par le WebSocket `ws://<hôte>:8080/ws/messages?token=<jeton>`, avec les trames JSON
            message, accuse, lu, saisie, presence et erreur (voir le README).
            """;

    @Bean
    public OpenAPI documentationApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ChatAPI")
                        .version("v3")
                        .description(DESCRIPTION))
                .components(new Components().addSecuritySchemes(SCHEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Exigence appliquée à toute l'API ; les deux endpoints publics la retirent eux-mêmes.
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_JWT));
    }
}
