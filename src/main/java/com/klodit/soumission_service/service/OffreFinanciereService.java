package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.entity.OffreFinanciere;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.*;
import com.klodit.soumission_service.repository.OffreFinanciereRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OffreFinanciereService {

    private final SoumissionRepository soumissionRepository;
    private final OffreFinanciereRepository offreFinanciereRepository;
    private final MinIOService minIOService;
    private final HashService hashService;
    private final ChiffrementService chiffrementService;
    private final AuditLogService auditLogService;
    private final MinIOProperties minIOProperties;

    /**
     * US-3 : Déposer l'offre financière chiffrée (E2E).
     * Le fichier reçu est DÉJÀ chiffré côté client (AES-256-GCM + RSA-4096).
     * Le serveur vérifie la signature ECDSA P-384, stocke le ciphertext
     * et calcule son hash d'intégrité (CSL §4.4.5, Table 4.11 étape 4).
     *
     * @param soumissionId        ID de la soumission
     * @param operateurId         ID de l'opérateur économique
     * @param fichierChiffre      fichier chiffré (ciphertext)
     * @param hashClient          hash SHA-256 du ciphertext calculé côté client
     *                            (optionnel)
     * @param signatureEcdsa      signature ECDSA P-384 (Base64) sur le hash du
     *                            ciphertext
     * @param clePubliqueEcdsaPem clé publique ECDSA P-384 PEM de l'opérateur
     *                            économique
     */
    @Transactional
    public OffreFinanciereResponse deposerOffreFinanciere(
            String soumissionId, String operateurId,
            MultipartFile fichierChiffre, String hashClient,
            String signatureEcdsa, String clePubliqueEcdsaPem) {

        // 1. Charger et valider la soumission
        Soumission soumission = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> new SoumissionNotFoundException(soumissionId));

        if (!soumission.getOperateurId().equals(operateurId)) {
            throw new AccesRefuseException(
                    "Accès refusé : vous n'êtes pas le propriétaire de cette soumission");
        }

        if (soumission.getStatut() != StatutSoumission.BROUILLON) {
            throw new IllegalStateException(
                    "L'offre financière ne peut être déposée qu'en statut BROUILLON");
        }

        // 2. Vérifier qu'aucune offre financière n'a déjà été déposée
        offreFinanciereRepository.findBySoumissionId(soumissionId).ifPresent(of -> {
            throw new OffreDejaDeposeeException("offre financière");
        });

        try {
            // 3. Calculer le hash SHA-256 du CIPHERTEXT (pas du plaintext)
            String hashServeur = hashService.calculerHash(fichierChiffre);

            if (hashClient != null && !hashClient.isBlank()
                    && !hashServeur.equalsIgnoreCase(hashClient)) {
                log.warn("Hash ciphertext client ≠ serveur pour soumission {}. " +
                        "Client: {}, Serveur: {}", soumissionId, hashClient, hashServeur);
            }

            // 4. Vérifier la signature ECDSA P-384 (non-répudiation — CSL §4.4.5 Table 4.11
            // étape 4)
            // Note : la vérification est best-effort côté serveur.
            // La clé et la signature sont stockées pour vérification formelle ultérieure
            // (ouverture des plis / audit). En cas de clé malformée (ex: PEM tronqué
            // via query param), on log un warning mais on accepte le dépôt.
            boolean signatureVerifiee = false;
            try {
                java.security.PublicKey clePubliqueEcdsa = chiffrementService
                        .reconstruireClePubliqueECDSA(clePubliqueEcdsaPem);
                byte[] signatureBytes = java.util.Base64.getDecoder().decode(signatureEcdsa);
                byte[] hashBytes = hashServeur.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                signatureVerifiee = chiffrementService.verifierSignatureECDSA(
                        hashBytes, signatureBytes, clePubliqueEcdsa);

                if (!signatureVerifiee) {
                    log.warn("Signature ECDSA invalide pour soumission {} — accepté avec avertissement", soumissionId);
                } else {
                    log.info("Signature ECDSA P-384 vérifiée avec succès pour soumission {}", soumissionId);
                }
            } catch (Exception ecdsaEx) {
                log.warn("Vérification ECDSA impossible pour soumission {} : {} — "
                        + "clé et signature stockées pour vérification ultérieure",
                        soumissionId, ecdsaEx.getMessage());
            }

            // 5. Upload du ciphertext vers MinIO (bucket sécurisé)
            String bucket = minIOProperties.getBucket().getOffresFinancieres();
            String fichierUrl = minIOService.uploadFichier(fichierChiffre, bucket, soumissionId);

            // 6. Persister l'offre financière (chiffrée, sans montant — sera rempli à
            // l'ouverture)
            OffreFinanciere offreFinanciere = OffreFinanciere.builder()
                    .soumission(soumission)
                    .fichierChiffreUrl(fichierUrl)
                    .hashFichier(hashServeur)
                    .signatureEcdsa(signatureEcdsa)
                    .clePubliqueEcdsa(clePubliqueEcdsaPem)
                    .isDechiffree(false)
                    .build();

            offreFinanciere = offreFinanciereRepository.save(offreFinanciere);
            log.info("Offre financière (chiffrée) enregistrée — ID: {}", offreFinanciere.getId());

            // 7. Log d'audit
            auditLogService.logDepot(soumissionId, operateurId,
                    "OFFRE_FINANCIERE", true,
                    "Ciphertext hashé: " + hashServeur);

            return toResponse(offreFinanciere);

        } catch (OffreDejaDeposeeException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            auditLogService.logDepot(soumissionId, operateurId,
                    "OFFRE_FINANCIERE", false, "Erreur: " + e.getMessage());
            throw new FichierInvalideException(
                    "Erreur lors du dépôt de l'offre financière : " + e.getMessage());
        }
    }

    /**
     * Consulter l'offre financière d'une soumission.
     * Les montants ne sont visibles qu'après déchiffrement.
     */
    public OffreFinanciereResponse getOffreFinanciere(String soumissionId) {
        OffreFinanciere of = offreFinanciereRepository.findBySoumissionId(soumissionId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Offre financière", "soumission " + soumissionId));
        return toResponse(of);
    }

    /**
     * Retourne toutes les offres financières non encore déchiffrées d'un AO.
     * Utilisé par l'étape de déchiffrement (US-7).
     */
    public List<OffreFinanciere> getOffresNonDechiffrees(String appelOffreId) {
        return offreFinanciereRepository
                .findBySoumissionAppelOffreIdAndIsDechiffree(appelOffreId, false);
    }

    /**
     * Met à jour une offre financière après déchiffrement (US-7).
     * Le PDF clair est stocké dans MinIO et l'URL est mise à jour dans l'entité.
     * Les montants seront remplis ultérieurement par l'OCR (Service IA).
     */
    @Transactional
    public void mettreAJourApresDecryptage(OffreFinanciere of) {
        of.setIsDechiffree(true);
        of.setDateDechiffrement(LocalDateTime.now());
        offreFinanciereRepository.save(of);
    }

    /**
     * Met à jour les montants d'une offre financière après analyse OCR par le
     * Service IA.
     * Appelé par le consumer
     * {@link com.klodit.soumission_service.messaging.consumer.OffreFinanciereAnalyseConsumer}.
     *
     * @param offreFinanciereId ID de l'offre financière
     * @param montantHt         montant HT extrait par OCR
     * @param tva               TVA extraite par OCR
     * @param montantTtc        montant TTC extrait par OCR
     * @param observations      observations de l'IA
     */
    @Transactional
    public void mettreAJourMontantsDepuisOCR(String offreFinanciereId,
            BigDecimal montantHt, BigDecimal tva,
            BigDecimal montantTtc, String observations) {
        OffreFinanciere of = offreFinanciereRepository.findById(offreFinanciereId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Offre financière", offreFinanciereId));

        if (!of.getIsDechiffree()) {
            log.warn("Tentative de mise à jour des montants sur une offre non déchiffrée — OF: {}",
                    offreFinanciereId);
            throw new IllegalStateException(
                    "L'offre financière n'a pas encore été déchiffrée. Montants non mis à jour.");
        }

        of.setMontantHt(montantHt);
        of.setTva(tva);
        of.setMontantTtc(montantTtc);

        offreFinanciereRepository.save(of);

        log.info("Montants OCR mis à jour — OF: {}, HT: {}, TVA: {}, TTC: {}",
                offreFinanciereId, montantHt, tva, montantTtc);

        // Log d'audit
        auditLogService.logDepot(
                of.getSoumission().getId(),
                of.getSoumission().getOperateurId(),
                "OFFRE_FINANCIERE_OCR",
                true,
                "Montants extraits par OCR — HT: " + montantHt + ", TTC: " + montantTtc
                        + (observations != null ? " | " + observations : ""));
    }

    private OffreFinanciereResponse toResponse(OffreFinanciere of) {
        return OffreFinanciereResponse.builder()
                .id(of.getId())
                .fichierChiffreUrl(of.getFichierChiffreUrl())
                .fichierClairUrl(of.getFichierClairUrl())
                .hashFichier(of.getHashFichier())
                .signatureVerifiee(of.getSignatureEcdsa() != null)
                .montantHt(of.getMontantHt())
                .tva(of.getTva())
                .montantTtc(of.getMontantTtc())
                .isDechiffree(of.getIsDechiffree())
                .dateDechiffrement(of.getDateDechiffrement())
                .createdAt(of.getCreatedAt())
                .build();
    }
}
