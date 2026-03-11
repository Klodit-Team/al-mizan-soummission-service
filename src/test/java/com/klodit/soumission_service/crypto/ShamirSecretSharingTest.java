package com.klodit.soumission_service.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShamirSecretSharing — Tests GF(256) K-of-N")
class ShamirSecretSharingTest {

    @Test
    @DisplayName("5-of-5 — tous les fragments → secret reconstitué")
    void split_join_all_fragments() {
        ShamirSecretSharing shamir = new ShamirSecretSharing(5, 3);
        byte[] secret = "CLÉ PRIVÉE RSA TRÈS SECRÈTE".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret);
        assertThat(parts).hasSize(5);

        byte[] recovered = shamir.join(parts);
        assertThat(recovered).isEqualTo(secret);
    }

    @Test
    @DisplayName("3-of-5 — seuil minimum K=3 → secret reconstitué")
    void split_join_threshold_fragments() {
        ShamirSecretSharing shamir = new ShamirSecretSharing(5, 3);
        byte[] secret = "SECRET FINANCIER 1 200 000 DZD".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret);

        // Prendre seulement 3 fragments (indices 1, 3, 5)
        Map<Integer, byte[]> subset = new HashMap<>();
        subset.put(1, parts.get(1));
        subset.put(3, parts.get(3));
        subset.put(5, parts.get(5));

        byte[] recovered = shamir.join(subset);
        assertThat(recovered).isEqualTo(secret);
    }

    @Test
    @DisplayName("2-of-5 — en dessous du seuil K=3 → résultat incorrect")
    void split_join_below_threshold_returns_wrong_data() {
        ShamirSecretSharing shamir = new ShamirSecretSharing(5, 3);
        byte[] secret = "SECRET PROTÉGÉ".getBytes(StandardCharsets.UTF_8);

        Map<Integer, byte[]> parts = shamir.split(secret);

        // Prendre seulement 2 fragments (< K=3)
        Map<Integer, byte[]> subset = new HashMap<>();
        subset.put(1, parts.get(1));
        subset.put(4, parts.get(4));

        byte[] recovered = shamir.join(subset);
        assertThat(recovered).isNotEqualTo(secret); // données incorrectes
    }

    @Test
    @DisplayName("Fragments de grandes données (> 1 Ko)")
    void split_join_large_secret() {
        ShamirSecretSharing shamir = new ShamirSecretSharing(5, 3);
        // Simuler une clé RSA-4096 (~2400 bytes)
        byte[] largeSecret = new byte[2400];
        new java.security.SecureRandom().nextBytes(largeSecret);

        Map<Integer, byte[]> parts = shamir.split(largeSecret);
        byte[] recovered = shamir.join(parts);

        assertThat(recovered).isEqualTo(largeSecret);
    }

    @Test
    @DisplayName("K > N → IllegalArgumentException")
    void constructor_k_greater_than_n_throws() {
        assertThatThrownBy(() -> new ShamirSecretSharing(3, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seuil");
    }
}
