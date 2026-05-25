package com.klodit.soumission_service.controller;

import com.klodit.soumission_service.dto.request.CreateSoumissionRequest;
import com.klodit.soumission_service.dto.request.StatutSoumissionRequest;
import com.klodit.soumission_service.dto.response.ApiResponse;
import com.klodit.soumission_service.dto.response.SoumissionDetailResponse;
import com.klodit.soumission_service.dto.response.SoumissionResponse;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.SoumissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/soumissions")
@RequiredArgsConstructor
@Tag(name = "Soumissions", description = "Gestion des soumissions aux marchés publics")
public class SoumissionController {

    private final SoumissionService soumissionService;
    private final RbacGuard rbacGuard;

    /**
     * US-1 : Créer une soumission en brouillon
     */
    @PostMapping
    @Operation(summary = "Créer une soumission (brouillon)", description = "Crée une nouvelle soumission en statut BROUILLON pour un appel d'offres")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Soumission initialisée avec succès sous forme de BROUILLON."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Données d'entrée invalides ou manquantes."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôle OPERATEUR_ECONOMIQUE requis).")
    })
    public ResponseEntity<ApiResponse<SoumissionResponse>> creerBrouillon(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Informations de l'AO et du Lot pour initialiser la soumission", required = true)
            @Valid @RequestBody CreateSoumissionRequest request,
            HttpServletRequest httpServletRequest) {

        rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE");
        String operateurId = rbacGuard.getUserId(httpServletRequest);
        SoumissionResponse response = soumissionService.creerBrouillon(request, operateurId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Soumission créée en brouillon"));
    }

    /**
     * US-8 : Lister mes soumissions
     */
    @GetMapping
    @Operation(summary = "Lister mes soumissions", description = "Retourne la liste des soumissions de l'opérateur économique connecté")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Liste de soumissions récupérée avec succès."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôle OPERATEUR_ECONOMIQUE requis).")
    })
    public ResponseEntity<ApiResponse<List<SoumissionResponse>>> listerMesSoumissions(
            HttpServletRequest httpServletRequest) {

        rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE");
        String operateurId = rbacGuard.getUserId(httpServletRequest);
        List<SoumissionResponse> list = soumissionService.listerMesSoumissions(operateurId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * Consulter le détail d'une soumission.
     * Accessible au propriétaire (OPERATEUR_ECONOMIQUE) ou à la
     * commission/admin/contrôleur.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une soumission", description = "Retourne le détail complet d'une soumission avec ses offres et caution")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Détail de la soumission récupéré avec succès."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôles requis: OPERATEUR_ECONOMIQUE, MEMBRE_COMMISSION, ADMIN, CONTROLEUR)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Soumission introuvable.")
    })
    public ResponseEntity<ApiResponse<SoumissionDetailResponse>> getDetail(
            @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
            @PathVariable String id,
            HttpServletRequest httpServletRequest) {

        rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE", "MEMBRE_COMMISSION", "ADMIN", "CONTROLEUR");
        SoumissionDetailResponse detail = soumissionService.getDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    /**
     * Lister les soumissions d'un appel d'offres.
     * Réservé à la commission, l'admin ou le contrôleur (phase d'ouverture des
     * plis).
     */
    @GetMapping("/appel-offre/{aoId}")
    @Operation(summary = "Soumissions par appel d'offres", description = "Liste toutes les soumissions déposées pour un appel d'offres donné")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Liste des soumissions récupérée avec succès."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôles requis: SERVICE_CONTRACTANT, MEMBRE_COMMISSION, ADMIN, CONTROLEUR)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Appel d'offres introuvable.")
    })
    public ResponseEntity<ApiResponse<List<SoumissionResponse>>> listerParAO(
            @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de l'appel d'offres", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
            @PathVariable String aoId,
            HttpServletRequest httpServletRequest) {

        rbacGuard.requireRole(httpServletRequest, "SERVICE_CONTRACTANT", "MEMBRE_COMMISSION", "ADMIN", "CONTROLEUR");
        List<SoumissionResponse> list = soumissionService.listerParAppelOffre(aoId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * US-5 : Valider et soumettre définitivement la soumission
     */
    @PutMapping("/{id}/valider")
    @Operation(summary = "Soumettre définitivement la soumission", description = "Horodate le dépôt côté serveur, vérifie le délai et publie l'événement.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Soumission validée et soumise définitivement avec horodatage électronique immuable."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Soumission incomplète (offre technique, financière ou caution manquante) ou date limite dépassée."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôle OPERATEUR_ECONOMIQUE requis)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Soumission introuvable.")
    })
    public ResponseEntity<ApiResponse<SoumissionResponse>> valider(
            @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission à valider", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        rbacGuard.requireRole(httpRequest, "OPERATEUR_ECONOMIQUE");
        String operateurId = rbacGuard.getUserId(httpRequest);
        String ipDepot = obtenirIpClient(httpRequest);
        SoumissionResponse response = soumissionService.validerEtSoumettre(id, operateurId, ipDepot);
        return ResponseEntity.ok(ApiResponse.ok(response, "Soumission déposée — horodatée : "
                + response.getHorodatageServeur()));
    }

    /**
     * Changer le statut d'une soumission (workflow interne).
     * Réservé à l'ADMIN et à la MEMBRE_COMMISSION.
     */
    @PutMapping("/{id}/statut")
    @Operation(summary = "Changer le statut (workflow interne)", description = "Réservé à l'ADMIN et à la MEMBRE_COMMISSION.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Le statut a été mis à jour avec succès."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Transition de statut invalide ou corps de requête mal formé."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôles requis: ADMIN, MEMBRE_COMMISSION)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Soumission introuvable.")
    })
    public ResponseEntity<ApiResponse<SoumissionResponse>> changerStatut(
            @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Nouveau statut cible (StatutSoumissionRequest)", required = true)
            @RequestBody StatutSoumissionRequest request,
            HttpServletRequest httpServletRequest) {

        rbacGuard.requireRole(httpServletRequest, "ADMIN", "MEMBRE_COMMISSION");
        SoumissionResponse response = soumissionService.changerStatut(id, request.getStatut());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── Utilitaire : extraction IP client ──
    private String obtenirIpClient(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
