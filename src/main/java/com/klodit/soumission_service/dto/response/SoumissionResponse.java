package com.klodit.soumission_service.dto.response;

import com.klodit.soumission_service.enums.StatutSoumission;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoumissionResponse {
    private String id;
    private String appelOffreId;
    private String operateurId;
    private String lotId;
    private String reference;
    private StatutSoumission statut;
    private LocalDateTime horodatageServeur;
    private Boolean isElectronique;
    private Boolean isDansDelai;
    private String ipDepot;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
