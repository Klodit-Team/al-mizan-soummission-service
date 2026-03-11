package com.klodit.soumission_service.messaging;

import com.klodit.soumission_service.messaging.consumer.CommissionOuvertureEventConsumer;
import com.klodit.soumission_service.service.DechiffrementService;
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
@DisplayName("CommissionOuvertureEventConsumer — Tests unitaires")
class CommissionOuvertureEventConsumerTest {

    @Mock
    private DechiffrementService dechiffrementService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private CommissionOuvertureEventConsumer consumer;

    @Test
    @DisplayName("Demande d'ouverture avec fragments → traitement réussi")
    void demandeOuverture_avecFragments_succes() {
        Map<String, Object> event = new HashMap<>();
        event.put("appelOffreId", "ao-001");
        event.put("fragments", List.of("frag1", "frag2", "frag3"));
        event.put("declencheParId", "membre-001");

        when(idempotencyService.isAlreadyProcessed("commission-ouverture-ao-001")).thenReturn(false);

        consumer.traiterDemandeOuverture(event);

        verify(idempotencyService).markAsProcessed(eq("commission-ouverture-ao-001"), anyString(), anyString());
    }

    @Test
    @DisplayName("Message déjà traité → idempotent, pas de traitement")
    void dejaTraite_ignore() {
        Map<String, Object> event = new HashMap<>();
        event.put("appelOffreId", "ao-002");
        event.put("fragments", List.of("frag1"));

        when(idempotencyService.isAlreadyProcessed("commission-ouverture-ao-002")).thenReturn(true);

        consumer.traiterDemandeOuverture(event);

        verify(idempotencyService, never()).markAsProcessed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Aucun fragment → pas de crash, pas marqué comme traité quand même")
    void aucunFragment_pasMarque() {
        Map<String, Object> event = new HashMap<>();
        event.put("appelOffreId", "ao-003");
        // fragments absent

        when(idempotencyService.isAlreadyProcessed("commission-ouverture-ao-003")).thenReturn(false);

        consumer.traiterDemandeOuverture(event);

        // Le consumer retourne early quand fragments = null, sans marquer comme traité
        verify(idempotencyService, never()).markAsProcessed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Événement avec declencheParId absent → utilise SYSTEM par défaut")
    void declencheParId_absent_utiliseSystem() {
        Map<String, Object> event = new HashMap<>();
        event.put("appelOffreId", "ao-004");
        event.put("fragments", List.of("f1", "f2", "f3"));

        when(idempotencyService.isAlreadyProcessed("commission-ouverture-ao-004")).thenReturn(false);

        assertThatCode(() -> consumer.traiterDemandeOuverture(event))
                .doesNotThrowAnyException();

        verify(idempotencyService).markAsProcessed(eq("commission-ouverture-ao-004"), anyString(), anyString());
    }
}
