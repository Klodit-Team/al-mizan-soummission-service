package com.klodit.soumission_service.client;

import com.klodit.soumission_service.client.dto.AppelOffreExterneDTO;
import com.klodit.soumission_service.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Client REST synchrone vers le Service Appels d'Offres (:8003).
 *
 * Utilise le nouveau RestClient de Spring 6+ (successeur de RestTemplate).
 * En cas d'indisponibilité du service AO, les méthodes retournent
 * des Optional.empty() avec un log d'avertissement (dégradation gracieuse).
 */
@Component
@Slf4j
public class AppelOffreClient {

    private final RestClient restClient;

    public AppelOffreClient(
            @Value("${services.appel-offre.url:http://localhost:8003}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-User-Id", "SYSTEM")
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }

    /**
     * Récupère les détails d'un Appel d'Offres par son ID.
     *
     * @param appelOffreId ID de l'AO
     * @return Optional contenant l'AO, ou empty si indisponible/introuvable
     */
    public Optional<AppelOffreExterneDTO> getAppelOffre(String appelOffreId) {
        try {
            ApiResponse<AppelOffreExterneDTO> response = restClient.get()
                    .uri("/api/v1/appels-offres/{id}", appelOffreId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response != null && response.isSuccess() && response.getData() != null) {
                log.debug("AO récupéré — ID: {}, statut: {}",
                        appelOffreId, response.getData().getStatut());
                return Optional.of(response.getData());
            }
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Service AO indisponible pour l'AO {} : {} — dégradation gracieuse",
                    appelOffreId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Récupère la date limite de dépôt d'un AO.
     * Utilisé par US-5 pour vérifier si le dépôt est dans le délai légal.
     *
     * @param appelOffreId ID de l'AO
     * @return Optional contenant la date limite, ou empty si indisponible
     */
    public Optional<LocalDateTime> getDateLimiteDepot(String appelOffreId) {
        return getAppelOffre(appelOffreId)
                .map(AppelOffreExterneDTO::getDateLimiteDepot);
    }

    /**
     * Vérifie si un AO est actuellement en statut PUBLIE (accepte les soumissions).
     *
     * @param appelOffreId ID de l'AO
     * @return true si l'AO est publié, false sinon ou si indisponible
     */
    public boolean isAppelOffrePublie(String appelOffreId) {
        return getAppelOffre(appelOffreId)
                .map(ao -> "PUBLIE".equalsIgnoreCase(ao.getStatut()))
                .orElse(false);
    }

    /**
     * Récupère la date d'ouverture des plis d'un AO.
     * Utilisé par le service de déchiffrement pour empêcher le déchiffrement
     * avant que cette date ne soit atteinte (Loi 23-12, Art. 71).
     *
     * @param appelOffreId ID de l'AO
     * @return Optional contenant la date d'ouverture, ou empty si indisponible
     */
    public Optional<LocalDateTime> getDateOuverturePlis(String appelOffreId) {
        return getAppelOffre(appelOffreId)
                .map(AppelOffreExterneDTO::getDateOuverturePlis);
    }

    /**
     * Vérifie si la caution est requise pour un AO donné.
     */
    public boolean isCautionRequise(String appelOffreId) {
        return getAppelOffre(appelOffreId)
                .map(ao -> Boolean.TRUE.equals(ao.getCautionRequise()))
                .orElse(true); // Par défaut : exigée (principe de précaution)
    }
}
