package fr.efrei.chatapi.websocket;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import fr.efrei.chatapi.security.JwtService;
import fr.efrei.chatapi.security.UtilisateurConnecte;
import lombok.RequiredArgsConstructor;

/**
 * Authentifie l'ouverture du WebSocket. Le jeton est lu dans l'URL (?token=) car l'API WebSocket
 * des navigateurs ne permet pas d'ajouter un en-tête Authorization à la requête de handshake.
 * L'identité est vérifiée une seule fois ici, puis rattachée à la session pour toute sa durée.
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    // Clé sous laquelle l'utilisateur authentifié est conservé dans les attributs de la session.
    public static final String ATTRIBUT_UTILISATEUR = "utilisateur";

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = request instanceof ServletServerHttpRequest servlet
                ? servlet.getServletRequest().getParameter("token")
                : null;
        UtilisateurConnecte utilisateur = token == null ? null : jwtService.lireToken(token).orElse(null);
        if (utilisateur == null) {
            // Handshake refusé avec le même code que l'API REST : la connexion n'est jamais ouverte.
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTRIBUT_UTILISATEUR, utilisateur);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Rien à faire après l'ouverture : l'enregistrement de la session est fait par le handler.
    }
}
