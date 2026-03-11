package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.RabbitMQConfig;
import com.klodit.soumission_service.messaging.event.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service d'audit centralisé.
 * Envoie systématiquement des événements d'audit typés (succès ET échecs)
 * vers le Service Audit (:8009) via RabbitMQ.
 * Toutes les méthodes sont @Async pour ne pas bloquer le flux métier.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Enregistre un événement de dépôt (tentative ou succès).
     */
    @Async
    public void logDepot(String soumissionId, String operateurId,
            String typeDepot, boolean succes, String details) {
        AuditEvent event = AuditEvent.builder()
                .type("DEPOT_TENTATIVE")
                .soumissionId(soumissionId)
                .operateurId(operateurId)
                .typeDepot(typeDepot) // OFFRE_TECHNIQUE | OFFRE_FINANCIERE | CAUTION
                .succes(succes)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();

        publierEvenementAudit(event);
    }

    /**
     * Enregistre un événement de validation de soumission.
     */
    @Async
    public void logValidation(String soumissionId, String operateurId,
            String ipDepot, boolean succes, String horodatage) {
        AuditEvent event = AuditEvent.builder()
                .type("SOUMISSION_VALIDATION")
                .soumissionId(soumissionId)
                .operateurId(operateurId)
                .ipDepot(ipDepot)
                .succes(succes)
                .horodatage(horodatage)
                .timestamp(LocalDateTime.now())
                .build();

        publierEvenementAudit(event);
    }

    /**
     * Enregistre un événement de déchiffrement (ouverture des plis).
     */
    @Async
    public void logDechiffrement(String appelOffreId, String membreCommissionId,
            int nombreOffres, boolean succes) {
        AuditEvent event = AuditEvent.builder()
                .type("DECHIFFREMENT_PLIS")
                .appelOffreId(appelOffreId)
                .membreCommissionId(membreCommissionId)
                .nombreOffres(nombreOffres)
                .succes(succes)
                .timestamp(LocalDateTime.now())
                .build();

        publierEvenementAudit(event);
    }

    // ── Publication RabbitMQ ──────────────────────────────

    private void publierEvenementAudit(AuditEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    RabbitMQConfig.RK_AUDIT_DEPOT,
                    event);
            log.debug("Événement audit publié : {}", event.getType());
        } catch (Exception e) {
            // Ne jamais bloquer le flux métier sur un échec d'audit
            log.error("Impossible de publier l'événement audit : {}", e.getMessage());
        }
    }
}
