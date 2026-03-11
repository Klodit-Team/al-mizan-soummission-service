package com.klodit.soumission_service.controller;

import com.klodit.soumission_service.dto.request.DechiffrementRequest;
import com.klodit.soumission_service.dto.response.ApiResponse;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.OffreFinanciereService;
import com.klodit.soumission_service.service.DechiffrementService;
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

        /**
         * US-3 : Uploader l'offre financière chiffrée (E2E)
         * Le fichier DOIT être chiffré côté client avant upload.
         * La signature ECDSA P-384 est obligatoire (non-répudiation CSL §4.4.5).
         */
        @PostMapping(value = "/api/v1/soumissions/{soumissionId}/offre-financiere", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Déposer l'offre financière chiffrée", description = "Upload le ciphertext de l'offre financière. "
                        + "Le fichier doit être chiffré AES-256-GCM + RSA-4096 avant envoi. "
                        + "La signature ECDSA P-384 garantit la non-répudiation.")
        public ResponseEntity<ApiResponse<OffreFinanciereResponse>> deposer(
                        @PathVariable String soumissionId,
                        @RequestParam("fichierChiffre") MultipartFile fichierChiffre,
                        @RequestParam(value = "hashClient", required = false) String hashClient,
                        @RequestParam("signatureEcdsa") String signatureEcdsa,
                        @RequestParam("clePubliqueEcdsaPem") String clePubliqueEcdsaPem,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE");
                String operateurId = rbacGuard.getUserId(httpServletRequest);
                OffreFinanciereResponse response = offreFinanciereService
                                .deposerOffreFinanciere(soumissionId, operateurId,
                                                fichierChiffre, hashClient, signatureEcdsa, clePubliqueEcdsaPem);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.ok(response, "Offre financière chiffrée enregistrée"));
        }

        /**
         * Consulter l'offre financière d'une soumission.
         * Accessible au propriétaire ou à la commission/admin/contrôleur.
         */
        @GetMapping("/api/v1/soumissions/{soumissionId}/offre-financiere")
        @Operation(summary = "Consulter l'offre financière", description = "Les montants ne sont visibles qu'après ouverture des plis.")
        public ResponseEntity<ApiResponse<OffreFinanciereResponse>> getOffreFinanciere(
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
         * Après déchiffrement :
         * - Le PDF en clair est stocké dans MinIO pour consultation ultérieure
         * - Un événement OCR est envoyé au Service IA pour extraire les montants
         * - Les montants (HT, TVA, TTC) seront remplis automatiquement après l'OCR
         */
        @PostMapping("/api/v1/offres-financieres/dechiffrer/{aoId}")
        @Operation(summary = "Déchiffrer les offres (ouverture des plis)", description = "Reconstitue la clé privée RSA via K fragments Shamir, "
                        + "déchiffre les PDF des offres financières, les stocke en clair dans MinIO "
                        + "et envoie une demande OCR au Service IA pour extraction des montants.")
        public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> dechiffrer(
                        @PathVariable String aoId,
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
