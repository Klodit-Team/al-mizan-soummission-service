package com.klodit.soumission_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klodit.soumission_service.dto.request.CreateCautionRequest;
import com.klodit.soumission_service.dto.response.ApiResponse;
import com.klodit.soumission_service.dto.response.CautionResponse;
import com.klodit.soumission_service.exception.FichierInvalideException;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.CautionService;
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
@RequestMapping("/api/v1/soumissions/{soumissionId}/caution")
@RequiredArgsConstructor
@Tag(name = "Cautions", description = "Gestion des cautions bancaires")
public class CautionController {

    private final CautionService cautionService;
    private final ObjectMapper objectMapper;
    private final RbacGuard rbacGuard;

    /**
     * US-4 : Joindre la caution bancaire (multipart/form-data)
     * On reçoit "donnees" en String pour éviter le problème de Content-Type
     * application/octet-stream envoyé par Swagger UI, puis on désérialise
     * manuellement.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Joindre la caution bancaire", description = "Permet de joindre le justificatif bancaire (scan PDF/image) et ses métadonnées à une soumission existante.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "La caution bancaire a été enregistrée avec succès."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Données JSON incorrectes, montant invalide ou scan manquant."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Opération non autorisée (rôle OPERATEUR_ECONOMIQUE requis)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Soumission d'offre introuvable.")
    })
    public ResponseEntity<ApiResponse<CautionResponse>> ajouterCaution(
            @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission d'offre", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
            @PathVariable String soumissionId,
            @io.swagger.v3.oas.annotations.Parameter(description = "Métadonnées JSON de la caution (CreateCautionRequest) sous forme de chaîne de caractères", required = true)
            @RequestPart("donnees") String donneesJson,
            @io.swagger.v3.oas.annotations.Parameter(description = "Le scan physique (PDF/image) de la caution bancaire", required = true)
            @RequestPart("scanCaution") MultipartFile scanCaution,
            HttpServletRequest httpServletRequest) {

        rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE");
        String operateurId = rbacGuard.getUserId(httpServletRequest);
        CreateCautionRequest request;
        try {
            request = objectMapper.readValue(donneesJson, CreateCautionRequest.class);
        } catch (Exception e) {
            throw new FichierInvalideException(
                    "Format JSON invalide pour les données de la caution : " + e.getMessage());
        }

        CautionResponse response = cautionService.ajouterCaution(
                soumissionId, operateurId, request, scanCaution);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Caution bancaire enregistrée"));
    }

    /**
     * Consulter la caution d'une soumission.
     * Accessible au propriétaire ou à la commission/admin/contrôleur.
     */
    @GetMapping
    @Operation(summary = "Consulter la caution", description = "Retourne les informations détaillées de la caution bancaire d'une soumission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Informations de la caution récupérées avec succès."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé pour cet utilisateur (rôles requis: OPERATEUR_ECONOMIQUE, MEMBRE_COMMISSION, ADMIN, CONTROLEUR)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Caution ou soumission introuvable.")
    })
    public ResponseEntity<ApiResponse<CautionResponse>> getCaution(
            @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission d'offre", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
            @PathVariable String soumissionId,
            HttpServletRequest httpServletRequest) {

        rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE", "MEMBRE_COMMISSION", "ADMIN", "CONTROLEUR");
        CautionResponse response = cautionService.getCaution(soumissionId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
