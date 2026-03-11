package com.klodit.soumission_service.client.dto;

import lombok.*;

/**
 * DTO représentant une pièce administrative telle que retournée
 * par le Service Documents (:8005).
 *
 * Correspondance avec la table pieces_administratives :
 * - id, soumission_id, document_id, type, designation
 * - is_valide (validé par la commission)
 * - date_expiration (validité temporelle du document)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PieceAdministrativeExterneDTO {
    private String id;
    private String soumissionId;
    private String documentId;
    private String type; // NIF, NIS, REGISTRE_COMMERCE, etc.
    private String designation;
    private Boolean isValide;
    private String dateExpiration;
}
