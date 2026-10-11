package fr.efrei.chatapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Test unitaire sans Spring : la fenêtre glissante est vérifiée seule, avec une fenêtre courte.
class RateLimiterTests {

    private static final long FENETRE_MS = 300;

    private final RateLimiter rateLimiter = new RateLimiter(3, FENETRE_MS);

    @Test
    void refuseAuDelaDeLaLimitePuisAutoriseQuandLaFenetreAGlisse() throws InterruptedException {
        assertThat(rateLimiter.autoriser(1L)).isTrue();
        assertThat(rateLimiter.autoriser(1L)).isTrue();
        assertThat(rateLimiter.autoriser(1L)).isTrue();
        assertThat(rateLimiter.autoriser(1L)).isFalse();

        Thread.sleep(FENETRE_MS + 50);
        assertThat(rateLimiter.autoriser(1L)).isTrue();
    }

    @Test
    void chaqueUtilisateurASaPropreLimite() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.autoriser(1L);
        }
        assertThat(rateLimiter.autoriser(1L)).isFalse();
        assertThat(rateLimiter.autoriser(2L)).isTrue();
    }
}
