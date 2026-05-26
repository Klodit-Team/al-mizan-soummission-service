package com.klodit.soumission_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

/**
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepotOffreFinanciereRequest {
    // hash calculé côté client sur le ciphertext (optionnel — le serveur recalcule
    // toujours)
    private String hashClient;

    // Signature ECDSA P-384 (Base64) sur le hash du ciphertext — OBLIGATOIRE
    // (non-répudiation)
    @NotBlank(message = "La signature ECDSA est obligatoire (non-répudiation)")
    private String signatureEcdsa;

    // Clé publique ECDSA P-384 PEM de l'opérateur économique — OBLIGATOIRE
    @NotBlank(message = "La clé publique ECDSA de l'opérateur est obligatoire")
    private String clePubliqueEcdsaPem;

    @NotEmpty(message = "Les lignes de l'offre financière sont obligatoires")
    private List<LigneOffreRequest> lignes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LigneOffreRequest {
        @NotBlank(message = "L'identifiant de l'article est obligatoire")
        private String articleId;

        @NotNull(message = "Le prix unitaire est obligatoire")
        @DecimalMin(value = "0.00", message = "Le prix unitaire doit être supérieur ou égal à 0")
        private java.math.BigDecimal prixUnitaire;

        // Colonnes descriptives interdites
        private String designation;
        private java.math.BigDecimal quantite;
        private String unite;
    }
}
