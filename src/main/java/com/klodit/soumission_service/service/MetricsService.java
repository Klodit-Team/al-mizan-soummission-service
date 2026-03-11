package com.klodit.soumission_service.service;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service de métriques métier personnalisées exposées via Prometheus.
 *
 * Métriques collectées :
 * - soumission_deposee_total : compteur de soumissions déposées (par statut)
 * - soumission_validation_duration : temps de validation d'une soumission
 * - chiffrement_operation_duration : temps d'une opération de
 * chiffrement/déchiffrement
 * - fichier_upload_size_bytes : taille des fichiers uploadés
 * - rabbitmq_event_published_total : compteur d'événements publiés (par type)
 * - rabbitmq_event_consumed_total : compteur d'événements consommés (par queue)
 * - rabbitmq_event_duplicate_total : compteur de messages dupliqués
 * (idempotence)
 * - session_validation_total : compteur de validations de session (par
 * résultat)
 * - rate_limit_exceeded_total : compteur de requêtes rate-limitées
 * - active_soumissions_gauge : nombre de soumissions actives (brouillon)
 */
@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry registry;

    // Compteurs
    private final Counter soumissionDeposeeCounter;
    private final Counter eventPublishedCounter;
    private final Counter eventConsumedCounter;
    private final Counter eventDuplicateCounter;
    private final Counter rateLimitCounter;

    // Timers
    private final Timer validationTimer;
    private final Timer chiffrementTimer;

    // Distribution
    private final DistributionSummary uploadSizeSummary;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;

        // Compteurs
        this.soumissionDeposeeCounter = Counter.builder("soumission_deposee_total")
                .description("Nombre total de soumissions déposées")
                .tag("service", "soumission-service")
                .register(registry);

        this.eventPublishedCounter = Counter.builder("rabbitmq_event_published_total")
                .description("Événements RabbitMQ publiés")
                .tag("service", "soumission-service")
                .register(registry);

        this.eventConsumedCounter = Counter.builder("rabbitmq_event_consumed_total")
                .description("Événements RabbitMQ consommés")
                .tag("service", "soumission-service")
                .register(registry);

        this.eventDuplicateCounter = Counter.builder("rabbitmq_event_duplicate_total")
                .description("Événements RabbitMQ dupliqués (idempotence)")
                .tag("service", "soumission-service")
                .register(registry);

        this.rateLimitCounter = Counter.builder("rate_limit_exceeded_total")
                .description("Requêtes rejetées par rate limiting")
                .tag("service", "soumission-service")
                .register(registry);

        // Timers
        this.validationTimer = Timer.builder("soumission_validation_duration")
                .description("Durée de validation d'une soumission")
                .tag("service", "soumission-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.chiffrementTimer = Timer.builder("chiffrement_operation_duration")
                .description("Durée d'une opération de chiffrement/déchiffrement")
                .tag("service", "soumission-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Distribution
        this.uploadSizeSummary = DistributionSummary.builder("fichier_upload_size_bytes")
                .description("Taille des fichiers uploadés")
                .tag("service", "soumission-service")
                .baseUnit("bytes")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }

    // ── Méthodes publiques ─────────────────────────────────

    public void incrementSoumissionDeposee() {
        soumissionDeposeeCounter.increment();
    }

    public void incrementEventPublished(String eventType) {
        Counter.builder("rabbitmq_event_published_total")
                .tag("event_type", eventType)
                .tag("service", "soumission-service")
                .register(registry)
                .increment();
    }

    public void incrementEventConsumed(String queue) {
        Counter.builder("rabbitmq_event_consumed_total")
                .tag("queue", queue)
                .tag("service", "soumission-service")
                .register(registry)
                .increment();
    }

    public void incrementEventDuplicate(String queue) {
        eventDuplicateCounter.increment();
    }

    public void incrementRateLimitExceeded() {
        rateLimitCounter.increment();
    }

    public void recordValidationDuration(long durationMs) {
        validationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordChiffrementDuration(long durationMs) {
        chiffrementTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordUploadSize(long sizeBytes) {
        uploadSizeSummary.record(sizeBytes);
    }

    /**
     * Enregistre un gauge pour le nombre de soumissions actives (brouillons).
     * Appelé au démarrage du service.
     */
    public void registerActiveSoumissionsGauge(java.util.function.Supplier<Number> supplier) {
        Gauge.builder("active_soumissions_gauge", supplier)
                .description("Nombre de soumissions en brouillon")
                .tag("service", "soumission-service")
                .register(registry);
    }
}
