package com.klodit.soumission_service.client.dto;

import lombok.*;
import java.time.LocalDateTime;

/**
 * DTO représentant un Appel d'Offres tel que retourné par le Service AO
 * (:8003).
 * Seuls les champs utiles au Soumission Service sont mappés.
 *
 * Correspondance avec la table appels_offres du Service AO :
 * - statut : BROUILLON, PUBLIE, EN_COURS, OUVERTURE_PLIS, EVALUATION, ATTRIBUE,
 * ANNULE, INFRUCTUEUX
 * - type_appel : OUVERT, RESTREINT, GRE_A_GRE, CONSULTATION
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppelOffreExterneDTO {
    private String id;
    private String reference;
    private String statut;
    private LocalDateTime dateLimiteDepot;
    private LocalDateTime dateOuverturePlis;
    private String typeAppel; // OUVERT, RESTREINT, GRE_A_GRE, CONSULTATION
    private Boolean cautionRequise;
}
