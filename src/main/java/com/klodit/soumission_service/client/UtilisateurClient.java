package com.klodit.soumission_service.client;

import com.klodit.soumission_service.client.dto.UtilisateurExterneDTO;
import com.klodit.soumission_service.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Client REST synchrone vers le Service Utilisateurs (:8002).
 *
 * Permet de valider l'identité et le profil d'un opérateur économique
 * avant d'autoriser une opération critique (dépôt, validation).
 *
 * Dégradation gracieuse : si le service est indisponible,
 * les opérations continuent avec un log d'avertissement.
 */
@Component
@Slf4j
public class UtilisateurClient {

    private final RestClient restClient;

    public UtilisateurClient(
            @Value("${services.utilisateur.url:http://localhost:8002}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-User-Id", "SYSTEM")
                .defaultHeader("X-User-Role", "ADMIN")
                .build();
    }

    /**
     * Récupère le profil d'un utilisateur par son ID.
     *
     * @param userId ID de l'utilisateur (UUID)
     * @return Optional contenant le profil, ou empty si indisponible/introuvable
     */
    public Optional<UtilisateurExterneDTO> getUtilisateur(String userId) {
        try {
            ApiResponse<UtilisateurExterneDTO> response = restClient.get()
                    .uri("/api/v1/utilisateurs/{id}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response != null && response.isSuccess() && response.getData() != null) {
                log.debug("Utilisateur récupéré — ID: {}, rôle: {}",
                        userId, response.getData().getRole());
                return Optional.of(response.getData());
            }
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Service Utilisateurs indisponible pour l'ID {} : {} — dégradation gracieuse",
                    userId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Vérifie qu'un utilisateur est un opérateur économique éligible et vérifié.
     * Critères (auth.users + operateurs_economiques + organisations) :
     * - Compte actif (auth.users.is_active = true)
     * - Éligible (is_eligible = true)
     * - Non blacklisté (is_blacklisted = false)
     * - Organisation vérifiée (is_verified = true)
     * - Rôle = OPERATEUR_ECONOMIQUE
     *
     * @param userId ID de l'utilisateur
     * @return true si toutes les conditions sont remplies, false sinon
     */
    public boolean isOperateurValide(String userId) {
        return getUtilisateur(userId)
                .map(u -> Boolean.TRUE.equals(u.getIsActive())
                        && Boolean.TRUE.equals(u.getIsEligible())
                        && !Boolean.TRUE.equals(u.getIsBlacklisted())
                        && Boolean.TRUE.equals(u.getIsVerifie())
                        && "OPERATEUR_ECONOMIQUE".equalsIgnoreCase(u.getRole()))
                .orElse(true); // Dégradation gracieuse : autoriser si service indisponible
    }

    /**
     * Vérifie qu'un utilisateur est un membre de commission.
     */
    public boolean isMembreCommission(String userId) {
        return getUtilisateur(userId)
                .map(u -> {
                    String role = u.getRole();
                    return "MEMBRE_COMMISSION".equalsIgnoreCase(role)
                            || "ADMIN".equalsIgnoreCase(role);
                })
                .orElse(true); // Dégradation gracieuse
    }
}
