package com.klodit.soumission_service.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HashService — Tests unitaires")
class HashServiceTest {

    private final HashService hashService = new HashService();

    @Test
    @DisplayName("Calcul SHA-256 sur bytes — résultat déterministe")
    void calculerHash_bytes_deterministe() {
        byte[] data = "Offre financière confidentielle".getBytes();

        String hash1 = hashService.calculerHash(data);
        String hash2 = hashService.calculerHash(data);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("Deux contenus différents → hashes différents")
    void calculerHash_contenus_differents() {
        String hash1 = hashService.calculerHash("contenu A".getBytes());
        String hash2 = hashService.calculerHash("contenu B".getBytes());

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
