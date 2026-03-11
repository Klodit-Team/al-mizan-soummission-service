package com.klodit.soumission_service.controller;

import com.klodit.soumission_service.dto.response.ApiResponse;
import com.klodit.soumission_service.entity.CleChiffrement;
import com.klodit.soumission_service.entity.OffreFinanciere;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.repository.CleChiffrementRepository;
import com.klodit.soumission_service.repository.OffreFinanciereRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import com.klodit.soumission_service.service.ChiffrementService;
import com.klodit.soumission_service.service.MinIOService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur DEV UNIQUEMENT — génère des données de test chiffrées
 * pour permettre la démonstration du flux de déchiffrement (ouverture des
 * plis).
 *
 * Active seulement avec le profil "dev".
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
@Tag(name = "Dev / Test", description = "Endpoints de test (profil dev uniquement)")
public class DevTestController {

        private final SoumissionRepository soumissionRepository;
        private final OffreFinanciereRepository offreFinanciereRepository;
        private final CleChiffrementRepository cleChiffrementRepository;
        private final ChiffrementService chiffrementService;
        private final MinIOService minIOService;

        /**
         * Génère une offre financière RÉELLEMENT chiffrée pour une soumission
         * existante.
         *
         * Flux :
         * 1. Récupère la clé publique RSA de l'AO
         * 2. Crée un contenu PDF simulé (en dev, il s'agit d'un faux PDF)
         * 3. Chiffre avec AES-256-GCM + RSA-4096
         * 4. Construit l'enveloppe binaire
         * 5. Upload vers MinIO
         * 6. Met à jour l'offre financière en base
         *
         * Cela permet ensuite de tester le déchiffrement via POST /dechiffrer/{aoId}.
         * Après déchiffrement, le PDF sera stocké en clair dans MinIO et
         * un événement OCR sera envoyé au Service IA pour extraction des montants.
         */
        @PostMapping("/api/v1/dev/generer-offre-chiffree/{soumissionId}")
        @Operation(summary = "[DEV] Générer une offre financière réellement chiffrée", description = "Remplace le fichier chiffré de l'offre par un fichier correctement "
                        + "chiffré avec la clé RSA de l'AO. Utilisé pour tester le déchiffrement. "
                        + "Le contenu est un faux PDF simulé pour les tests.")
        public ResponseEntity<ApiResponse<Map<String, Object>>> genererOffreChiffree(
                        @PathVariable String soumissionId,
                        @RequestParam(defaultValue = "1500000.00") String montantHt,
                        @RequestParam(defaultValue = "285000.00") String tva,
                        @RequestParam(defaultValue = "1785000.00") String montantTtc) {

                // 1. Récupérer la soumission
                Soumission soumission = soumissionRepository.findById(soumissionId)
                                .orElseThrow(
                                                () -> new com.klodit.soumission_service.exception.SoumissionNotFoundException(
                                                                soumissionId));

                // 2. Récupérer l'offre financière existante
                OffreFinanciere offre = offreFinanciereRepository.findBySoumissionId(soumissionId)
                                .orElseThrow(() -> new com.klodit.soumission_service.exception.RessourceIntrouvableException(
                                                "Offre financière", "soumission " + soumissionId));

                // 3. Récupérer la clé publique RSA de l'AO
                String appelOffreId = soumission.getAppelOffreId();
                CleChiffrement cleChiffrement = cleChiffrementRepository.findByAppelOffreId(appelOffreId)
                                .orElseThrow(() -> new com.klodit.soumission_service.exception.RessourceIntrouvableException(
                                                "Clé de chiffrement", "appel d'offres " + appelOffreId));

                PublicKey clePubliqueRSA = chiffrementService.reconstruireClePublique(cleChiffrement.getClePublique());
                log.info("[DEV] Clé publique RSA récupérée pour AO: {}", appelOffreId);

                // 4. Créer un contenu PDF simulé pour les tests (en prod, c'est un vrai PDF)
                // Le Service IA effectuera l'OCR pour extraire les montants du PDF
                String contenuSimulePdf = "%PDF-1.4 [SIMULATED]\n"
                                + "Offre Financière — Soumission " + soumissionId + "\n"
                                + "Montant HT : " + montantHt + " DZD\n"
                                + "TVA : " + tva + " DZD\n"
                                + "Montant TTC : " + montantTtc + " DZD\n"
                                + "%%EOF";
                byte[] plaintext = contenuSimulePdf.getBytes(StandardCharsets.UTF_8);
                log.info("[DEV] Contenu PDF simulé généré — {} bytes", plaintext.length);

                // 5. Chiffrer avec AES-256-GCM
                SecretKey cleAES = chiffrementService.genererCleAES();
                byte[][] resultatAES = chiffrementService.chiffrerAES(plaintext, cleAES);
                byte[] ciphertext = resultatAES[0];
                byte[] iv = resultatAES[1];

                // 6. Chiffrer la clé AES avec RSA-4096 OAEP
                byte[] cleAESChiffree = chiffrementService.chiffrerCleAES(cleAES, clePubliqueRSA);

                // 7. Construire l'enveloppe : [4 bytes len][cleAESChiffree][12 bytes
                // IV][ciphertext]
                ByteBuffer enveloppe = ByteBuffer.allocate(4 + cleAESChiffree.length + iv.length + ciphertext.length);
                enveloppe.putInt(cleAESChiffree.length);
                enveloppe.put(cleAESChiffree);
                enveloppe.put(iv);
                enveloppe.put(ciphertext);
                byte[] enveloppeBytes = enveloppe.array();

                log.info(
                                "[DEV] Enveloppe construite — {} bytes (cleAES chiffrée: {} bytes, IV: {} bytes, ciphertext: {} bytes)",
                                enveloppeBytes.length, cleAESChiffree.length, iv.length, ciphertext.length);

                // 8. Upload vers MinIO (bucket offres-financieres)
                String objectName = soumissionId + "/" + UUID.randomUUID() + "-offre-chiffree-test.bin";
                String fichierUrl = minIOService.uploadBytes(enveloppeBytes, "offres-financieres", objectName);

                // 9. Mettre à jour l'offre en base
                String ancienneUrl = offre.getFichierChiffreUrl();
                offre.setFichierChiffreUrl(fichierUrl);
                offre.setFichierClairUrl(null); // Réinitialiser — sera rempli après déchiffrement
                offre.setIsDechiffree(false);
                offre.setDateDechiffrement(null);
                offre.setMontantHt(null);
                offre.setTva(null);
                offre.setMontantTtc(null);

                // Recalculer le hash du fichier chiffré
                try {
                        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                        byte[] hashBytes = digest.digest(enveloppeBytes);
                        String hash = java.util.HexFormat.of().formatHex(hashBytes);
                        offre.setHashFichier(hash);
                } catch (Exception e) {
                        log.warn("[DEV] Impossible de calculer le hash : {}", e.getMessage());
                }

                offreFinanciereRepository.save(offre);
                log.info("[DEV] Offre financière mise à jour — ancienne URL: {}, nouvelle URL: {}",
                                ancienneUrl, fichierUrl);

                // 10. Réponse
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("soumissionId", soumissionId);
                data.put("appelOffreId", appelOffreId);
                data.put("offreId", offre.getId());
                data.put("contenuSimulePdf", contenuSimulePdf);
                data.put("fichierChiffreUrl", fichierUrl);
                data.put("tailleFichier", enveloppeBytes.length + " bytes");
                data.put("tailleCleAESChiffree", cleAESChiffree.length + " bytes");
                data.put("message", "PDF simulé chiffré avec la clé RSA de l'AO. "
                                + "Vous pouvez maintenant tester POST /dechiffrer/" + appelOffreId
                                + ". Après déchiffrement, le PDF sera stocké en clair et un événement OCR sera envoyé.");

                return ResponseEntity.ok(ApiResponse.ok(data, "Offre financière chiffrée générée avec succès"));
        }
}
