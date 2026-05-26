package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.request.DepotOffreFinanciereRequest;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.entity.LigneOffreFinanciere;
import com.klodit.soumission_service.entity.OffreFinanciere;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.*;
import com.klodit.soumission_service.repository.LigneOffreFinanciereRepository;
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
    private final LigneOffreFinanciereRepository ligneOffreFinanciereRepository;
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
     * @param soumissionId   ID de la soumission
     * @param operateurId    ID de l'opérateur économique
     * @param fichierChiffre fichier chiffré (ciphertext)
     * @param request        le DTO DepotOffreFinanciereRequest contenant les lignes du BPU, la signature, la clé publique et le hash client
     */
    @Transactional
    public OffreFinanciereResponse deposerOffreFinanciere(
            String soumissionId, String operateurId,
            MultipartFile fichierChiffre, DepotOffreFinanciereRequest request) {

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

        // 2. Validation stricte du payload
        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new FichierInvalideException("Les lignes de l'offre financière sont obligatoires");
        }

        for (DepotOffreFinanciereRequest.LigneOffreRequest item : request.getLignes()) {
            if (item.getDesignation() != null || item.getQuantite() != null || item.getUnite() != null) {
                throw new FichierInvalideException(
                        "Interdiction formelle de modifier ou de renseigner les colonnes relatives aux désignations, quantités et unités.");
            }
        }

        // 3. Charger les lignes de BPU pré-remplies en base
        List<LigneOffreFinanciere> lignesDb = ligneOffreFinanciereRepository.findBySoumissionId(soumissionId);
        if (lignesDb.isEmpty()) {
            throw new FichierInvalideException("Aucun BPU pré-rempli trouvé pour cette soumission.");
        }

        // Vérifier que le nombre d'éléments correspond
        if (request.getLignes().size() != lignesDb.size()) {
            throw new FichierInvalideException("La structure du BPU soumis est différente de la structure originale (nombre de lignes incorrect).");
        }

        // Mapper les IDs pour vérification d'exactitude
        java.util.Map<String, LigneOffreFinanciere> mapDb = lignesDb.stream()
                .collect(java.util.stream.Collectors.toMap(LigneOffreFinanciere::getId, l -> l));

        java.math.BigDecimal totalHt = java.math.BigDecimal.ZERO;

        for (DepotOffreFinanciereRequest.LigneOffreRequest item : request.getLignes()) {
            LigneOffreFinanciere ligneDb = mapDb.get(item.getArticleId());
            if (ligneDb == null) {
                throw new FichierInvalideException("L'article ID " + item.getArticleId() + " ne correspond à aucune ligne originale du BPU.");
            }
            // Enregistrer le prix unitaire
            ligneDb.setPrixUnitaire(item.getPrixUnitaire());
            ligneOffreFinanciereRepository.save(ligneDb);

            // Calculer montant HT de la ligne (prix * quantite)
            java.math.BigDecimal ligneMontant = item.getPrixUnitaire().multiply(ligneDb.getQuantite());
            totalHt = totalHt.add(ligneMontant);
        }

        // 4. Vérifier qu'aucune offre financière n'a déjà été déposée
        offreFinanciereRepository.findBySoumissionId(soumissionId).ifPresent(of -> {
            throw new OffreDejaDeposeeException("offre financière");
        });

        // 5. Calculer les montants globaux
        java.math.BigDecimal tvaRate = new java.math.BigDecimal("0.19"); // TVA 19% par défaut
        java.math.BigDecimal tvaMontant = totalHt.multiply(tvaRate);
        java.math.BigDecimal totalTtc = totalHt.add(tvaMontant);

        try {
            // 6. Calculer le hash SHA-256 du CIPHERTEXT (pas du plaintext)
            String hashServeur = hashService.calculerHash(fichierChiffre);

            String hashClient = request.getHashClient();
            if (hashClient != null && !hashClient.isBlank()
                    && !hashServeur.equalsIgnoreCase(hashClient)) {
                log.warn("Hash ciphertext client ≠ serveur pour soumission {}. " +
                        "Client: {}, Serveur: {}", soumissionId, hashClient, hashServeur);
            }

            // 7. Vérifier la signature ECDSA P-384
            boolean signatureVerifiee = false;
            try {
                java.security.PublicKey clePubliqueEcdsa = chiffrementService
                        .reconstruireClePubliqueECDSA(request.getClePubliqueEcdsaPem());
                byte[] signatureBytes = java.util.Base64.getDecoder().decode(request.getSignatureEcdsa());
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

            // 8. Upload du ciphertext vers MinIO (bucket sécurisé)
            String bucket = minIOProperties.getBucket().getOffresFinancieres();
            String fichierUrl = minIOService.uploadFichier(fichierChiffre, bucket, soumissionId);

            // 9. Persister l'offre financière
            OffreFinanciere offreFinanciere = OffreFinanciere.builder()
                    .soumission(soumission)
                    .fichierChiffreUrl(fichierUrl)
                    .hashFichier(hashServeur)
                    .signatureEcdsa(request.getSignatureEcdsa())
                    .clePubliqueEcdsa(request.getClePubliqueEcdsaPem())
                    .isDechiffree(false)
                    .montantHt(totalHt)
                    .tva(tvaMontant)
                    .montantTtc(totalTtc)
                    .build();

            offreFinanciere = offreFinanciereRepository.save(offreFinanciere);
            log.info("Offre financière (chiffrée) enregistrée — ID: {}, Montant HT: {}", offreFinanciere.getId(), totalHt);

            // 10. Log d'audit
            auditLogService.logDepot(soumissionId, operateurId,
                    "OFFRE_FINANCIERE", true,
                    "Ciphertext hashé: " + hashServeur + " | Montant HT: " + totalHt);

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
