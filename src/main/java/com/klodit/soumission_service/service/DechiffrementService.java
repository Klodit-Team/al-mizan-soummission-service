package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.request.DechiffrementRequest;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.entity.OffreFinanciere;
import com.klodit.soumission_service.exception.ChiffrementException;
import com.klodit.soumission_service.messaging.event.OffreFinanciereAnalyseDemandeEvent;
import com.klodit.soumission_service.messaging.event.OffresDecrypteesEvent;
import com.klodit.soumission_service.messaging.publisher.SoumissionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Service de déchiffrement des offres financières lors de l'ouverture des plis.
 *
 * Flux complet :
 * 1. Valider K fragments Shamir soumis par la commission
 * 2. Reconstituer la clé privée RSA
 * 3. Pour chaque offre : télécharger le ciphertext depuis MinIO
 * 4. Déchiffrer la clé AES (RSA OAEP)
 * 5. Déchiffrer le contenu (AES-256-GCM) → PDF en clair
 * 6. Stocker le PDF en clair dans MinIO (bucket offres-financieres-claires)
 * 7. Publier un événement OCR vers le Service IA pour extraction des montants
 * 8. Publier l'événement offres.dechiffrees
 *
 * Note : Les montants (montantHt, tva, montantTtc) ne sont PAS extraits ici.
 * Ils sont remplis ultérieurement par le consumer OCR
 * ({@link com.klodit.soumission_service.messaging.consumer.OffreFinanciereAnalyseConsumer})
 * après analyse du PDF par le Service IA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DechiffrementService {

    private final CleChiffrementService cleChiffrementService;
    private final OffreFinanciereService offreFinanciereService;
    private final ChiffrementService chiffrementService;
    private final MinIOService minIOService;
    private final AuditLogService auditLogService;
    private final SoumissionEventPublisher eventPublisher;
    private final MinIOProperties minIOProperties;
    private final com.klodit.soumission_service.client.AppelOffreClient appelOffreClient;

    /**
     * US-7 : Déchiffrer toutes les offres financières d'un AO.
     *
     * @param appelOffreId ID de l'appel d'offres
     * @param fragments    fragments Shamir soumis par les membres (≥ K)
     * @param membreId     ID du membre qui déclenche l'opération
     */
    /**
     * Résultat enrichi du déchiffrement : offres réussies + erreurs détaillées.
     */
    public record DechiffrementResultat(
            List<OffreFinanciereResponse> offresDecryptees,
            List<String> erreurs,
            int totalTrouvees) {
    }

    @Transactional
    public DechiffrementResultat dechiffrerOffres(
            String appelOffreId,
            List<DechiffrementRequest.FragmentSoumis> fragments,
            String membreId) {

        log.info("Début déchiffrement — AO: {}, {} fragments soumis par {}",
                appelOffreId, fragments.size(), membreId);

        // 0. Vérifier que la date d'ouverture des plis est atteinte (Loi 23-12, Art.
        // 71)
        appelOffreClient.getDateOuverturePlis(appelOffreId).ifPresent(dateOuverture -> {
            if (LocalDateTime.now().isBefore(dateOuverture)) {
                log.warn("Tentative de déchiffrement avant la date d'ouverture — AO: {}, dateOuverture: {}, par: {}",
                        appelOffreId, dateOuverture, membreId);
                auditLogService.logDechiffrement(appelOffreId, membreId, 0, false);
                throw new ChiffrementException(
                        "Le déchiffrement ne peut pas être effectué avant la date d'ouverture des plis ("
                                + dateOuverture + "). Tentative refusée conformément à la Loi 23-12.");
            }
        });

        // 1. Reconstituer la clé privée RSA depuis les fragments Shamir
        PrivateKey clePrivee;
        try {
            clePrivee = cleChiffrementService.reconstituerClePrivee(appelOffreId, fragments);
        } catch (ChiffrementException e) {
            auditLogService.logDechiffrement(appelOffreId, membreId, 0, false);
            throw e;
        }

        // 2. Récupérer toutes les offres financières non déchiffrées
        List<OffreFinanciere> offres = offreFinanciereService.getOffresNonDechiffrees(appelOffreId);
        log.info("Offres non déchiffrées trouvées pour AO {}: {}", appelOffreId, offres.size());

        if (offres.isEmpty()) {
            log.warn("Aucune offre financière à déchiffrer pour l'AO: {}", appelOffreId);
            return new DechiffrementResultat(List.of(), List.of(), 0);
        }

        List<OffreFinanciereResponse> resultats = new ArrayList<>();
        List<String> erreurs = new ArrayList<>();

        for (OffreFinanciere offre : offres) {
            try {
                // 3. Télécharger le ciphertext depuis MinIO
                String[] urlParts = offre.getFichierChiffreUrl().split("/", 2);
                String bucket = urlParts[0];
                String objectName = urlParts[1];
                log.debug("Téléchargement fichier chiffré — bucket: {}, object: {}", bucket, objectName);

                try (InputStream ciphertextStream = minIOService.telechargerFichier(bucket, objectName)) {
                    byte[] ciphertextAvecEnveloppe = ciphertextStream.readAllBytes();
                    log.debug("Fichier chiffré téléchargé — {} bytes", ciphertextAvecEnveloppe.length);

                    // 4. Désérialiser l'enveloppe chiffrée
                    EnveloppeChiffree enveloppe = deserialiserEnveloppe(ciphertextAvecEnveloppe);

                    // 5. Déchiffrer la clé AES avec la clé privée RSA
                    SecretKey cleAES = chiffrementService.dechiffrerCleAES(
                            enveloppe.cleAESChiffree(), clePrivee);

                    // 6. Déchiffrer le contenu avec AES-256-GCM → PDF en clair
                    byte[] pdfClair = chiffrementService.dechiffrerAES(
                            enveloppe.ciphertext(), enveloppe.iv(), cleAES);

                    log.info("Offre déchiffrée — soumission: {}, taille PDF clair: {} bytes",
                            offre.getSoumission().getId(), pdfClair.length);

                    // 7. Calculer le hash SHA-256 du PDF en clair (intégrité)
                    String hashPdfClair = calculerHashSHA256(pdfClair);

                    // 8. Stocker le PDF en clair dans MinIO (bucket offres-financieres-claires)
                    String bucketClair = minIOProperties.getBucket().getOffresFinancieresClaires();
                    String objectNameClair = offre.getSoumission().getId() + "/"
                            + offre.getId() + "-offre-financiere.pdf";
                    String fichierClairUrl = minIOService.uploadBytes(
                            pdfClair, bucketClair, objectNameClair);

                    log.info("PDF clair stocké dans MinIO — URL: {}", fichierClairUrl);

                    // 9. Mettre à jour l'entité : marquée déchiffrée + URL du fichier clair
                    offre.setFichierClairUrl(fichierClairUrl);
                    offreFinanciereService.mettreAJourApresDecryptage(offre);

                    // 10. Publier la demande d'analyse OCR vers le Service IA
                    // → Le Service IA fera l'OCR du PDF pour extraire montantHt, tva, montantTtc
                    eventPublisher.publierDemandeAnalyseOffreFinanciere(
                            OffreFinanciereAnalyseDemandeEvent.builder()
                                    .soumissionId(offre.getSoumission().getId())
                                    .offreFinanciereId(offre.getId())
                                    .fichierClairUrl(fichierClairUrl)
                                    .hashFichierClair(hashPdfClair)
                                    .appelOffreId(appelOffreId)
                                    .operateurId(offre.getSoumission().getOperateurId())
                                    .build());

                    log.info("Demande OCR publiée pour offre financière {} — soumission: {}",
                            offre.getId(), offre.getSoumission().getId());
                }

                resultats.add(toResponse(offre));

            } catch (Exception e) {
                String errMsg = String.format("Offre %s : %s", offre.getId(), e.getMessage());
                log.error("Échec déchiffrement — {}", errMsg, e);
                erreurs.add(errMsg);
            }
        }

        // 11. Log d'audit
        auditLogService.logDechiffrement(appelOffreId, membreId, resultats.size(), true);

        // 12. Publier l'événement offres.dechiffrees (asynchrone)
        eventPublisher.publierOffresDecryptees(OffresDecrypteesEvent.builder()
                .appelOffreId(appelOffreId)
                .nombreOffres(resultats.size())
                .soumissionIds(offres.stream()
                        .map(of -> of.getSoumission().getId())
                        .toList())
                .declencheParId(membreId)
                .build());

        log.info("Déchiffrement terminé — AO: {}, {}/{} offres déchiffrées, {} erreurs. "
                + "Les montants seront extraits par OCR (Service IA)",
                appelOffreId, resultats.size(), offres.size(), erreurs.size());
        return new DechiffrementResultat(resultats, erreurs, offres.size());
    }

    // ── Helpers ───────────────────────────────────────────

    /**
     * Format de l'enveloppe chiffrée :
     * [4 bytes : longueur cleAESChiffree] [cleAESChiffree] [12 bytes : IV]
     * [ciphertext]
     */
    private EnveloppeChiffree deserialiserEnveloppe(byte[] data) {
        try {
            int offset = 0;
            int longueurCle = ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            offset += 4;

            byte[] cleAESChiffree = new byte[longueurCle];
            System.arraycopy(data, offset, cleAESChiffree, 0, longueurCle);
            offset += longueurCle;

            byte[] iv = new byte[12];
            System.arraycopy(data, offset, iv, 0, 12);
            offset += 12;

            byte[] ciphertext = new byte[data.length - offset];
            System.arraycopy(data, offset, ciphertext, 0, ciphertext.length);

            return new EnveloppeChiffree(cleAESChiffree, iv, ciphertext);
        } catch (Exception e) {
            throw new ChiffrementException("Format de l'enveloppe chiffrée invalide : " + e.getMessage());
        }
    }

    /**
     * Calcule le hash SHA-256 d'un tableau de bytes.
     */
    private String calculerHashSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Impossible de calculer le hash SHA-256 : {}", e.getMessage());
            return "hash-non-disponible";
        }
    }

    private OffreFinanciereResponse toResponse(OffreFinanciere of) {
        return OffreFinanciereResponse.builder()
                .id(of.getId())
                .fichierChiffreUrl(of.getFichierChiffreUrl())
                .fichierClairUrl(of.getFichierClairUrl())
                .hashFichier(of.getHashFichier())
                .montantHt(of.getMontantHt())
                .tva(of.getTva())
                .montantTtc(of.getMontantTtc())
                .isDechiffree(of.getIsDechiffree())
                .dateDechiffrement(of.getDateDechiffrement())
                .createdAt(of.getCreatedAt())
                .build();
    }

    // Record pour l'enveloppe chiffrée
    private record EnveloppeChiffree(byte[] cleAESChiffree, byte[] iv, byte[] ciphertext) {
    }
}
