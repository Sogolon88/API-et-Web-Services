package fr.efrei.chatapi.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.efrei.chatapi.model.Utilisateur;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final String CLAIM_ID = "uid";

    private final SecretKey cle;
    private final long dureeValiditeMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long dureeValiditeMs) {
        // Clé construite une seule fois. hmacShaKeyFor refuse une clé de moins de 256 bits :
        // une configuration faible fait échouer le démarrage au lieu de passer inaperçue.
        this.cle = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.dureeValiditeMs = dureeValiditeMs;
    }

    public String genererToken(Utilisateur utilisateur) {
        long maintenant = System.currentTimeMillis();
        // L'id est embarqué dans le jeton pour éviter une lecture en base à chaque requête.
        return Jwts.builder()
                .subject(utilisateur.getUsername())
                .claim(CLAIM_ID, utilisateur.getId())
                .issuedAt(new Date(maintenant))
                .expiration(new Date(maintenant + dureeValiditeMs))
                .signWith(cle)
                .compact();
    }

    /**
     * Vérifie signature et expiration. Un jeton invalide donne un Optional vide
     * plutôt qu'une exception, pour que l'appelant réponde 401 et non 500.
     */
    public Optional<UtilisateurConnecte> lireToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(cle)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // Le JSON peut désérialiser l'id en Integer ou en Long : on passe par Number.
            if (!(claims.get(CLAIM_ID) instanceof Number id)) {
                return Optional.empty();
            }
            return Optional.of(new UtilisateurConnecte(id.longValue(), claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
