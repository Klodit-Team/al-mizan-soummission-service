package com.klodit.soumission_service.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Utilitaires d'horodatage partagés.
 * Complète HorodatageService (Service Spring) pour les cas
 * où l'on a besoin de méthodes statiques (entités, DTOs, logs).
 *
 * Fuseau officiel : Africa/Algiers (UTC+1) — Loi n°23-12.
 */
public final class DateTimeUtils {

    public static final ZoneId ZONE_ALGERIE = ZoneId.of("Africa/Algiers");
    public static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    public static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private DateTimeUtils() {
    }

    /**
     * Retourne l'heure courante dans le fuseau algérien.
     */
    public static LocalDateTime maintenantAlgerie() {
        return ZonedDateTime.now(ZONE_ALGERIE).toLocalDateTime();
    }

    /**
     * Formate un LocalDateTime en ISO lisible (yyyy-MM-dd'T'HH:mm:ss.SSS).
     */
    public static String formaterIso(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_FORMATTER) : "N/A";
    }

    /**
     * Formate un LocalDateTime en format d'affichage (dd/MM/yyyy HH:mm:ss).
     */
    public static String formaterAffichage(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DISPLAY_FORMATTER) : "N/A";
    }

    /**
     * Calcule la différence en minutes entre deux instants.
     */
    public static long differenceMinutes(LocalDateTime debut, LocalDateTime fin) {
        return ChronoUnit.MINUTES.between(debut, fin);
    }

    /**
     * Vérifie si un instant est dans le futur (par rapport au fuseau algérien).
     */
    public static boolean estDansLeFutur(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isAfter(maintenantAlgerie());
    }
}
