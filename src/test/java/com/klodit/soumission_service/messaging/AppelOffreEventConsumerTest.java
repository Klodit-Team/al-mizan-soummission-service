package com.klodit.soumission_service.messaging;

import com.klodit.soumission_service.messaging.consumer.AppelOffreEventConsumer;
import com.klodit.soumission_service.messaging.event.AppelOffrePublieEvent;
import com.klodit.soumission_service.service.CleChiffrementService;
import com.klodit.soumission_service.service.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppelOffreEventConsumer — Tests unitaires")
class AppelOffreEventConsumerTest {

    @Mock
    private CleChiffrementService cleChiffrementService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private AppelOffreEventConsumer consumer;

    // ── traiterAoPublie ──────────────────────────────────

    @Test
    @DisplayName("AO publié avec membres → génération de clés")
    void aoPublie_avecMembres_genereLesCles() {
        AppelOffrePublieEvent event = AppelOffrePublieEvent.builder()
                .appelOffreId("ao-001")
                .membresCommissionIds(List.of("m1", "m2", "m3"))
                .build();

        when(idempotencyService.isAlreadyProcessed("ao-publie-ao-001")).thenReturn(false);

        consumer.traiterAoPublie(event);

        verify(cleChiffrementService).genererCles("ao-001", List.of("m1", "m2", "m3"));
        verify(idempotencyService).markAsProcessed(eq("ao-publie-ao-001"), anyString(), anyString());
    }

    @Test
    @DisplayName("AO publié sans membres → pas de génération de clés")
    void aoPublie_sansMembres_pasDeGeneration() {
        AppelOffrePublieEvent event = AppelOffrePublieEvent.builder()
                .appelOffreId("ao-002")
                .membresCommissionIds(List.of())
                .build();

        when(idempotencyService.isAlreadyProcessed("ao-publie-ao-002")).thenReturn(false);

        consumer.traiterAoPublie(event);

        verify(cleChiffrementService, never()).genererCles(anyString(), anyList());
    }

    @Test
    @DisplayName("AO publié avec null membres → pas de génération de clés")
    void aoPublie_nullMembres_pasDeGeneration() {
        AppelOffrePublieEvent event = AppelOffrePublieEvent.builder()
                .appelOffreId("ao-003")
                .membresCommissionIds(null)
                .build();

        when(idempotencyService.isAlreadyProcessed("ao-publie-ao-003")).thenReturn(false);

        consumer.traiterAoPublie(event);

        verify(cleChiffrementService, never()).genererCles(anyString(), anyList());
    }

    @Test
    @DisplayName("Exception dans genererCles → propagée (idempotence : pas marqué comme traité)")
    void aoPublie_exception_propagee() {
        AppelOffrePublieEvent event = AppelOffrePublieEvent.builder()
                .appelOffreId("ao-004")
                .membresCommissionIds(List.of("m1", "m2", "m3"))
                .build();

        when(idempotencyService.isAlreadyProcessed("ao-publie-ao-004")).thenReturn(false);
        when(cleChiffrementService.genererCles(anyString(), anyList()))
                .thenThrow(new RuntimeException("Test exception"));

        assertThatThrownBy(() -> consumer.traiterAoPublie(event))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyService, never()).markAsProcessed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Message déjà traité → idempotent, pas de traitement")
    void aoPublie_dejaTraite_ignore() {
        AppelOffrePublieEvent event = AppelOffrePublieEvent.builder()
                .appelOffreId("ao-005")
                .membresCommissionIds(List.of("m1", "m2"))
                .build();

        when(idempotencyService.isAlreadyProcessed("ao-publie-ao-005")).thenReturn(true);

        consumer.traiterAoPublie(event);

        verify(cleChiffrementService, never()).genererCles(anyString(), anyList());
    }

    // ── traiterAoCloture ─────────────────────────────────

    @Test
    @DisplayName("AO clôturé → exécution sans erreur, marqué traité")
    void aoCloture_sanErreur() {
        Map<String, Object> event = new HashMap<>();
        event.put("appelOffreId", "ao-001");

        when(idempotencyService.isAlreadyProcessed("ao-cloture-ao-001")).thenReturn(false);

        assertThatCode(() -> consumer.traiterAoCloture(event))
                .doesNotThrowAnyException();

        verify(idempotencyService).markAsProcessed(eq("ao-cloture-ao-001"), anyString(), anyString());
    }

    @Test
    @DisplayName("AO clôturé déjà traité → idempotent")
    void aoCloture_dejaTraite() {
        Map<String, Object> event = new HashMap<>();
        event.put("appelOffreId", "ao-002");

        when(idempotencyService.isAlreadyProcessed("ao-cloture-ao-002")).thenReturn(true);

        consumer.traiterAoCloture(event);

        verify(idempotencyService, never()).markAsProcessed(anyString(), anyString(), anyString());
    }
}
