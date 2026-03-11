package com.klodit.soumission_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service d'horodatage légal.
 * La date/heure du SERVEUR fait foi selon la Loi n°23-12.
 * Toutes les opérations critiques (dépôt, validation) utilisent ce service.
 */
@Service
@Slf4j
public class HorodatageService {

    // Fuseau horaire officiel algérien (UTC+1)
    private static final ZoneId ZONE_ALGERIE = ZoneId.of("Africa/Algiers");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Retourne l'horodatage légal courant (heure serveur, fuseau Algérie).
     */
    public LocalDateTime maintenant() {
        LocalDateTime ts = ZonedDateTime.now(ZONE_ALGERIE).toLocalDateTime();
        log.debug("Horodatage légal : {}", ts.format(FORMATTER));
        return ts;
    }

    /**
     * Vérifie si l'instant courant est dans le délai de dépôt.
     *
     * @param dateLimiteDepot la date limite de dépôt de l'AO
     * @return true si on est encore dans le délai
     */
    public boolean estDansDelai(LocalDateTime dateLimiteDepot) {
        LocalDateTime maintenant = maintenant();
        boolean dansDelai = maintenant.isBefore(dateLimiteDepot);
        log.info("Vérification délai — maintenant: {}, limite: {}, dans délai: {}",
                maintenant.format(FORMATTER), dateLimiteDepot.format(FORMATTER), dansDelai);
        return dansDelai;
    }

    /**
     * Formate un LocalDateTime en chaîne lisible (pour les logs et réponses API).
     */
    public String formater(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FORMATTER) : "N/A";
    }
}
