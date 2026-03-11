package com.klodit.soumission_service.dto.response;

import com.klodit.soumission_service.enums.StatutCle;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleChiffrementResponse {
    private String id;
    private String appelOffreId;
    private String clePublique; // PEM RSA-4096 (retourné pour le chiffrement côté client)
    private StatutCle statut;
    private LocalDateTime dateGeneration;
    private LocalDateTime dateUtilisation;
    // NB : clePriveeChiffree et fragments NON exposés dans la réponse
}
