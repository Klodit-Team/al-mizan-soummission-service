package com.klodit.soumission_service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DateTimeUtils — Tests unitaires")
class DateTimeUtilsTest {

    @Test
    @DisplayName("maintenantAlgerie → retourne une date non nulle")
    void maintenantAlgerie() {
        LocalDateTime now = DateTimeUtils.maintenantAlgerie();
        assertThat(now).isNotNull();
        assertThat(now).isBeforeOrEqualTo(LocalDateTime.now().plusHours(2));
    }

    @Test
    @DisplayName("formaterIso → format ISO correct")
    void formaterIso() {
        LocalDateTime dt = LocalDateTime.of(2026, 3, 15, 14, 30, 45, 123_000_000);
        assertThat(DateTimeUtils.formaterIso(dt)).isEqualTo("2026-03-15T14:30:45.123");
    }

    @Test
    @DisplayName("formaterIso null → retourne N/A")
    void formaterIso_null() {
        assertThat(DateTimeUtils.formaterIso(null)).isEqualTo("N/A");
    }

    @Test
    @DisplayName("formaterAffichage → format d'affichage correct")
    void formaterAffichage() {
        LocalDateTime dt = LocalDateTime.of(2026, 3, 15, 14, 30, 45);
        assertThat(DateTimeUtils.formaterAffichage(dt)).isEqualTo("15/03/2026 14:30:45");
    }

    @Test
    @DisplayName("formaterAffichage null → retourne N/A")
    void formaterAffichage_null() {
        assertThat(DateTimeUtils.formaterAffichage(null)).isEqualTo("N/A");
    }

    @Test
    @DisplayName("differenceMinutes → calcul correct")
    void differenceMinutes() {
        LocalDateTime debut = LocalDateTime.of(2026, 3, 15, 10, 0);
        LocalDateTime fin = LocalDateTime.of(2026, 3, 15, 10, 45);
        assertThat(DateTimeUtils.differenceMinutes(debut, fin)).isEqualTo(45);
    }

    @Test
    @DisplayName("estDansLeFutur → vrai pour date future")
    void estDansLeFutur_vrai() {
        LocalDateTime future = LocalDateTime.now().plusYears(10);
        assertThat(DateTimeUtils.estDansLeFutur(future)).isTrue();
    }

    @Test
    @DisplayName("estDansLeFutur → faux pour date passée")
    void estDansLeFutur_faux() {
        LocalDateTime past = LocalDateTime.of(2020, 1, 1, 0, 0);
        assertThat(DateTimeUtils.estDansLeFutur(past)).isFalse();
    }

    @Test
    @DisplayName("estDansLeFutur null → retourne false")
    void estDansLeFutur_null() {
        assertThat(DateTimeUtils.estDansLeFutur(null)).isFalse();
    }
}
