package com.klodit.soumission_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

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
}
