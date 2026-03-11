package com.klodit.soumission_service.client.dto;

import lombok.*;

/**
 * DTO représentant un utilisateur tel que retourné par le Service Utilisateurs
 * (:8002).
 * Seuls les champs nécessaires au Soumission Service sont mappés.
 *
 * Correspondance avec les tables des services :
 * - auth.users : isActive (is_active)
 * - profiles : nom, prenom
 * - roles (via user_roles) : role
 * - organisations : denomination, nif, email, isVerifie (is_verified)
 * - operateurs_economiques : isEligible (is_eligible), isBlacklisted
 * (is_blacklisted)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisateurExterneDTO {
    private String id;
    private String nom;
    private String prenom;
    private String email;
    private String role; // OPERATEUR_ECONOMIQUE, SERVICE_CONTRACTANT, etc.
    private String nif; // organisations.nif
    private String denomination; // organisations.denomination
    private Boolean isActive; // auth.users.is_active
    private Boolean isEligible; // operateurs_economiques.is_eligible
    private Boolean isBlacklisted; // operateurs_economiques.is_blacklisted
    private Boolean isVerifie; // organisations.is_verified
}
