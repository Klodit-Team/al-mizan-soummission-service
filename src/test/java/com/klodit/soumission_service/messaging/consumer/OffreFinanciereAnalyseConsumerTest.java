package com.klodit.soumission_service.messaging.consumer;

import com.klodit.soumission_service.service.OffreFinanciereService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OffreFinanciereAnalyseConsumer — Tests unitaires")
class OffreFinanciereAnalyseConsumerTest {

    @Mock
    private OffreFinanciereService offreFinanciereService;

    @InjectMocks
    private OffreFinanciereAnalyseConsumer consumer;

    @Test
    @DisplayName("Événement valide (Number) → montants mis à jour")
    void evenementValide_number_montantsMisAJour() {
        Map<String, Object> event = new HashMap<>();
        event.put("offreFinanciereId", "of-001");
        event.put("soumissionId", "soum-001");
        event.put("montantHt", 1500000.00); // Number (Double)
        event.put("tva", 285000.00);
        event.put("montantTtc", 1785000.00);
        event.put("scoreOcr", 0.95);
        event.put("observations", "Extraction réussie");

        consumer.traiterResultatAnalyseFinanciere(event);

        verify(offreFinanciereService).mettreAJourMontantsDepuisOCR(
                eq("of-001"),
                argThat(bd -> bd.doubleValue() == 1500000.00),
                argThat(bd -> bd.doubleValue() == 285000.00),
                argThat(bd -> bd.doubleValue() == 1785000.00),
                eq("Extraction réussie"));
    }

    @Test
    @DisplayName("Événement valide (String) → montants parsés correctement")
    void evenementValide_string_montantsParses() {
        Map<String, Object> event = new HashMap<>();
        event.put("offreFinanciereId", "of-002");
        event.put("soumissionId", "soum-002");
        event.put("montantHt", "2500000.50"); // String
        event.put("tva", "475000.10");
        event.put("montantTtc", "2975000.60");
        event.put("scoreOcr", 0.87);
        event.put("observations", "");

        consumer.traiterResultatAnalyseFinanciere(event);

        verify(offreFinanciereService).mettreAJourMontantsDepuisOCR(
                eq("of-002"),
                eq(new BigDecimal("2500000.50")),
                eq(new BigDecimal("475000.10")),
                eq(new BigDecimal("2975000.60")),
                eq(""));
    }

    @Test
    @DisplayName("Montants null dans l'événement → passés comme null au service")
    void montantsNull_passesCommeNull() {
        Map<String, Object> event = new HashMap<>();
        event.put("offreFinanciereId", "of-003");
        event.put("soumissionId", "soum-003");
        // montantHt, tva, montantTtc absents → null

        consumer.traiterResultatAnalyseFinanciere(event);

        verify(offreFinanciereService).mettreAJourMontantsDepuisOCR(
                eq("of-003"),
                isNull(), isNull(), isNull(),
                eq(""));
    }

    @Test
    @DisplayName("Montant String malformé → converti en null (pas de crash)")
    void montantMalforme_pasDeErreur() {
        Map<String, Object> event = new HashMap<>();
        event.put("offreFinanciereId", "of-004");
        event.put("soumissionId", "soum-004");
        event.put("montantHt", "pas-un-nombre");
        event.put("tva", 100.00);
        event.put("montantTtc", "aussi-pas-un-nombre");

        consumer.traiterResultatAnalyseFinanciere(event);

        verify(offreFinanciereService).mettreAJourMontantsDepuisOCR(
                eq("of-004"),
                isNull(), // montantHt null car malformé
                argThat(bd -> bd.doubleValue() == 100.00), // tva OK
                isNull(), // montantTtc null car malformé
                eq(""));
    }

    @Test
    @DisplayName("Exception du service → capturée sans propagation (pas de poison message)")
    void exceptionService_capturee() {
        Map<String, Object> event = new HashMap<>();
        event.put("offreFinanciereId", "of-999");
        event.put("soumissionId", "soum-999");

        doThrow(new RuntimeException("Offre introuvable"))
                .when(offreFinanciereService)
                .mettreAJourMontantsDepuisOCR(any(), any(), any(), any(), any());

        // Ne doit PAS lever d'exception (try/catch interne dans le consumer)
        assertThatCode(() -> consumer.traiterResultatAnalyseFinanciere(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ScoreOcr absent → pas de crash")
    void scoreOcrAbsent_pasDeErreur() {
        Map<String, Object> event = new HashMap<>();
        event.put("offreFinanciereId", "of-005");
        event.put("soumissionId", "soum-005");
        event.put("montantHt", 100);
        event.put("tva", 19);
        event.put("montantTtc", 119);
        // pas de scoreOcr

        assertThatCode(() -> consumer.traiterResultatAnalyseFinanciere(event))
                .doesNotThrowAnyException();

        verify(offreFinanciereService).mettreAJourMontantsDepuisOCR(
                eq("of-005"), any(), any(), any(), eq(""));
    }

    @Test
    @DisplayName("Événement avec Integer (pas Double) → converti en BigDecimal")
    void evenementAvecInteger_convertiCorrectement() {
        Map<String, Object> event = new HashMap<>();
        event.put("offreFinanciereId", "of-006");
        event.put("soumissionId", "soum-006");
        event.put("montantHt", 1000); // Integer
        event.put("tva", 190L); // Long
        event.put("montantTtc", 1190);
        event.put("observations", "Test Integer");

        consumer.traiterResultatAnalyseFinanciere(event);

        verify(offreFinanciereService).mettreAJourMontantsDepuisOCR(
                eq("of-006"),
                argThat(bd -> bd.intValue() == 1000),
                argThat(bd -> bd.intValue() == 190),
                argThat(bd -> bd.intValue() == 1190),
                eq("Test Integer"));
    }
}
