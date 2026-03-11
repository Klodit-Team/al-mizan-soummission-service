package com.klodit.soumission_service.messaging.event;

import lombok.*;

/**
 * Événement envoyé au Service IA après déchiffrement d'une offre financière
 * lors de l'ouverture des plis.
 *
 * Le Service IA effectue l'OCR du PDF en clair pour extraire les montants
 * (montant HT, TVA, montant TTC) et les renvoie via
 * {@link OffreFinanciereAnalyseTermineeEvent}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreFinanciereAnalyseDemandeEvent {
    private String soumissionId;
    private String offreFinanciereId;
    private String fichierClairUrl; // URL MinIO du PDF déchiffré (en clair)
    private String hashFichierClair; // Hash SHA-256 du fichier en clair (intégrité)
    private String appelOffreId;
    private String operateurId;
}
