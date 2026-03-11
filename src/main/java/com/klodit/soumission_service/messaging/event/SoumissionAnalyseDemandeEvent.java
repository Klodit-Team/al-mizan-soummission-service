package com.klodit.soumission_service.messaging.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoumissionAnalyseDemandeEvent {
    private String soumissionId;
    private String offreTechniqueId;
    private String fichierUrl; // URL MinIO du fichier à analyser (OCR)
    private String hashFichier; // Pour vérifier l'intégrité après transport
    private String appelOffreId;
    private String operateurId;
}
