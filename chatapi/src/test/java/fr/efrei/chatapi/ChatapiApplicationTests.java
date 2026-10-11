package fr.efrei.chatapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Vérifie que le contexte Spring démarre avec la configuration de test (H2 en mémoire).
@SpringBootTest
@ActiveProfiles("test")
class ChatapiApplicationTests {

    @Test
    void contextLoads() {
    }
}
