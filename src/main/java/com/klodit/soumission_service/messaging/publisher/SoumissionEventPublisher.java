package com.klodit.soumission_service.messaging.publisher;

import com.klodit.soumission_service.config.RabbitMQConfig;
import com.klodit.soumission_service.messaging.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SoumissionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Publie l'événement "soumission déposée" après validation définitive (US-5).
     * Consommé par : Service Notifications, Service Audit.
     */
    @Async
    public void publierSoumissionDeposee(SoumissionDeposeeEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    RabbitMQConfig.RK_SOUMISSION_DEPOSEE,
                    event);
            log.info("Événement publié → soumission.deposee | ID: {}", event.getSoumissionId());
        } catch (Exception e) {
            log.error("Échec publication soumission.deposee : {}", e.getMessage(), e);
        }
    }

    /**
     * Publie la demande d'analyse OCR vers le Service IA (US-2).
     * Consommé par : Service IA (précision cible ≥ 90%).
     */
    @Async
    public void publierDemandeAnalyseOCR(SoumissionAnalyseDemandeEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    RabbitMQConfig.RK_SOUMISSION_ANALYSE,
                    event);
            log.info("Événement publié → soumission.analyse.demandee | OT ID: {}",
                    event.getOffreTechniqueId());
        } catch (Exception e) {
            log.error("Échec publication soumission.analyse.demandee : {}", e.getMessage(), e);
        }
    }

    /**
     * Publie l'événement après déchiffrement des offres financières (US-7).
     * Consommé par : Service Évaluations, Service Audit.
     */
    @Async
    public void publierOffresDecryptees(OffresDecrypteesEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    RabbitMQConfig.RK_OFFRES_DECHIFFREES,
                    event);
            log.info("Événement publié → offres.dechiffrees | AO: {}, nb offres: {}",
                    event.getAppelOffreId(), event.getNombreOffres());
        } catch (Exception e) {
            log.error("Échec publication offres.dechiffrees : {}", e.getMessage(), e);
        }
    }

    /**
     * Publie l'accusé de réception après dépôt réussi (US-5).
     * Consommé par : Service Notifications (email à l'opérateur).
     */
    @Async
    public void publierSoumissionRecue(SoumissionRecueEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    RabbitMQConfig.RK_SOUMISSION_RECUE,
                    event);
            log.info("Événement publié → soumission.recue | Réf: {}", event.getAccuseReceptionRef());
        } catch (Exception e) {
            log.error("Échec publication soumission.recue : {}", e.getMessage(), e);
        }
    }

    /**
     * Publie le changement de statut (routing key dynamique).
     * Routing key = "soumission.statut.{nouveauStatut}" (ex:
     * soumission.statut.ouverte)
     *
     * Le TopicExchange route vers les queues abonnées à "soumission.statut.*".
     */
    @Async
    public void publierStatutChange(SoumissionStatutChangeEvent event) {
        try {
            String routingKey = "soumission.statut." + event.getNouveauStatut().toLowerCase();
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    routingKey,
                    event);
            log.info("Événement publié → {} | Soumission: {} ({} → {})",
                    routingKey, event.getSoumissionId(),
                    event.getAncienStatut(), event.getNouveauStatut());
        } catch (Exception e) {
            log.error("Échec publication soumission.statut.change : {}", e.getMessage(), e);
        }
    }

    /**
     * Publie la demande d'analyse OCR d'une offre financière déchiffrée (PDF en
     * clair)
     * vers le Service IA.
     * Consommé par : Service IA (OCR extraction montants HT, TVA, TTC).
     */
    @Async
    public void publierDemandeAnalyseOffreFinanciere(OffreFinanciereAnalyseDemandeEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    RabbitMQConfig.RK_OFFRE_FINANCIERE_ANALYSE,
                    event);
            log.info("Événement publié → offre.financiere.analyse.demandee | OF ID: {}, Soumission: {}",
                    event.getOffreFinanciereId(), event.getSoumissionId());
        } catch (Exception e) {
            log.error("Échec publication offre.financiere.analyse.demandee : {}", e.getMessage(), e);
        }
    }

    /**
     * Publie l'événement de clôture des soumissions (pour déclencher l'IA).
     * Consommé par : AI Orchestrator.
     */
    @Async
    public void publierSoumissionsClosed(String aoId) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("aoId", aoId);
            payload.put("soumissionId", null);
            
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SOUMISSION_EXCHANGE,
                    "soumissions.closed",
                    payload);
            log.info("Événement publié → soumissions.closed | AO: {}", aoId);
        } catch (Exception e) {
            log.error("Échec publication soumissions.closed : {}", e.getMessage(), e);
        }
    }
}
