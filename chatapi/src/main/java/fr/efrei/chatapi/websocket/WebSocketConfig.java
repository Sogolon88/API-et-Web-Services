package fr.efrei.chatapi.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

/**
 * Déclare le point d'entrée temps réel. REST sert les ressources (comptes, historique) ;
 * le WebSocket garde une connexion ouverte pour que le serveur pousse les messages sans attendre de requête.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketHandler messageWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    // Origines autorisées laissées par défaut (même origine) : les clients web sont servis par ce serveur.
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketHandler, "/ws/messages")
                .addInterceptors(jwtHandshakeInterceptor);
    }
}
