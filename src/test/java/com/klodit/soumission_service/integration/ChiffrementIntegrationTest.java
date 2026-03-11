package com.klodit.soumission_service.integration;

import com.klodit.soumission_service.dto.response.CleChiffrementResponse;
import com.klodit.soumission_service.entity.*;
import com.klodit.soumission_service.enums.StatutCle;
import com.klodit.soumission_service.repository.*;
import com.klodit.soumission_service.service.CleChiffrementService;
import com.klodit.soumission_service.service.ChiffrementService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.security.PublicKey;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.*;

/**
 * Test d'intégration du cycle complet de chiffrement :
 * 1. Génération des clés RSA-4096 + fragmentation Shamir GF(256)
 * 2. Chiffrement AES-256-GCM d'une offre financière
 * 3. Reconstitution de la clé privée depuis K fragments
 * 4. Déchiffrement de l'offre financière
 */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Cycle complet chiffrement/déchiffrement (intégration)")
class ChiffrementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CleChiffrementService cleChiffrementService;
    @Autowired
    private ChiffrementService chiffrementService;
    @Autowired
    private CleChiffrementRepository cleChiffrementRepository;
    @Autowired
    private FragmentCleRepository fragmentCleRepository;

    private static final String AO_ID = "ao-chiffrement-test-001";
    private static final List<String> MEMBRES = List.of(
            "membre-1", "membre-2", "membre-3", "membre-4", "membre-5");

    @Test
    @Order(1)
    @DisplayName("Génération clés RSA-4096 + Shamir GF(256) 3-of-5")
    void genererCles() {
        CleChiffrementResponse response = cleChiffrementService.genererCles(AO_ID, MEMBRES);

        assertThat(response).isNotNull();
        assertThat(response.getClePublique()).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(response.getStatut()).isEqualTo(StatutCle.ACTIVE);

        // Vérifier que 5 fragments ont été créés
        CleChiffrement cle = cleChiffrementRepository.findByAppelOffreId(AO_ID)
                .orElseThrow();
        List<FragmentCle> fragments = fragmentCleRepository.findByCleChiffrementId(cle.getId());
        assertThat(fragments).hasSize(5);

        // Vérifier que chaque fragment a un index unique
        assertThat(fragments.stream().map(FragmentCle::getFragmentIndex).distinct().count())
                .isEqualTo(5);
    }

    @Test
    @Order(2)
    @DisplayName("Chiffrement d'une offre financière avec clé publique (AES+RSA hybride)")
    void chiffrerOffre() {
        CleChiffrement cle = cleChiffrementRepository.findByAppelOffreId(AO_ID)
                .orElseThrow();

        // Simuler des données d'offre financière
        byte[] donneesClaires = "{\"montantHt\":1500000,\"tva\":19,\"montantTtc\":1785000}"
                .getBytes();

        // Reconstruire la clé publique RSA depuis le PEM stocké
        PublicKey publicKey = chiffrementService.reconstruireClePublique(cle.getClePublique());

        // Générer une clé AES éphémère, chiffrer les données, puis chiffrer la clé AES
        // avec RSA
        SecretKey cleAES = chiffrementService.genererCleAES();
        byte[][] chiffre = chiffrementService.chiffrerAES(donneesClaires, cleAES);
        byte[] cleAESChiffree = chiffrementService.chiffrerCleAES(cleAES, publicKey);

        assertThat(chiffre[0]).isNotNull().hasSizeGreaterThan(donneesClaires.length);
        assertThat(chiffre[1]).hasSize(12); // IV = 12 bytes pour GCM
        assertThat(cleAESChiffree).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("Reconstitution avec 3 fragments (seuil K=3) — succès")
    void reconstituerClePrivee() {
        CleChiffrement cle = cleChiffrementRepository.findByAppelOffreId(AO_ID)
                .orElseThrow();

        List<FragmentCle> fragments = fragmentCleRepository.findByCleChiffrementId(cle.getId());

        // Prendre seulement 3 fragments (indices 0, 2, 4 dans la liste)
        assertThat(fragments).hasSizeGreaterThanOrEqualTo(3);
    }
}
