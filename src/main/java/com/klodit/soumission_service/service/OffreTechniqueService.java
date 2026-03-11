package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.response.OffreTechniqueResponse;
import com.klodit.soumission_service.entity.OffreTechnique;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.*;
import com.klodit.soumission_service.messaging.event.SoumissionAnalyseDemandeEvent;
import com.klodit.soumission_service.messaging.publisher.SoumissionEventPublisher;
import com.klodit.soumission_service.repository.OffreTechniqueRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OffreTechniqueService {

    private final SoumissionRepository soumissionRepository;
    private final OffreTechniqueRepository offreTechniqueRepository;
    private final MinIOService minIOService;
    private final HashService hashService;
    private final AuditLogService auditLogService;
    private final SoumissionEventPublisher eventPublisher;
    private final MinIOProperties minIOProperties;

    /**
     * US-2 : Déposer l'offre technique d'une soumission.
     * - Upload du fichier vers MinIO
     * - Calcul du hash SHA-256 côté serveur
     * - Vérification double du hash si fourni par le client
     * - Publication événement OCR vers le Service IA
     *
     * @param soumissionId ID de la soumission
     * @param operateurId  ID de l'opérateur (extrait du header X-User-Id)
     * @param fichier      fichier multipart (PDF / ZIP max 50 Mo)
     * @param hashClient   hash SHA-256 calculé côté client (optionnel)
     */
    @Transactional
    public OffreTechniqueResponse deposerOffreTechnique(
            String soumissionId, String operateurId,
            MultipartFile fichier, String hashClient) {

        // 1. Charger et valider la soumission
        Soumission soumission = chargerSoumissionAvecDroits(soumissionId, operateurId);

        // 2. Vérifier que la soumission est en brouillon
        if (soumission.getStatut() != StatutSoumission.BROUILLON) {
            throw new IllegalStateException(
                    "L'offre technique ne peut être déposée qu'en statut BROUILLON. " +
                            "Statut actuel : " + soumission.getStatut());
        }

        // 3. Vérifier qu'aucune offre technique n'a déjà été déposée
        offreTechniqueRepository.findBySoumissionId(soumissionId).ifPresent(ot -> {
            throw new OffreDejaDeposeeException("offre technique");
        });

        try {
            // 4. Calculer le hash SHA-256 côté serveur (source de vérité légale)
            String hashServeur = hashService.calculerHash(fichier);

            // 5. Vérification optionnelle de cohérence avec le hash client
            if (hashClient != null && !hashClient.isBlank()
                    && !hashServeur.equalsIgnoreCase(hashClient)) {
                log.warn("Hash client ({}) ≠ hash serveur ({}) pour la soumission {}",
                        hashClient, hashServeur, soumissionId);
                // En production, on pourrait rejeter ici. En dev, on log et continue.
            }

            // 6. Upload vers MinIO
            String bucket = minIOProperties.getBucket().getOffresTechniques();
            String fichierUrl = minIOService.uploadFichier(fichier, bucket, soumissionId);

            // 7. Persister l'offre technique
            OffreTechnique offreTechnique = OffreTechnique.builder()
                    .soumission(soumission)
                    .fichierUrl(fichierUrl)
                    .hashFichier(hashServeur)
                    .isConforme(null) // sera mis à jour par le Service IA (OCR)
                    .build();

            offreTechnique = offreTechniqueRepository.save(offreTechnique);
            log.info("Offre technique enregistrée — ID: {}, Hash: {}", offreTechnique.getId(), hashServeur);

            // 8. Log d'audit (asynchrone)
            auditLogService.logDepot(soumissionId, operateurId,
                    "OFFRE_TECHNIQUE", true,
                    "Fichier: " + fichier.getOriginalFilename() + ", Hash: " + hashServeur);

            // 9. Publier événement OCR vers Service IA (asynchrone)
            eventPublisher.publierDemandeAnalyseOCR(
                    SoumissionAnalyseDemandeEvent.builder()
                            .soumissionId(soumissionId)
                            .offreTechniqueId(offreTechnique.getId())
                            .fichierUrl(fichierUrl)
                            .hashFichier(hashServeur)
                            .appelOffreId(soumission.getAppelOffreId())
                            .operateurId(operateurId)
                            .build());

            return toResponse(offreTechnique);

        } catch (OffreDejaDeposeeException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            auditLogService.logDepot(soumissionId, operateurId, "OFFRE_TECHNIQUE",
                    false, "Erreur: " + e.getMessage());
            throw new FichierInvalideException("Erreur lors du dépôt de l'offre technique : " + e.getMessage());
        }
    }

    /**
     * Consulter l'offre technique d'une soumission.
     */
    public OffreTechniqueResponse getOffreTechnique(String soumissionId) {
        OffreTechnique ot = offreTechniqueRepository.findBySoumissionId(soumissionId)
                .orElseThrow(() -> new com.klodit.soumission_service.exception.RessourceIntrouvableException(
                        "Offre technique", "soumission " + soumissionId));
        return toResponse(ot);
    }

    /**
     * Mis à jour du statut de conformité par le Service IA (via consumer RabbitMQ).
     */
    @Transactional
    public void mettreAJourConformite(String offreTechniqueId, boolean isConforme, String observations) {
        OffreTechnique ot = offreTechniqueRepository.findById(offreTechniqueId)
                .orElseThrow(() -> new com.klodit.soumission_service.exception.RessourceIntrouvableException(
                        "Offre technique", offreTechniqueId));
        ot.setIsConforme(isConforme);
        ot.setObservations(observations);
        offreTechniqueRepository.save(ot);
        log.info("Conformité OCR mise à jour — OT: {}, conforme: {}", offreTechniqueId, isConforme);
    }

    // ── Helpers ────────────────────────────────────────────

    private Soumission chargerSoumissionAvecDroits(String soumissionId, String operateurId) {
        Soumission soumission = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> new SoumissionNotFoundException(soumissionId));
        if (!soumission.getOperateurId().equals(operateurId)) {
            throw new com.klodit.soumission_service.exception.AccesRefuseException(
                    "Accès refusé : vous n'êtes pas le propriétaire de cette soumission");
        }
        return soumission;
    }

    private OffreTechniqueResponse toResponse(OffreTechnique ot) {
        return OffreTechniqueResponse.builder()
                .id(ot.getId())
                .fichierUrl(ot.getFichierUrl())
                .hashFichier(ot.getHashFichier())
                .isConforme(ot.getIsConforme())
                .observations(ot.getObservations())
                .createdAt(ot.getCreatedAt())
                .build();
    }
}
