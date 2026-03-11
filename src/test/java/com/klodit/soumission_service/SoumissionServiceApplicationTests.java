package com.klodit.soumission_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test d'intégration — nécessite MySQL, Redis et RabbitMQ actifs.
 * Désactivé par défaut en CI sans infrastructure.
 * Lancer manuellement après `docker compose up -d`.
 */
@SpringBootTest
@ActiveProfiles("dev")
@org.junit.jupiter.api.Disabled("Nécessite l'infrastructure Docker (MySQL, Redis, RabbitMQ). Lancer manuellement après docker compose up -d")
class SoumissionServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
