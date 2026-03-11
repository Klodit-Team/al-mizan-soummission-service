package com.klodit.soumission_service.messaging;

import com.klodit.soumission_service.entity.OffreTechnique;
import com.klodit.soumission_service.messaging.consumer.IaAnalyseEventConsumer;
import com.klodit.soumission_service.messaging.event.IaAnalyseTermineeEvent;
import com.klodit.soumission_service.repository.OffreTechniqueRepository;
import com.klodit.soumission_service.service.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IaAnalyseEventConsumer — Tests unitaires")
class IaAnalyseEventConsumerTest {

    @Mock
    private OffreTechniqueRepository offreTechniqueRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private IaAnalyseEventConsumer consumer;

    private IaAnalyseTermineeEvent buildEvent(String soumissionId, Boolean isConforme,
            Double scoreOcr, String timestamp) {
        return IaAnalyseTermineeEvent.builder()
                .soumissionId(soumissionId)
                .isConforme(isConforme)
                .scoreOcr(scoreOcr)
                .timestamp(timestamp)
                .build();
    }

    @Test
    @DisplayName("Résultat IA conforme → offre technique mise à jour avec isConforme=true")
    void resultatConforme() {
        IaAnalyseTermineeEvent event = buildEvent("s-001", true, 98.5, "2024-01-01T00:00:00");
        OffreTechnique ot = new OffreTechnique();

        when(idempotencyService.isAlreadyProcessed("ia-analyse-s-001-2024-01-01T00:00:00")).thenReturn(false);
        when(offreTechniqueRepository.findBySoumissionId("s-001")).thenReturn(Optional.of(ot));

        consumer.traiterAnalyseTerminee(event);

        assertThat(ot.getIsConforme()).isTrue();
        verify(offreTechniqueRepository).save(ot);
        verify(idempotencyService).markAsProcessed(eq("ia-analyse-s-001-2024-01-01T00:00:00"), anyString(),
                anyString());
    }

    @Test
    @DisplayName("Résultat IA non conforme → offre technique mise à jour avec isConforme=false")
    void resultatNonConforme() {
        IaAnalyseTermineeEvent event = buildEvent("s-002", false, 75.0, "2024-01-02T00:00:00");
        OffreTechnique ot = new OffreTechnique();

        when(idempotencyService.isAlreadyProcessed("ia-analyse-s-002-2024-01-02T00:00:00")).thenReturn(false);
        when(offreTechniqueRepository.findBySoumissionId("s-002")).thenReturn(Optional.of(ot));

        consumer.traiterAnalyseTerminee(event);

        assertThat(ot.getIsConforme()).isFalse();
        assertThat(ot.getObservations()).contains("75.0").contains("Non conforme");
        verify(offreTechniqueRepository).save(ot);
    }

    @Test
    @DisplayName("Offre technique introuvable → pas de save, mais marqué comme traité")
    void offreTechniqueAbsente() {
        IaAnalyseTermineeEvent event = buildEvent("s-003", true, 92.0, "2024-01-03T00:00:00");

        when(idempotencyService.isAlreadyProcessed("ia-analyse-s-003-2024-01-03T00:00:00")).thenReturn(false);
        when(offreTechniqueRepository.findBySoumissionId("s-003")).thenReturn(Optional.empty());

        consumer.traiterAnalyseTerminee(event);

        verify(offreTechniqueRepository, never()).save(any());
        verify(idempotencyService).markAsProcessed(eq("ia-analyse-s-003-2024-01-03T00:00:00"), anyString(),
                anyString());
    }

    @Test
    @DisplayName("Message déjà traité → idempotent, pas de traitement")
    void dejaTraite_ignore() {
        IaAnalyseTermineeEvent event = buildEvent("s-004", true, 95.0, "2024-01-04T00:00:00");

        when(idempotencyService.isAlreadyProcessed("ia-analyse-s-004-2024-01-04T00:00:00")).thenReturn(true);

        consumer.traiterAnalyseTerminee(event);

        verify(offreTechniqueRepository, never()).findBySoumissionId(anyString());
        verify(offreTechniqueRepository, never()).save(any());
    }

    @Test
    @DisplayName("Score OCR inclus dans les observations")
    void scoreOcrDansObservations() {
        IaAnalyseTermineeEvent event = buildEvent("s-005", true, 97.3, "2024-01-05T00:00:00");
        OffreTechnique ot = new OffreTechnique();

        when(idempotencyService.isAlreadyProcessed("ia-analyse-s-005-2024-01-05T00:00:00")).thenReturn(false);
        when(offreTechniqueRepository.findBySoumissionId("s-005")).thenReturn(Optional.of(ot));

        consumer.traiterAnalyseTerminee(event);

        assertThat(ot.getObservations()).contains("Score OCR: 97.3%").contains("Conforme");
    }
}
