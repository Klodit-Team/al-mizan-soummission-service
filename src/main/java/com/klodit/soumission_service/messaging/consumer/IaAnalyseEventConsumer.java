package com.klodit.soumission_service.messaging.consumer;

import com.klodit.soumission_service.config.RabbitMQConfig;
import com.klodit.soumission_service.messaging.event.IaAnalyseTermineeEvent;
import com.klodit.soumission_service.repository.OffreTechniqueRepository;
import com.klodit.soumission_service.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer : Résultat de l'analyse IA (OCR ≥ 90%) reçu.
 * Met à jour le champ is_conforme de l'offre technique.
 *
 * Idempotent : un même message reçu deux fois ne provoque
 * pas de double mise à jour grâce à la table processed_events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IaAnalyseEventConsumer {

    private final OffreTechniqueRepository offreTechniqueRepository;
    private final IdempotencyService idempotencyService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_IA_ANALYSE_TERMINEE)
    @Transactional
    public void traiterAnalyseTerminee(IaAnalyseTermineeEvent event) {
        String eventId = "ia-analyse-" + event.getSoumissionId() + "-" + event.getTimestamp();

        // Vérification d'idempotence
        if (idempotencyService.isAlreadyProcessed(eventId)) {
            return;
        }

        log.info("Réception analyse IA — soumission: {}, conforme: {}, score: {}",
                event.getSoumissionId(), event.getIsConforme(), event.getScoreOcr());

        offreTechniqueRepository.findBySoumissionId(event.getSoumissionId())
                .ifPresent(ot -> {
                    ot.setIsConforme(event.getIsConforme());
                    ot.setObservations("Score OCR: " + event.getScoreOcr()
                            + "% — " + (event.getIsConforme() ? "Conforme" : "Non conforme"));
                    offreTechniqueRepository.save(ot);
                    log.info("Offre technique mise à jour — soumission: {}, is_conforme: {}",
                            event.getSoumissionId(), event.getIsConforme());
                });

        // Marquer comme traité
        idempotencyService.markAsProcessed(eventId, "ia.analyse.terminee",
                RabbitMQConfig.QUEUE_IA_ANALYSE_TERMINEE);
    }
}
