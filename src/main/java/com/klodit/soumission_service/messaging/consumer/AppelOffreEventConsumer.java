package com.klodit.soumission_service.messaging.consumer;

import com.klodit.soumission_service.config.RabbitMQConfig;
import com.klodit.soumission_service.messaging.event.AppelOffrePublieEvent;
import com.klodit.soumission_service.service.CleChiffrementService;
import com.klodit.soumission_service.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Consumer : événements liés aux Appels d'Offres.
 *
 * - appel_offre.publie → Génération automatique des clés de chiffrement
 * (RSA-4096 + Shamir)
 * - appel_offre.cloture → Log informatif (plus de soumissions acceptées)
 *
 * Idempotent : protégé par la table processed_events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppelOffreEventConsumer {

    private final CleChiffrementService cleChiffrementService;
    private final IdempotencyService idempotencyService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_AO_PUBLIE)
    @Transactional
    public void traiterAoPublie(AppelOffrePublieEvent event) {
        String eventId = "ao-publie-" + event.getAppelOffreId();

        // Vérification d'idempotence
        if (idempotencyService.isAlreadyProcessed(eventId)) {
            return;
        }

        log.info("AO publié reçu — ID: {}, membres commission: {}",
                event.getAppelOffreId(), event.getMembresCommissionIds());

        try {
            List<String> membres = event.getMembresCommissionIds();
            if (membres != null && !membres.isEmpty()) {
                cleChiffrementService.genererCles(event.getAppelOffreId(), membres);
                log.info("Clés de chiffrement générées automatiquement pour AO: {}",
                        event.getAppelOffreId());
            } else {
                log.warn("AO publié sans membres de commission — clés NON générées pour AO: {}",
                        event.getAppelOffreId());
            }
        } catch (Exception e) {
            log.error("Erreur génération clés pour AO {} : {}", event.getAppelOffreId(), e.getMessage(), e);
            // NE PAS marquer comme traité en cas d'erreur → le message sera retransmis
            throw e;
        }

        // Marquer comme traité APRÈS succès
        idempotencyService.markAsProcessed(eventId, "appel_offre.publie",
                RabbitMQConfig.QUEUE_AO_PUBLIE);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_AO_CLOTURE)
    @Transactional
    public void traiterAoCloture(Map<String, Object> event) {
        String aoId = String.valueOf(event.get("appelOffreId"));
        String eventId = "ao-cloture-" + aoId;

        // Vérification d'idempotence
        if (idempotencyService.isAlreadyProcessed(eventId)) {
            return;
        }

        log.info("AO clôturé — ID: {}. Plus aucune soumission ne sera acceptée.", aoId);

        // Marquer comme traité
        idempotencyService.markAsProcessed(eventId, "appel_offre.cloture",
                RabbitMQConfig.QUEUE_AO_CLOTURE);
    }
}
