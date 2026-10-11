package fr.efrei.chatapi.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentifie chaque requête REST à partir de l'en-tête "Authorization: Bearer <jeton>".
 * Le serveur ne conserve aucune session : chaque requête porte sa propre preuve d'identité,
 * conformément au principe sans état (stateless) de REST.
 * Volontairement pas annoté @Component : Spring Boot l'enregistrerait aussi comme
 * filtre servlet global et il s'exécuterait deux fois.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String entete = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (entete != null && entete.startsWith(PREFIXE)) {
            // Sans jeton valide, le contexte reste vide : la chaîne de sécurité répondra 401.
            jwtService.lireToken(entete.substring(PREFIXE.length()))
                    .ifPresent(utilisateur -> SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(utilisateur, null, List.of())));
        }
        chain.doFilter(request, response);
    }
}
