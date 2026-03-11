package com.klodit.soumission_service.messaging.event;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoumissionDeposeeEvent {
    private String soumissionId;
    private String reference;
    private String appelOffreId;
    private String operateurId;
    private String lotId;
    private LocalDateTime horodatageServeur;
    private Boolean isDansDelai;
}
