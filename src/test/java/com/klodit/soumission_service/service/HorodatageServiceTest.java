package com.klodit.soumission_service.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("HorodatageService — Tests unitaires")
class HorodatageServiceTest {

    private final HorodatageService horodatageService = new HorodatageService();

    @Test
    @DisplayName("maintenant() retourne une date non nulle")
    void maintenant_retourneDateNonNulle() {
        LocalDateTime result = horodatageService.maintenant();

        assertThat(result).isNotNull();
        assertThat(result).isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(1));
    }

    @Test
    @DisplayName("estDansDelai — avant la limite → true")
    void estDansDelai_avantLimite_true() {
        LocalDateTime dateLimite = LocalDateTime.now().plusDays(7);

        boolean result = horodatageService.estDansDelai(dateLimite);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("estDansDelai — après la limite → false")
    void estDansDelai_apresLimite_false() {
        LocalDateTime dateLimite = LocalDateTime.now().minusDays(1);

        boolean result = horodatageService.estDansDelai(dateLimite);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("formater — date valide → chaîne formatée")
    void formater_dateValide() {
        LocalDateTime date = LocalDateTime.of(2025, 6, 15, 14, 30, 45, 123000000);

        String result = horodatageService.formater(date);

        assertThat(result).isEqualTo("2025-06-15 14:30:45.123");
    }

    @Test
    @DisplayName("formater — date nulle → N/A")
    void formater_dateNulle() {
        String result = horodatageService.formater(null);

        assertThat(result).isEqualTo("N/A");
    }
}
