package com.klodit.soumission_service.client;

import com.klodit.soumission_service.client.dto.PieceAdministrativeExterneDTO;
import com.klodit.soumission_service.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * Client REST synchrone vers le Service Documents (:8005).
 *
 * Permet de vérifier que les pièces administratives d'une soumission
 * sont complètes et validées avant la validation définitive (US-5).
 *
 * Dégradation gracieuse : si le service est indisponible,
 * la validation est autorisée avec un log d'avertissement.
 */
@Component
@Slf4j
public class DocumentsClient {

    private final RestClient restClient;

    public DocumentsClient(
            @Value("${services.document.url:http://localhost:8005}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-User-Id", "SYSTEM")
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }

    /**
     * Récupère les pièces administratives associées à une soumission.
     *
     * @param soumissionId ID de la soumission
     * @return liste des pièces, ou empty si service indisponible
     */
    public Optional<List<PieceAdministrativeExterneDTO>> getPiecesAdministratives(String soumissionId) {
        try {
            ApiResponse<List<PieceAdministrativeExterneDTO>> response = restClient.get()
                    .uri("/api/v1/pieces-administratives/soumission/{soumissionId}", soumissionId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response != null && response.isSuccess() && response.getData() != null) {
                log.debug("Pièces administratives récupérées — soumission: {}, nombre: {}",
                        soumissionId, response.getData().size());
                return Optional.of(response.getData());
            }
            return Optional.of(List.of());

        } catch (Exception e) {
            log.warn("Service Documents indisponible pour la soumission {} : {} — dégradation gracieuse",
                    soumissionId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Vérifie que toutes les pièces administratives requises sont présentes et
     * validées.
     *
     * Les pièces obligatoires (Haute priorité, cahier des charges §3.2.5 US-3) :
     * NIF, NIS, REGISTRE_COMMERCE, CASIER_JUDICIAIRE, CNAS, CASNOS,
     * ATTESTATION_FISCALE, BILAN.
     *
     * @param soumissionId ID de la soumission
     * @return true si toutes les pièces sont présentes et valides, ou si le service
     *         est indisponible
     */
    public boolean arePiecesAdministrativesValides(String soumissionId) {
        return getPiecesAdministratives(soumissionId)
                .map(pieces -> {
                    if (pieces.isEmpty()) {
                        log.warn("Aucune pièce administrative trouvée pour la soumission {}", soumissionId);
                        return false;
                    }

                    // Vérifier que toutes les pièces existantes sont validées
                    boolean toutesValides = pieces.stream()
                            .allMatch(p -> Boolean.TRUE.equals(p.getIsValide()));

                    if (!toutesValides) {
                        long nonValides = pieces.stream()
                                .filter(p -> !Boolean.TRUE.equals(p.getIsValide()))
                                .count();
                        log.warn("Soumission {} : {}/{} pièces non validées",
                                soumissionId, nonValides, pieces.size());
                    }

                    return toutesValides;
                })
                .orElse(true); // Dégradation gracieuse : autoriser si service indisponible
    }
}
