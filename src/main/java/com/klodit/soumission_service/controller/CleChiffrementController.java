package com.klodit.soumission_service.controller;

import com.klodit.soumission_service.dto.response.ApiResponse;
import com.klodit.soumission_service.dto.response.CleChiffrementResponse;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.CleChiffrementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cles-chiffrement")
@RequiredArgsConstructor
@Tag(name = "Clés de Chiffrement", description = "Génération et gestion des clés RSA-4096 / Shamir")
public class CleChiffrementController {

        private final CleChiffrementService cleChiffrementService;
        private final RbacGuard rbacGuard;

        /**
         * US-6 : Générer la paire de clés RSA-4096 pour un AO (Shamir K-of-N)
         * Normalement déclenché automatiquement par AppelOffreEventConsumer (RabbitMQ).
         * Cet endpoint HTTP est réservé à l'ADMIN pour une génération manuelle/fallback
         * si l'event a échoué.
         */
        @PostMapping("/{aoId}")
        @Operation(summary = "Générer les clés pour un AO", description = "Génère manuellement une paire de clés RSA-4096 et fragmente la clé privée en N parts Shamir destinées aux membres de la commission d'ouverture des plis (fallback / administration).")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Clés RSA-4096 et fragments Shamir générés avec succès."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Liste de membres de commission invalide ou déjà initialisée."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès interdit (rôle ADMIN requis)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Appel d'offres introuvable.")
        })
        public ResponseEntity<ApiResponse<CleChiffrementResponse>> genererCles(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID unique de l'Appel d'offres", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String aoId,
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Liste des IDs des membres de la commission d'ouverture des plis", required = true)
                        @RequestBody List<String> membresCommission,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "ADMIN");
                CleChiffrementResponse response = cleChiffrementService.genererCles(aoId, membresCommission);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.ok(response, "Clés RSA-4096 générées — " +
                                                membresCommission.size() + " fragments Shamir créés"));
        }

        /**
         * Récupérer la clé publique d'un AO (pour le chiffrement côté client).
         * Accessible à l'opérateur économique avant de chiffrer son offre financière.
         */
        @GetMapping("/{aoId}/publique")
        @Operation(summary = "Récupérer la clé publique", description = "Retourne la clé publique RSA-4096 au format PEM d'un Appel d'Offres afin de permettre à l'opérateur de chiffrer son enveloppe financière.")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Clé publique RSA récupérée avec succès."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôles autorisés: OPERATEUR_ECONOMIQUE, ADMIN)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Clé publique ou Appel d'offres introuvable.")
        })
        public ResponseEntity<ApiResponse<CleChiffrementResponse>> getClePublique(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID de l'Appel d'offres", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String aoId,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "OPERATEUR_ECONOMIQUE", "ADMIN");
                CleChiffrementResponse response = cleChiffrementService.getClePublique(aoId);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        /**
         * US-7 : Récupérer les fragments Shamir d'un AO (pour les membres de la commission).
         */
        @GetMapping("/{aoId}/fragments")
        @Operation(summary = "Récupérer les fragments Shamir d'un AO", description = "Retourne la liste de tous les fragments Shamir de l'AO. "
                        + "Normalement appelé par le frontend de la commission pour collecter les fragments des membres présents.")
        @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Fragments récupérés avec succès."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Accès refusé (rôles requis: MEMBRE_COMMISSION, ADMIN)."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Clés ou Appel d'offres introuvables.")
        })
        public ResponseEntity<ApiResponse<List<com.klodit.soumission_service.dto.request.DechiffrementRequest.FragmentSoumis>>> getFragments(
                        @io.swagger.v3.oas.annotations.Parameter(description = "UUID de l'Appel d'offres", required = true, schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"))
                        @PathVariable String aoId,
                        HttpServletRequest httpServletRequest) {

                rbacGuard.requireRole(httpServletRequest, "MEMBRE_COMMISSION", "ADMIN");
                List<com.klodit.soumission_service.dto.request.DechiffrementRequest.FragmentSoumis> response = cleChiffrementService.getFragments(aoId);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }
}

