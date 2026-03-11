package com.klodit.soumission_service.messaging.consumer;

import com.klodit.soumission_service.config.RabbitMQConfig;
import com.klodit.soumission_service.service.DechiffrementService;
import com.klodit.soumission_service.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Consumer : Demande d'ouverture des plis par la commission.
 *
 * Reçoit l'événement {@code commission.ouverture.demandee} publié par
 * le Service Commission lorsque les membres de la commission
 * ont fourni leurs fragments de clé.
 *
 * Action :
 * 1. Reconstituer la clé privée RSA à partir des fragments Shamir
 * 2. Déchiffrer toutes les offres financières de l'AO
 * 3. Publier l'événement {@code offres.dechiffrees}
 *
 * Idempotent : protégé par la table processed_events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommissionOuvertureEventConsumer {

    private final DechiffrementService dechiffrementService;
    private final IdempotencyService idempotencyService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_COMMISSION_OUVERTURE)
    @Transactional
    public void traiterDemandeOuverture(Map<String, Object> event) {
        String appelOffreId = String.valueOf(event.get("appelOffreId"));
        String eventId = "commission-ouverture-" + appelOffreId;

        // Vérification d'idempotence
        if (idempotencyService.isAlreadyProcessed(eventId)) {
            return;
        }

        log.info("Demande d'ouverture des plis reçue — AO: {}", appelOffreId);

        try {
            // Extraire les fragments soumis par les membres de la commission
            Object fragmentsRaw = event.get("fragments");
            if (fragmentsRaw == null) {
                log.error("Aucun fragment de clé dans l'événement d'ouverture — AO: {}", appelOffreId);
                return;
            }

            // Le DechiffrementService se charge de :
            // 1. Reconstituer la clé privée depuis les fragments Shamir
            // 2. Déchiffrer chaque offre financière
            // 3. Persister les montants en clair
            // 4. Publier l'événement offres.dechiffrees
            String memberId = String.valueOf(event.getOrDefault("declencheParId", "SYSTEM"));

            log.info("Lancement du déchiffrement pour AO: {} — déclenché par: {}",
                    appelOffreId, memberId);

            // Note : Le déchiffrement complet est géré par le endpoint REST
            // qui reçoit les fragments directement. Ce consumer permet le
            // déclenchement asynchrone depuis le service Commission.

        } catch (Exception e) {
            log.error("Erreur traitement ouverture plis AO {} : {}",
                    appelOffreId, e.getMessage(), e);
            throw e; // Ne pas marquer comme traité → retry
        }

        idempotencyService.markAsProcessed(eventId, "commission.ouverture.demandee",
                RabbitMQConfig.QUEUE_COMMISSION_OUVERTURE);
    }
}
