package com.klodit.soumission_service.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValiderSoumissionRequest {
    // L'IP est extraite automatiquement de la requête HTTP
    // L'horodatage est généré côté serveur
    // Ce DTO peut être enrichi si nécessaire
    private boolean confirmationLegale; // L'OE confirme la soumission définitive
}
