package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.request.CreateCautionRequest;
import com.klodit.soumission_service.dto.response.CautionResponse;
import com.klodit.soumission_service.entity.Caution;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutCaution;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.*;
import com.klodit.soumission_service.repository.CautionRepository;
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
public class CautionService {

    private final SoumissionRepository soumissionRepository;
    private final CautionRepository cautionRepository;
    private final MinIOService minIOService;
    private final AuditLogService auditLogService;
    private final MinIOProperties minIOProperties;

    /**
     * US-4 : Joindre la caution bancaire à une soumission.
     */
    @Transactional
    public CautionResponse ajouterCaution(
            String soumissionId, String operateurId,
            CreateCautionRequest request, MultipartFile scanCaution) {

        // Charger et valider la soumission
        Soumission soumission = soumissionRepository.findById(soumissionId)
                .orElseThrow(() -> new SoumissionNotFoundException(soumissionId));

        if (!soumission.getOperateurId().equals(operateurId)) {
            throw new AccesRefuseException(
                    "Accès refusé : vous n'êtes pas le propriétaire de cette soumission");
        }

        if (soumission.getStatut() != StatutSoumission.BROUILLON) {
            throw new IllegalStateException("La caution ne peut être ajoutée qu'en statut BROUILLON");
        }

        // Si une caution existe déjà, on la supprime (réécriture lors d'un retry)
        cautionRepository.findBySoumissionId(soumissionId).ifPresent(c -> {
            cautionRepository.delete(c);
            cautionRepository.flush();
        });

        // Valider les dates
        if (request.getDateExpiration().isBefore(java.time.LocalDateTime.now())) {
            throw new FichierInvalideException(
                    "La date d'expiration de la caution est déjà passée");
        }

        try {
            // Upload du scan de la caution vers MinIO
            String bucket = minIOProperties.getBucket().getCautions();
            String fichierUrl = minIOService.uploadFichier(scanCaution, bucket, soumissionId);

            Caution caution = Caution.builder()
                    .soumission(soumission)
                    .compteBancaireId(request.getCompteBancaireId())
                    .reference(request.getReference())
                    .dateExpiration(request.getDateExpiration())
                    .statut(StatutCaution.VALIDE)
                    .fichierUrl(fichierUrl)
                    .build();

            caution = cautionRepository.save(caution);
            log.info("Caution enregistrée — ID: {}, compteBancaireId: {}, reference: {}",
                    caution.getId(), caution.getCompteBancaireId(), caution.getReference());

            auditLogService.logDepot(soumissionId, operateurId, "CAUTION", true,
                    "Compte: " + request.getCompteBancaireId() + ", Réf: " + request.getReference());

            return toResponse(caution);

        } catch (OffreDejaDeposeeException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            auditLogService.logDepot(soumissionId, operateurId, "CAUTION",
                    false, "Erreur: " + e.getMessage());
            throw new FichierInvalideException("Erreur lors du dépôt de la caution : " + e.getMessage());
        }
    }

    /**
     * Consulter la caution d'une soumission.
     */
    public CautionResponse getCaution(String soumissionId) {
        Caution caution = cautionRepository.findBySoumissionId(soumissionId)
                .orElseThrow(() -> new RessourceIntrouvableException(
                        "Caution", "soumission " + soumissionId));
        return toResponse(caution);
    }

    private CautionResponse toResponse(Caution c) {
        return CautionResponse.builder()
                .id(c.getId())
                .compteBancaireId(c.getCompteBancaireId())
                .reference(c.getReference())
                .dateExpiration(c.getDateExpiration())
                .statut(c.getStatut())
                .fichierUrl(c.getFichierUrl())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
