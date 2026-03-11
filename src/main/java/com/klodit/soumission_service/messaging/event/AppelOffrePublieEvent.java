package com.klodit.soumission_service.messaging.event;

import lombok.*;
import java.util.List;

/**
 * Événement reçu du Service Appel d'Offres quand un AO est publié.
 * Déclenche la génération automatique des clés de chiffrement (RSA-4096 +
 * Shamir).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppelOffrePublieEvent {
    private String appelOffreId;
    private String titre;
    private List<String> membresCommissionIds;
    private String timestamp;
}
