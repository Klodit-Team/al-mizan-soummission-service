package com.klodit.soumission_service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MetricsService — Tests unitaires")
class MetricsServiceTest {

    private MeterRegistry registry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsService = new MetricsService(registry);
    }

    @Test
    @DisplayName("incrementSoumissionDeposee → compteur incrémenté")
    void incrementSoumissionDeposee() {
        metricsService.incrementSoumissionDeposee();
        metricsService.incrementSoumissionDeposee();

        Counter counter = registry.find("soumission_deposee_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("incrementEventPublished → compteur avec tag event_type")
    void incrementEventPublished() {
        metricsService.incrementEventPublished("soumission.deposee");

        Counter counter = registry.find("rabbitmq_event_published_total")
                .tag("event_type", "soumission.deposee")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("incrementEventConsumed → compteur avec tag queue")
    void incrementEventConsumed() {
        metricsService.incrementEventConsumed("queue.ao.publie");

        Counter counter = registry.find("rabbitmq_event_consumed_total")
                .tag("queue", "queue.ao.publie")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("incrementEventDuplicate → compteur incrémenté")
    void incrementEventDuplicate() {
        metricsService.incrementEventDuplicate("queue.test");

        Counter counter = registry.find("rabbitmq_event_duplicate_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("incrementRateLimitExceeded → compteur incrémenté")
    void incrementRateLimitExceeded() {
        metricsService.incrementRateLimitExceeded();

        Counter counter = registry.find("rate_limit_exceeded_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordValidationDuration → timer enregistré")
    void recordValidationDuration() {
        metricsService.recordValidationDuration(150);

        Timer timer = registry.find("soumission_validation_duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordChiffrementDuration → timer enregistré")
    void recordChiffrementDuration() {
        metricsService.recordChiffrementDuration(250);

        Timer timer = registry.find("chiffrement_operation_duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("recordUploadSize → distribution enregistrée")
    void recordUploadSize() {
        metricsService.recordUploadSize(1024_000);

        var summary = registry.find("fichier_upload_size_bytes").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("registerActiveSoumissionsGauge → gauge créé")
    void registerActiveSoumissionsGauge() {
        metricsService.registerActiveSoumissionsGauge(() -> 42);

        var gauge = registry.find("active_soumissions_gauge").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42.0);
    }
}
