package com.klodit.soumission_service.messaging.event;

import lombok.*;
import java.math.BigDecimal;

/**
 * Événement reçu du Service IA lorsque l'analyse OCR d'une offre financière
 * (PDF en clair) est terminée.
 *
 * L'OCR extrait les montants du document PDF déchiffré et les renvoie
 * pour mise à jour en base de données.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreFinanciereAnalyseTermineeEvent {
    private String soumissionId;
    private String offreFinanciereId;
    private BigDecimal montantHt;
    private BigDecimal tva;
    private BigDecimal montantTtc;
    private Double scoreOcr; // Précision OCR en % (ex: 95.2)
    private String observations; // Observations/remarques de l'IA
    private String timestamp;
}
