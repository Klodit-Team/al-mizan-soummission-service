package com.klodit.soumission_service.dto.request;

import lombok.*;

/**
 * La requête est multipart/form-data :
 * - fichier : MultipartFile (géré directement dans le contrôleur
 * via @RequestParam)
 * - hashClient : hash SHA-256 calculé côté client (optionnel, pour double
 * vérification)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepotOffreTechniqueRequest {
    // hash calculé côté client (optionnel — le serveur recalcule toujours)
    private String hashClient;
}
