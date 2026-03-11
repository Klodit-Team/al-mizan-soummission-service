package com.klodit.soumission_service.messaging.event;

import lombok.*;

/**
 * Événement reçu du Service IA lorsque l'analyse OCR d'une offre technique est
 * terminée.
 * Met à jour le champ is_conforme de l'offre technique.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IaAnalyseTermineeEvent {
    private String soumissionId;
    private String offreTechniqueId;
    private Boolean isConforme;
    private Double scoreOcr; // ex: 92.5 (précision OCR en %)
    private String observations;
    private String timestamp;
}
