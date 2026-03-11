package com.klodit.soumission_service.controller;

import com.klodit.soumission_service.dto.response.ApiResponse;
import com.klodit.soumission_service.dto.response.OffreTechniqueResponse;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.OffreTechniqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/soumissions/{soumissionId}/offre-technique")
@RequiredArgsConstructor
@Tag(name = "Offres Techniques", description = "Dépôt et consultation des offres techniques")
public class OffreTechniqueController {

        private final OffreTechniqueService offreTechniqueService;
        private final RbacGuard rbacGuard;

        /**
         * US-2 : Uploader l'offre technique (PDF/ZIP — max 50 Mo)
         * Requête : multipart/form-data
         * - fichier : le fichier de l'offre technique
         * - hashClient : (optionnel) hash SHA-256 calculé côté client
         */
        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Déposer l'offre technique", description = "Upload le fichier de l'offre technique. " +
                        "Un hash SHA-256 est calculé côté serveur. " +
                        "L'analyse OCR est déclenchée automatiquement (Service IA).")
        public ResponseEntity<ApiResponse<OffreTechniqueResponse>> deposer(
                        @PathVariable String soumissionId,
                        @RequestParam("fichier") MultipartFile fichier,
                        @RequestParam(value = "hashClient", required = false) String hashClient,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE");
                String operateurId = rbacGuard.getUserId(httpServletRequest);
                OffreTechniqueResponse response = offreTechniqueService
                                .deposerOffreTechnique(soumissionId, operateurId, fichier, hashClient);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.ok(response, "Offre technique déposée — analyse OCR en cours"));
        }

        /**
         * Consulter l'offre technique d'une soumission.
         * Accessible au propriétaire ou à la commission/admin/contrôleur.
         */
        @GetMapping
        @Operation(summary = "Consulter l'offre technique", description = "Retourne les métadonnées de l'offre technique (hash, conformité OCR)")
        public ResponseEntity<ApiResponse<OffreTechniqueResponse>> getOffreTechnique(
                        @PathVariable String soumissionId,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE", "MEMBRE_COMMISSION", "ADMIN",
                                "CONTROLEUR");
                OffreTechniqueResponse response = offreTechniqueService.getOffreTechnique(soumissionId);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }
}
