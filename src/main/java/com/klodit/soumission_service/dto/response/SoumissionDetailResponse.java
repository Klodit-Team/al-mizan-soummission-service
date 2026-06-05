package com.klodit.soumission_service.dto.response;

import com.klodit.soumission_service.enums.StatutSoumission;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoumissionDetailResponse {
    private String id;
    private String appelOffreId;
    private String operateurId;
    private String lotId;
    private String reference;
    private StatutSoumission statut;
    private LocalDateTime horodatageServeur;
    private Boolean isElectronique;
    private Boolean isDansDelai;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Sous-objets inclus
    private OffreTechniqueResponse offreTechnique;
    private OffreFinanciereResponse offreFinanciere;
    private CautionResponse caution;
    private List<LigneOffreFinanciereResponse> lignesOffreFinanciere;
}
