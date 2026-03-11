package com.klodit.soumission_service.messaging.event;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Événement d'audit typé envoyé au Service Audit (:8009) via RabbitMQ.
 * Remplace les Map<String,Object> non typées pour garantir la type-safety.
 * Types possibles : DEPOT_TENTATIVE, SOUMISSION_VALIDATION, DECHIFFREMENT_PLIS.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {
    private String type; // DEPOT_TENTATIVE | SOUMISSION_VALIDATION | DECHIFFREMENT_PLIS
    private String soumissionId;
    private String appelOffreId;
    private String operateurId;
    private String membreCommissionId;
    private String typeDepot; // OFFRE_TECHNIQUE | OFFRE_FINANCIERE | CAUTION (si type = DEPOT_TENTATIVE)
    private String ipDepot;
    private Boolean succes;
    private String horodatage;
    private String details;
    private int nombreOffres;
    private LocalDateTime timestamp;
}
