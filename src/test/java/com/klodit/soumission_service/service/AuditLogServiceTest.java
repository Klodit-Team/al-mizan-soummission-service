package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.RabbitMQConfig;
import com.klodit.soumission_service.messaging.event.AuditEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService — Tests unitaires")
class AuditLogServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AuditLogService auditLogService;

    @Captor
    private ArgumentCaptor<AuditEvent> eventCaptor;

    // ── logDepot ─────────────────────────────────────────

    @Test
    @DisplayName("logDepot — succès → événement publié avec type DEPOT_TENTATIVE")
    void logDepot_succes() {
        auditLogService.logDepot("soum-001", "op-001", "OFFRE_TECHNIQUE", true, "OK");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.SOUMISSION_EXCHANGE),
                eq(RabbitMQConfig.RK_AUDIT_DEPOT),
                eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo("DEPOT_TENTATIVE");
        assertThat(event.getSoumissionId()).isEqualTo("soum-001");
        assertThat(event.getOperateurId()).isEqualTo("op-001");
        assertThat(event.getTypeDepot()).isEqualTo("OFFRE_TECHNIQUE");
        assertThat(event.getSucces()).isTrue();
        assertThat(event.getDetails()).isEqualTo("OK");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("logDepot — échec → événement avec succes=false")
    void logDepot_echec() {
        auditLogService.logDepot("soum-001", "op-001", "CAUTION", false, "Erreur upload");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.SOUMISSION_EXCHANGE),
                eq(RabbitMQConfig.RK_AUDIT_DEPOT),
                eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getSucces()).isFalse();
        assertThat(event.getDetails()).isEqualTo("Erreur upload");
    }

    // ── logValidation ────────────────────────────────────

    @Test
    @DisplayName("logValidation — succès → événement SOUMISSION_VALIDATION")
    void logValidation_succes() {
        auditLogService.logValidation("soum-001", "op-001", "192.168.1.1", true, "2025-06-15 14:30:00.000");

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.SOUMISSION_EXCHANGE),
                eq(RabbitMQConfig.RK_AUDIT_DEPOT),
                eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo("SOUMISSION_VALIDATION");
        assertThat(event.getIpDepot()).isEqualTo("192.168.1.1");
        assertThat(event.getSucces()).isTrue();
        assertThat(event.getHorodatage()).contains("2025-06-15");
    }

    // ── logDechiffrement ─────────────────────────────────

    @Test
    @DisplayName("logDechiffrement — succès → événement DECHIFFREMENT_PLIS")
    void logDechiffrement_succes() {
        auditLogService.logDechiffrement("ao-001", "comm-001", 5, true);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.SOUMISSION_EXCHANGE),
                eq(RabbitMQConfig.RK_AUDIT_DEPOT),
                eventCaptor.capture());

        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getType()).isEqualTo("DECHIFFREMENT_PLIS");
        assertThat(event.getAppelOffreId()).isEqualTo("ao-001");
        assertThat(event.getMembreCommissionId()).isEqualTo("comm-001");
        assertThat(event.getNombreOffres()).isEqualTo(5);
        assertThat(event.getSucces()).isTrue();
    }

    // ── Résilience RabbitMQ ──────────────────────────────

    @Test
    @DisplayName("logDepot — RabbitMQ en erreur → pas d'exception levée")
    void logDepot_rabbitDown_noException() {
        doThrow(new RuntimeException("RabbitMQ indisponible"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(AuditEvent.class));

        // Ne doit PAS lever d'exception
        assertThatCode(() -> auditLogService.logDepot(
                "soum-001", "op-001", "OFFRE_TECHNIQUE", true, "test"))
                .doesNotThrowAnyException();
    }
}
