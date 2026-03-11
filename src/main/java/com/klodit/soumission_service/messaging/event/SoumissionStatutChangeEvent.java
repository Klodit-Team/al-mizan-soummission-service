package com.klodit.soumission_service.messaging.event;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Événement changement de statut.
 * Publié à chaque transition dans le workflow de la soumission.
 * Consommé par : Service Notifications, Service Audit.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoumissionStatutChangeEvent {
    private String soumissionId;
    private String reference;
    private String appelOffreId;
    private String ancienStatut;
    private String nouveauStatut;
    private LocalDateTime horodatage;
    private String declenchePar; // ID de l'utilisateur ou "SYSTEM"
}
