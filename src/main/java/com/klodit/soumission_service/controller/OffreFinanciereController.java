package com.klodit.soumission_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klodit.soumission_service.dto.request.DechiffrementRequest;
import com.klodit.soumission_service.dto.request.DepotOffreFinanciereRequest;
import com.klodit.soumission_service.dto.response.ApiResponse;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.DechiffrementService;
import com.klodit.soumission_service.service.OffreFinanciereService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "Offres Financières", description = "Dépôt chiffré et déchiffrement des offres financières")
public class OffreFinanciereController {

        private final OffreFinanciereService offreFinanciereService;
        private final DechiffrementService dechiffrementService;
        private final RbacGuard rbacGuard;
        private final ObjectMapper objectMapper;

        /**
         * US-3 : Uploader l'offre financière chiffrée (E2E)
         * Le fichier DOIT être chiffré côté client avant upload.
         */
        @PostMapping(value = "/api/v1/soumissions/{soumissionId}/offre-financiere", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Déposer l'offre financière chiffrée", description = "Upload le ciphertext de l'offre financière. "
                        + "Le fichier doit être chiffré AES-256-GCM + RSA-4096 avant envoi. "
                        + "La signature ECDSA P-384 garantit la non-répudiation.")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "L'offre financière chiffrée a été enregistrée avec succès."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Signature ECDSA invalide, clé publique mal formée ou fichier manquant."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès interdit (rôle OPERATEUR_ECONOMIQUE requis)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Soumission d'offre introuvable.")
        })
        public ResponseEntity<ApiResponse<OffreFinanciereResponse>> deposer(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission d'offre", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String soumissionId,
                        @io.swagger.v3.oas.annotations.Parameter(description = "Le scan physique ou archive chiffrée de l'offre financière", required = true)
                        @RequestParam("fichierChiffre") MultipartFile fichierChiffre,
                        @io.swagger.v3.oas.annotations.Parameter(description = "Métadonnées JSON de dépôt d'offre (DepotOffreFinanciereRequest)", required = true)
                        @RequestPart("donnees") String donneesJson,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE");
                String operateurId = rbacGuard.getUserId(httpServletRequest);

                DepotOffreFinanciereRequest request;
                try {
                        request = objectMapper.readValue(donneesJson, DepotOffreFinanciereRequest.class);
                } catch (Exception e) {
                        throw new com.klodit.soumission_service.exception.FichierInvalideException(
                                        "Format JSON invalide pour les données du dépôt d'offre : " + e.getMessage());
                }

                OffreFinanciereResponse response = offreFinanciereService
                                .deposerOffreFinanciere(soumissionId, operateurId, fichierChiffre, request);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.ok(response, "Offre financière chiffrée enregistrée"));
        }

        /**
         * Consulter l'offre financière d'une soumission.
         * Accessible au propriétaire ou à la commission/admin/contrôleur.
         */
        @GetMapping("/api/v1/soumissions/{soumissionId}/offre-financiere")
        @Operation(summary = "Consulter l'offre financière", description = "Les montants ne sont visibles qu'après ouverture des plis.")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Détails de l'offre financière récupérés avec succès."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôles requis: OPERATEUR_ECONOMIQUE, MEMBRE_COMMISSION, ADMIN, CONTROLEUR)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Offre financière ou soumission introuvable.")
        })
        public ResponseEntity<ApiResponse<OffreFinanciereResponse>> getOffreFinanciere(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de la soumission d'offre", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String soumissionId,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE", "MEMBRE_COMMISSION", "ADMIN",
                                "CONTROLEUR");
                OffreFinanciereResponse response = offreFinanciereService.getOffreFinanciere(soumissionId);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        /**
         * US-7 : Déchiffrer toutes les offres d'un AO (ouverture des plis)
         * Requiert K fragments Shamir des membres de la commission.
         */
        @PostMapping("/api/v1/offres-financieres/dechiffrer/{aoId}")
        @Operation(summary = "Déchiffrer les offres (ouverture des plis)", description = "Reconstitue la clé privée RSA via K fragments Shamir, "
                        + "déchiffre les PDF des offres financières, les stocke en clair dans MinIO "
                        + "et envoie une demande OCR au Service IA pour extraction des montants.")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Offres financières déchiffrées avec succès. Lance l'analyse OCR."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Nombre de fragments insuffisant pour atteindre le seuil de reconstruction Shamir ou fragments corrompus."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Opération interdite (rôle MEMBRE_COMMISSION requis)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Appel d'offres ou clés de chiffrement introuvables.")
        })
        public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> dechiffrer(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de l'appel d'offres", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String aoId,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Dictionnaire contenant les fragments Shamir des membres de la commission (ID membre -> fragment)", required = true)
                        @Valid @RequestBody DechiffrementRequest request,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "MEMBRE_COMMISSION");
                String membreId = rbacGuard.getUserId(httpServletRequest);
                DechiffrementService.DechiffrementResultat resultat = dechiffrementService
                                .dechiffrerOffres(aoId, request.getFragments(), membreId);

                java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
                data.put("offresDecryptees", resultat.offresDecryptees());
                data.put("totalTrouvees", resultat.totalTrouvees());
                data.put("totalDecryptees", resultat.offresDecryptees().size());
                data.put("ocrEnCours", true);
                data.put("messageOcr", "Les montants (HT, TVA, TTC) seront extraits automatiquement par OCR");
                if (!resultat.erreurs().isEmpty()) {
                        data.put("erreurs", resultat.erreurs());
                }

                String message = String.format("%d/%d offre(s) financière(s) déchiffrée(s) — OCR en cours",
                                resultat.offresDecryptees().size(), resultat.totalTrouvees());
                if (!resultat.erreurs().isEmpty()) {
                        message += " — " + resultat.erreurs().size() + " erreur(s)";
                }

                return ResponseEntity.ok(ApiResponse.ok(data, message));
        }
}
