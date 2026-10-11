package fr.efrei.chatapi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration de Spring Security : routes publiques, routes protégées par JWT
 * et réponse 401 pour toute requête non authentifiée.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // BCrypt intègre un sel aléatoire et un coût réglable : deux mots de passe identiques
    // donnent deux hachages différents, et une attaque par force brute reste lente.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Spring construit l'AuthenticationManager à partir du UserDetailsService et du PasswordEncoder déclarés.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    // Chaîne de filtres appliquée à chaque requête HTTP, avant l'appel du contrôleur.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        return http
                // API sans état authentifiée par jeton : pas de cookie de session, donc pas de CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/comptes", "/api/auth/login").permitAll()
                        // /error doit rester public, sinon chaque erreur serait masquée par un 401.
                        .requestMatchers("/error").permitAll()
                        // Le jeton du WebSocket est dans l'URL et non dans l'en-tête : il est vérifié
                        // par JwtHandshakeInterceptor, qui refuse lui-même le handshake avec un 401.
                        .requestMatchers(HttpMethod.GET, "/ws/messages").permitAll()
                        // Fichiers du client web, à la racine uniquement ("/*" ne descend pas dans /api) :
                        // ils ne contiennent aucune donnée, et toute autre ressource reste protégée par défaut.
                        .requestMatchers(HttpMethod.GET, "/", "/*.html", "/*.css", "/*.js").permitAll()
                        // Documentation OpenAPI publique : elle décrit l'API, les endpoints restent protégés.
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                // Par défaut Spring répond 403 ; le sujet exige 401 pour une requête sans jeton valide.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
