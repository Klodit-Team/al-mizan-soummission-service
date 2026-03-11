package com.klodit.soumission_service.messaging.consumer;

import com.klodit.soumission_service.config.RabbitMQConfig;
import com.klodit.soumission_service.service.OffreFinanciereService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consommateur de l'événement retour d'analyse OCR des offres financières.
 *
 * Reçoit le résultat de l'analyse OCR du PDF en clair depuis le Service IA :
 * - montantHt, tva, montantTtc extraits du document
 * - scoreOcr : précision de l'extraction
 * - observations : remarques éventuelles
 *
 * Met à jour les champs montantHt, tva, montantTtc dans la table offres_financieres.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OffreFinanciereAnalyseConsumer {

    private final OffreFinanciereService offreFinanciereService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_OFFRE_FINANCIERE_ANALYSE_TERMINEE)
    public void traiterResultatAnalyseFinanciere(Map<String, Object> event) {
        try {
            String offreFinanciereId = (String) event.get("offreFinanciereId");
            String soumissionId = (String) event.get("soumissionId");

            BigDecimal montantHt = extraireBigDecimal(event, "montantHt");
            BigDecimal tva = extraireBigDecimal(event, "tva");
            BigDecimal montantTtc = extraireBigDecimal(event, "montantTtc");

            Double scoreOcr = event.get("scoreOcr") != null
                    ? ((Number) event.get("scoreOcr")).doubleValue()
                    : null;
            String observations = (String) event.getOrDefault("observations", "");

            log.info("Résultat OCR offre financière reçu — OF: {}, soumission: {}, "
                            + "montantHt: {}, tva: {}, montantTtc: {}, précision: {}%",
                    offreFinanciereId, soumissionId, montantHt, tva, montantTtc, scoreOcr);

            // Mettre à jour les montants en base
            offreFinanciereService.mettreAJourMontantsDepuisOCR(
                    offreFinanciereId, montantHt, tva, montantTtc, observations);

            log.info("Montants mis à jour pour offre financière {} — OCR terminé", offreFinanciereId);

        } catch (Exception e) {
            log.error("Erreur traitement résultat OCR offre financière : {}", e.getMessage(), e);
            // Ne pas relancer — laisser le message expirer ou aller en DLQ
        }
    }

    /**
     * Extrait un BigDecimal depuis la map d'événement (supporte Number et String).
     */
    private BigDecimal extraireBigDecimal(Map<String, Object> event, String champ) {
        Object valeur = event.get(champ);
        if (valeur == null) {
            return null;
        }
        if (valeur instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (valeur instanceof String str) {
            try {
                return new BigDecimal(str);
            } catch (NumberFormatException e) {
                log.warn("Impossible de parser {} comme BigDecimal : {}", champ, str);
                return null;
            }
        }
        return null;
    }
}
