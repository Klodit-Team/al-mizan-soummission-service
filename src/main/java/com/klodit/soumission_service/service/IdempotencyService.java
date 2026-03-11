package com.klodit.soumission_service.service;

import com.klodit.soumission_service.entity.ProcessedEvent;
import com.klodit.soumission_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service d'idempotence pour les consumers RabbitMQ.
 *
 * Chaque consumer vérifie via ce service si un message a déjà été traité
 * avant d'exécuter sa logique métier. L'unicité est garantie par
 * la contrainte UNIQUE sur event_id en base de données.
 *
 * Pattern :
 * if (idempotencyService.isAlreadyProcessed(eventId)) return;
 * // ... traitement métier ...
 * idempotencyService.markAsProcessed(eventId, "eventType", "queueName");
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Vérifie si un événement a déjà été traité.
     *
     * @param eventId identifiant unique du message (Message-Id ou corrélation UUID)
     * @return true si déjà traité — le consumer doit ignorer le message
     */
    public boolean isAlreadyProcessed(String eventId) {
        boolean exists = processedEventRepository.existsByEventId(eventId);
        if (exists) {
            log.info("Message déjà traité (idempotence) — eventId: {}", eventId);
        }
        return exists;
    }

    /**
     * Enregistre un événement comme traité.
     * À appeler APRÈS le traitement métier réussi, dans la même transaction.
     *
     * @param eventId   identifiant unique du message
     * @param eventType type logique de l'événement (ex: "ao.publie",
     *                  "ia.analyse.terminee")
     * @param queue     nom de la queue source
     */
    @Transactional
    public void markAsProcessed(String eventId, String eventType, String queue) {
        ProcessedEvent event = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .sourceQueue(queue)
                .processedAt(LocalDateTime.now())
                .build();
        processedEventRepository.save(event);
        log.debug("Événement marqué comme traité — eventId: {}, type: {}", eventId, eventType);
    }

    /**
     * Purge les entrées de plus de 30 jours.
     * Exécuté chaque jour à 3h du matin.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgerAnciennesEntrees() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int deleted = processedEventRepository.deleteByProcessedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purge idempotence : {} entrées supprimées (avant {})", deleted, cutoff);
        }
    }
}
