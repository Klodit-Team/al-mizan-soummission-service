package com.klodit.soumission_service.messaging.event;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Événement accusé de réception.
 * Publié quand le système confirme la bonne réception d'une soumission.
 * Consommé par : Service Notifications (email + notification mobile),
 *                Service Audit (traçabilité).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SoumissionRecueEvent {
    private String soumissionId;
    private String reference;
    private String appelOffreId;
    private String operateurId;
    private LocalDateTime horodatageReception;
    private String accuseReceptionRef;    // Référence unique de l'accusé
}
