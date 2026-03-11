package com.klodit.soumission_service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de dégradation gracieuse du client REST AppelOffreClient.
 *
 * On construit le client avec un port invalide (99999) pour simuler
 * l'indisponibilité du Service Appels d'Offres. Chaque méthode doit
 * retourner une valeur par défaut sûre, sans lever d'exception.
 */
@DisplayName("AppelOffreClient — Dégradation gracieuse")
class AppelOffreClientTest {

    private AppelOffreClient client;

    @BeforeEach
    void setUp() {
        // Port 99999 → service garanti inaccessible
        client = new AppelOffreClient("http://localhost:99999");
    }

    @Test
    @DisplayName("getAppelOffre — service indisponible → Optional.empty()")
    void getAppelOffre_serviceDown_returnsEmpty() {
        Optional<?> result = client.getAppelOffre("ao-inexistant");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("isAppelOffrePublie — service indisponible → false")
    void isAppelOffrePublie_serviceDown_returnsFalse() {
        boolean result = client.isAppelOffrePublie("ao-inexistant");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getDateLimiteDepot — service indisponible → Optional.empty()")
    void getDateLimiteDepot_serviceDown_returnsEmpty() {
        Optional<LocalDateTime> result = client.getDateLimiteDepot("ao-inexistant");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("isCautionRequise — service indisponible → true (principe de précaution)")
    void isCautionRequise_serviceDown_returnsTrue() {
        boolean result = client.isCautionRequise("ao-inexistant");
        assertThat(result).isTrue();
    }
}
