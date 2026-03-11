package com.klodit.soumission_service.service;

import com.klodit.soumission_service.crypto.ShamirSecretSharing;
import com.klodit.soumission_service.dto.response.CleChiffrementResponse;
import com.klodit.soumission_service.entity.CleChiffrement;
import com.klodit.soumission_service.entity.FragmentCle;
import com.klodit.soumission_service.enums.StatutCle;
import com.klodit.soumission_service.exception.ChiffrementException;
import com.klodit.soumission_service.repository.CleChiffrementRepository;
import com.klodit.soumission_service.repository.FragmentCleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service de gestion du cycle de vie des clés de chiffrement par Appel
 * d'Offres.
 *
 * Implémente le schéma Shamir Secret Sharing (K-of-N) :
 * - N fragments distribués aux membres de la commission
 * - K fragments suffisent pour reconstituer la clé privée
 * - Aucun membre seul ne peut ouvrir les plis
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CleChiffrementService {

    private final CleChiffrementRepository cleChiffrementRepository;
    private final FragmentCleRepository fragmentCleRepository;
    private final ChiffrementService chiffrementService;

    @Value("${chiffrement.shamir.total-shares:5}")
    private int totalShares;

    @Value("${chiffrement.shamir.threshold:3}")
    private int threshold;

    /**
     * US-6 : Génère une paire de clés RSA-4096 pour un appel d'offres.
     * La clé privée est fragmentée en N parts (Shamir K-of-N).
     * Chaque fragment est distribué à un membre de la commission.
     *
     * @param appelOffreId         ID de l'appel d'offres
     * @param membresCommissionIds liste des IDs des membres de la commission
     *                             (taille = N)
     */
    @Transactional
    public CleChiffrementResponse genererCles(String appelOffreId, List<String> membresCommissionIds) {
        log.info("Génération des clés pour AO: {} — membres: {}", appelOffreId, membresCommissionIds.size());

        // Vérifier qu'aucune clé n'existe déjà pour cet AO
        cleChiffrementRepository.findByAppelOffreId(appelOffreId).ifPresent(c -> {
            throw new IllegalStateException("Des clés existent déjà pour l'appel d'offres : " + appelOffreId);
        });

        if (membresCommissionIds.size() < totalShares) {
            throw new com.klodit.soumission_service.exception.FichierInvalideException(
                    "Nombre de membres insuffisant. Requis: " + totalShares
                            + ", fournis: " + membresCommissionIds.size());
        }

        // 1. Générer la paire de clés RSA-4096
        KeyPair keyPair = chiffrementService.genererPaireClésRSA();
        String clePubliquePem = chiffrementService.encoderClePubliqueEnPem(keyPair.getPublic());
        byte[] clePriveeBytes = keyPair.getPrivate().getEncoded();

        // 2. Fragmenter la clé privée avec le VRAI schéma Shamir GF(256)
        ShamirSecretSharing shamir = new ShamirSecretSharing(totalShares, threshold);
        Map<Integer, byte[]> fragments = shamir.split(clePriveeBytes);

        // 3. Persister la clé de chiffrement
        CleChiffrement cleChiffrement = CleChiffrement.builder()
                .appelOffreId(appelOffreId)
                .clePublique(clePubliquePem)
                .clePriveeChiffree(null) // La clé privée N'EST PAS stockée en clair
                .statut(StatutCle.ACTIVE)
                .build();

        cleChiffrement = cleChiffrementRepository.save(cleChiffrement);

        // 4. Persister les fragments (un par membre de la commission)
        List<FragmentCle> fragmentsEntites = new ArrayList<>();
        int memberIndex = 0;
        for (Map.Entry<Integer, byte[]> entry : fragments.entrySet()) {
            FragmentCle fragment = FragmentCle.builder()
                    .cleChiffrement(cleChiffrement)
                    .membreCommissionId(membresCommissionIds.get(memberIndex++))
                    .fragmentIndex(entry.getKey()) // index Shamir
                    .fragmentChiffre(Base64.getEncoder().encodeToString(entry.getValue()))
                    .estSoumis(false)
                    .build();
            fragmentsEntites.add(fragment);
        }
        fragmentCleRepository.saveAll(fragmentsEntites);

        // 5. Effacer la clé privée de la mémoire
        java.util.Arrays.fill(clePriveeBytes, (byte) 0);

        log.info("Clés générées (Shamir GF(256) {}-of-{}) — AO: {}, ID: {}",
                threshold, totalShares, appelOffreId, cleChiffrement.getId());

        return toResponse(cleChiffrement);
    }

    /**
     * Récupérer la clé publique d'un AO (pour le chiffrement côté client).
     */
    public CleChiffrementResponse getClePublique(String appelOffreId) {
        CleChiffrement cle = cleChiffrementRepository
                .findByAppelOffreIdAndStatut(appelOffreId, StatutCle.ACTIVE)
                .orElseThrow(() -> new com.klodit.soumission_service.exception.RessourceIntrouvableException(
                        "Clé de chiffrement active", "appel d'offres " + appelOffreId));
        return toResponse(cle);
    }

    /**
     * Reconstitue la clé privée à partir des fragments soumis (Shamir K-of-N).
     * Utilisé lors du déchiffrement des offres (ouverture des plis).
     *
     * @param appelOffreId ID de l'AO
     * @param fragments    liste des fragments soumis (index + valeur base64)
     * @return la clé privée RSA reconstituée
     */
    @Transactional
    public PrivateKey reconstituerClePrivee(String appelOffreId,
            List<com.klodit.soumission_service.dto.request.DechiffrementRequest.FragmentSoumis> fragmentsSoumis) {

        CleChiffrement cle = cleChiffrementRepository.findByAppelOffreId(appelOffreId)
                .orElseThrow(() -> new com.klodit.soumission_service.exception.RessourceIntrouvableException(
                        "Clé de chiffrement", "appel d'offres " + appelOffreId));

        if (cle.getStatut() == StatutCle.UTILISEE) {
            throw new ChiffrementException("Les clés de cet AO ont déjà été utilisées (ouverture faite)");
        }

        if (fragmentsSoumis.size() < threshold) {
            throw new ChiffrementException(
                    "Fragments insuffisants. Seuil requis: " + threshold
                            + ", fournis: " + fragmentsSoumis.size());
        }

        // Vérifier et marquer les fragments comme soumis
        List<FragmentCle> entityFragments = fragmentCleRepository
                .findByCleChiffrementId(cle.getId());

        for (var fragmentSoumis : fragmentsSoumis) {
            entityFragments.stream()
                    .filter(f -> f.getFragmentIndex().equals(fragmentSoumis.getIndex()))
                    .findFirst()
                    .ifPresent(f -> {
                        f.setEstSoumis(true);
                        f.setDateSoumission(java.time.LocalDateTime.now());
                        fragmentCleRepository.save(f);
                    });
        }

        // Reconstituer via Shamir GF(256) — seuls K fragments suffisent
        ShamirSecretSharing shamir = new ShamirSecretSharing(totalShares, threshold);
        Map<Integer, byte[]> parts = new HashMap<>();
        for (var fs : fragmentsSoumis) {
            parts.put(fs.getIndex(), Base64.getDecoder().decode(fs.getValeur()));
        }

        byte[] clePriveeBytes = shamir.join(parts);

        // Marquer la clé comme UTILISÉE
        cle.setStatut(StatutCle.UTILISEE);
        cle.setDateUtilisation(java.time.LocalDateTime.now());
        cleChiffrementRepository.save(cle);

        try {
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(clePriveeBytes);
            java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA",
                    org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME);
            PrivateKey privateKey = factory.generatePrivate(spec);

            // Effacer immédiatement la clé privée de la mémoire
            java.util.Arrays.fill(clePriveeBytes, (byte) 0);

            return privateKey;
        } catch (Exception e) {
            java.util.Arrays.fill(clePriveeBytes, (byte) 0);
            throw new ChiffrementException(
                    "Impossible de reconstituer la clé privée. Les fragments sont-ils corrects ? " + e.getMessage());
        }
    }

    private CleChiffrementResponse toResponse(CleChiffrement c) {
        return CleChiffrementResponse.builder()
                .id(c.getId())
                .appelOffreId(c.getAppelOffreId())
                .clePublique(c.getClePublique())
                .statut(c.getStatut())
                .dateGeneration(c.getDateGeneration())
                .dateUtilisation(c.getDateUtilisation())
                .build();
    }
}
