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
        @Operation(summary = "Déposer l'offre technique", description = "Permet de déposer le scan physique de l'offre technique (PDF/ZIP, max 50 Mo). Calcule son hash SHA-256 côté serveur et lance l'analyse OCR automatique pour valider la conformité des pièces.")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Offre technique déposée et analyse OCR en cours."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Fichier manquant, format non autorisé ou taille limite dépassée."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Opération interdite (rôle OPERATEUR_ECONOMIQUE requis)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Soumission d'offre introuvable.")
        })
        public ResponseEntity<ApiResponse<OffreTechniqueResponse>> deposer(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission d'offre", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String soumissionId,
                        @io.swagger.v3.oas.annotations.Parameter(description = "Le fichier physique de l'offre technique (PDF ou archive ZIP)", required = true)
                        @RequestParam("fichier") MultipartFile fichier,
                        @io.swagger.v3.oas.annotations.Parameter(description = "Hash SHA-256 calculé côté client (optionnel)", required = false)
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
        @Operation(summary = "Consulter l'offre technique", description = "Retourne les métadonnées de l'offre technique rattachée à une soumission (ex: hash de fichier, conformité OCR, URL de scan).")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Métadonnées de l'offre technique récupérées avec succès."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôles autorisés: OPERATEUR_ECONOMIQUE, MEMBRE_COMMISSION, ADMIN, CONTROLEUR)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Offre technique ou soumission introuvable.")
        })
        public ResponseEntity<ApiResponse<OffreTechniqueResponse>> getOffreTechnique(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission d'offre", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String soumissionId,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE", "MEMBRE_COMMISSION", "ADMIN",
                                "CONTROLEUR");
                OffreTechniqueResponse response = offreTechniqueService.getOffreTechnique(soumissionId);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }
}
