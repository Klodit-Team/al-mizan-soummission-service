package com.klodit.soumission_service.crypto;

import com.codahale.shamir.Scheme;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Map;

/**
 * Implémentation du schéma Shamir Secret Sharing sur GF(256).
 *
 * Propriétés mathématiques :
 * - Le secret est encodé comme le terme constant d'un polynôme de degré K-1
 * sur le corps fini GF(2^8)
 * - N points (fragments) sont évalués sur ce polynôme
 * - Tout sous-ensemble de K points permet de retrouver le polynôme
 * (et donc le secret) par interpolation de Lagrange
 * - Tout sous-ensemble de moins de K points ne révèle AUCUNE information
 * sur le secret (sécurité information-théorique)
 *
 * Utilise la bibliothèque Codahale Shamir (https://github.com/codahale/shamir)
 * qui implémente l'algorithme sur GF(256) avec des coefficients aléatoires.
 */
@Slf4j
public class ShamirSecretSharing {

    private final Scheme scheme;

    /**
     * Crée un schéma Shamir K-of-N.
     *
     * @param n nombre total de fragments à générer
     * @param k seuil minimum de fragments pour reconstituer le secret
     */
    public ShamirSecretSharing(int n, int k) {
        if (k > n) {
            throw new IllegalArgumentException(
                    "Le seuil K (" + k + ") ne peut pas dépasser le total N (" + n + ")");
        }
        if (k < 2) {
            throw new IllegalArgumentException(
                    "Le seuil K (" + k + ") doit être ≥ 2 pour garantir la sécurité");
        }
        this.scheme = new Scheme(new SecureRandom(), n, k);
        log.info("Schéma Shamir initialisé — N={}, K={}", n, k);
    }

    /**
     * Fragmente un secret en N parts Shamir.
     *
     * @param secret le secret à fragmenter (clé privée RSA encodée)
     * @return Map&lt;index, fragment&gt; — chaque index est unique (1..N)
     */
    public Map<Integer, byte[]> split(byte[] secret) {
        Map<Integer, byte[]> parts = scheme.split(secret);
        log.debug("Secret fragmenté en {} parts", parts.size());
        return parts;
    }

    /**
     * Reconstitue le secret à partir de K fragments minimum.
     *
     * @param parts Map&lt;index, fragment&gt; — au moins K fragments requis
     * @return le secret original reconstitué
     * @throws IllegalArgumentException si le nombre de fragments est insuffisant
     */
    public byte[] join(Map<Integer, byte[]> parts) {
        byte[] secret = scheme.join(parts);
        log.debug("Secret reconstitué à partir de {} fragments", parts.size());
        return secret;
    }
}
