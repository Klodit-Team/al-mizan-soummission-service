package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.request.DepotOffreFinanciereRequest;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.entity.LigneOffreFinanciere;
import com.klodit.soumission_service.entity.OffreFinanciere;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.*;
import com.klodit.soumission_service.repository.LigneOffreFinanciereRepository;
import com.klodit.soumission_service.repository.OffreFinanciereRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("OffreFinanciereService — Tests unitaires")
class OffreFinanciereServiceTest {

        @Mock
        private SoumissionRepository soumissionRepository;
        @Mock
        private OffreFinanciereRepository offreFinanciereRepository;
        @Mock
        private LigneOffreFinanciereRepository ligneOffreFinanciereRepository;
        @Mock
        private MinIOService minIOService;
        @Mock
        private HashService hashService;
        @Mock
        private ChiffrementService chiffrementService;
        @Mock
        private AuditLogService auditLogService;
        @Mock
        private MinIOProperties minIOProperties;

        @InjectMocks
        private OffreFinanciereService offreFinanciereService;

        // ── mettreAJourMontantsDepuisOCR ──────────────────────

        @Nested
        @DisplayName("mettreAJourMontantsDepuisOCR")
        class MettreAJourMontantsDepuisOCR {

                @Test
                @DisplayName("Succès — montants OCR mis à jour sur offre déjà déchiffrée")
                void succes_montantsOcrMisAJour() {
                        Soumission soumission = Soumission.builder()
                                        .id("soum-001")
                                        .operateurId("op-001")
                                        .build();

                        OffreFinanciere offre = OffreFinanciere.builder()
                                        .id("of-001")
                                        .soumission(soumission)
                                        .fichierChiffreUrl("offres-financieres/test/fichier.enc")
                                        .fichierClairUrl(
                                                         "offres-financieres-claires/soum-001/of-001-offre-financiere.pdf")
                                        .hashFichier("hash")
                                        .isDechiffree(true)
                                        .dateDechiffrement(LocalDateTime.now())
                                        .build();

                        when(offreFinanciereRepository.findById("of-001")).thenReturn(Optional.of(offre));
                        when(offreFinanciereRepository.save(any())).thenAnswer(i -> i.getArgument(0));

                        BigDecimal ht = new BigDecimal("1500000.00");
                        BigDecimal tva = new BigDecimal("285000.00");
                        BigDecimal ttc = new BigDecimal("1785000.00");

                        // Act
                        offreFinanciereService.mettreAJourMontantsDepuisOCR(
                                         "of-001", ht, tva, ttc, "OCR score 95%");

                        // Assert
                        assertThat(offre.getMontantHt()).isEqualByComparingTo(ht);
                        assertThat(offre.getTva()).isEqualByComparingTo(tva);
                        assertThat(offre.getMontantTtc()).isEqualByComparingTo(ttc);

                        verify(offreFinanciereRepository).save(offre);
                        verify(auditLogService).logDepot(
                                         eq("soum-001"), eq("op-001"),
                                         eq("OFFRE_FINANCIERE_OCR"), eq(true),
                                         contains("1500000"));
                }

                @Test
                @DisplayName("Offre introuvable → RessourceIntrouvableException")
                void offreIntrouvable_exception() {
                        when(offreFinanciereRepository.findById("of-inexistant"))
                                         .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> offreFinanciereService.mettreAJourMontantsDepuisOCR(
                                         "of-inexistant",
                                         BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, null))
                                         .isInstanceOf(RessourceIntrouvableException.class);

                        verify(offreFinanciereRepository, never()).save(any());
                }

                @Test
                @DisplayName("Offre non déchiffrée → IllegalStateException")
                void offreNonDechiffree_exception() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-002")
                                         .operateurId("op-002")
                                         .build();

                        OffreFinanciere offre = OffreFinanciere.builder()
                                         .id("of-002")
                                         .soumission(soumission)
                                         .fichierChiffreUrl("url")
                                         .hashFichier("hash")
                                         .isDechiffree(false) // PAS encore déchiffrée
                                         .build();

                        when(offreFinanciereRepository.findById("of-002"))
                                         .thenReturn(Optional.of(offre));

                        assertThatThrownBy(() -> offreFinanciereService.mettreAJourMontantsDepuisOCR(
                                         "of-002",
                                         new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"),
                                         null))
                                         .isInstanceOf(IllegalStateException.class)
                                         .hasMessageContaining("pas encore été déchiffrée");

                        // Montants NE doivent PAS avoir été mis à jour
                        assertThat(offre.getMontantHt()).isNull();
                        verify(offreFinanciereRepository, never()).save(any());
                }

                @Test
                @DisplayName("Observations nulles → pas de crash, audit OK")
                void observationsNulles_pasDeErreur() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-003")
                                         .operateurId("op-003")
                                         .build();

                        OffreFinanciere offre = OffreFinanciere.builder()
                                         .id("of-003")
                                         .soumission(soumission)
                                         .fichierChiffreUrl("url")
                                         .hashFichier("hash")
                                         .isDechiffree(true)
                                         .dateDechiffrement(LocalDateTime.now())
                                         .build();

                        when(offreFinanciereRepository.findById("of-003"))
                                         .thenReturn(Optional.of(offre));
                        when(offreFinanciereRepository.save(any())).thenAnswer(i -> i.getArgument(0));

                        // Act — observations = null
                        offreFinanciereService.mettreAJourMontantsDepuisOCR(
                                         "of-003",
                                         new BigDecimal("500000"), new BigDecimal("95000"),
                                         new BigDecimal("595000"), null);

                        assertThat(offre.getMontantHt()).isEqualByComparingTo("500000");
                        verify(offreFinanciereRepository).save(offre);
                }
        }

        // ── mettreAJourApresDecryptage ──────────────────────

        @Nested
        @DisplayName("mettreAJourApresDecryptage")
        class MettreAJourApresDecryptage {

                @Test
                @DisplayName("Après décryptage — isDechiffree=true et dateDechiffrement set")
                void apresDecryptage_flagEtDateSet() {
                        OffreFinanciere offre = OffreFinanciere.builder()
                                         .id("of-decrypt")
                                         .isDechiffree(false)
                                         .build();

                        when(offreFinanciereRepository.save(any())).thenAnswer(i -> i.getArgument(0));

                        offreFinanciereService.mettreAJourApresDecryptage(offre);

                        assertThat(offre.getIsDechiffree()).isTrue();
                        assertThat(offre.getDateDechiffrement()).isNotNull();
                        assertThat(offre.getDateDechiffrement()).isBeforeOrEqualTo(LocalDateTime.now());

                        // Les montants doivent rester NULL (remplis par OCR)
                        assertThat(offre.getMontantHt()).isNull();
                        assertThat(offre.getTva()).isNull();
                        assertThat(offre.getMontantTtc()).isNull();

                        verify(offreFinanciereRepository).save(offre);
                }
        }

        // ── deposerOffreFinanciere ───────────────────────────

        @Nested
        @DisplayName("deposerOffreFinanciere")
        class DeposerOffreFinanciere {

                @Test
                @DisplayName("Succès — offre financière chiffrée déposée")
                void succes() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-001").operateurId("op-001").appelOffreId("ao-001")
                                         .statut(StatutSoumission.BROUILLON).build();

                        MockMultipartFile fichier = new MockMultipartFile(
                                         "fichierChiffre", "offre.enc", "application/octet-stream",
                                         "ciphertext".getBytes());

                        MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
                        bucketConfig.setOffresFinancieres("offres-financieres");

                        List<LigneOffreFinanciere> lignesBpu = List.of(
                                        LigneOffreFinanciere.builder()
                                                        .id("ligne-001")
                                                        .designation("Lot 1")
                                                        .quantite(BigDecimal.ONE)
                                                        .unite("LOT")
                                                        .build()
                        );

                        DepotOffreFinanciereRequest request = DepotOffreFinanciereRequest.builder()
                                        .hashClient("clientHash")
                                        .signatureEcdsa("sig-base64")
                                        .clePubliqueEcdsaPem("PEM-key")
                                        .lignes(List.of(
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-001")
                                                                        .prixUnitaire(new BigDecimal("1000.00"))
                                                                        .build()
                                        ))
                                        .build();

                        when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
                        when(ligneOffreFinanciereRepository.findBySoumissionId("soum-001")).thenReturn(lignesBpu);
                        when(offreFinanciereRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());
                        when(hashService.calculerHash(any(org.springframework.web.multipart.MultipartFile.class)))
                                         .thenReturn("hashCipher");
                        when(minIOProperties.getBucket()).thenReturn(bucketConfig);
                        when(minIOService.uploadFichier(any(), eq("offres-financieres"), eq("soum-001")))
                                         .thenReturn("offres-financieres/soum-001/uuid-offre.enc");
                        when(offreFinanciereRepository.save(any(OffreFinanciere.class))).thenAnswer(inv -> {
                                OffreFinanciere of = inv.getArgument(0);
                                of.setId("of-001");
                                return of;
                        });
                        // ECDSA verification will fail gracefully (catch block in service)
                        when(chiffrementService.reconstruireClePubliqueECDSA(anyString()))
                                         .thenThrow(new RuntimeException("Invalid PEM for test"));

                        OffreFinanciereResponse result = offreFinanciereService.deposerOffreFinanciere(
                                         "soum-001", "op-001", fichier, request);

                        assertThat(result).isNotNull();
                        assertThat(result.getHashFichier()).isEqualTo("hashCipher");
                        assertThat(result.getIsDechiffree()).isFalse();
                        verify(offreFinanciereRepository).save(any(OffreFinanciere.class));
                        verify(auditLogService).logDepot(eq("soum-001"), eq("op-001"),
                                         eq("OFFRE_FINANCIERE"), eq(true), anyString());
                }

                @Test
                @DisplayName("Soumission introuvable → SoumissionNotFoundException")
                void soumissionIntrouvable() {
                        MockMultipartFile fichier = new MockMultipartFile(
                                         "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                        DepotOffreFinanciereRequest request = DepotOffreFinanciereRequest.builder()
                                        .hashClient("clientHash")
                                        .signatureEcdsa("sig-base64")
                                        .clePubliqueEcdsaPem("PEM-key")
                                        .lignes(List.of(
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-001")
                                                                        .prixUnitaire(new BigDecimal("1000.00"))
                                                                        .build()
                                        ))
                                        .build();

                        when(soumissionRepository.findById("soum-999")).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> offreFinanciereService.deposerOffreFinanciere(
                                         "soum-999", "op-001", fichier, request))
                                         .isInstanceOf(SoumissionNotFoundException.class);
                }

                @Test
                @DisplayName("Opérateur non propriétaire → AccesRefuseException")
                void accesRefuse() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-001").operateurId("op-001")
                                         .statut(StatutSoumission.BROUILLON).build();
                        MockMultipartFile fichier = new MockMultipartFile(
                                         "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                        DepotOffreFinanciereRequest request = DepotOffreFinanciereRequest.builder()
                                        .hashClient("clientHash")
                                        .signatureEcdsa("sig-base64")
                                        .clePubliqueEcdsaPem("PEM-key")
                                        .lignes(List.of(
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-001")
                                                                        .prixUnitaire(new BigDecimal("1000.00"))
                                                                        .build()
                                        ))
                                        .build();

                        when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));

                        assertThatThrownBy(() -> offreFinanciereService.deposerOffreFinanciere(
                                         "soum-001", "op-autre", fichier, request))
                                         .isInstanceOf(AccesRefuseException.class);
                }

                @Test
                @DisplayName("Statut non BROUILLON → IllegalStateException")
                void statutInvalide() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-001").operateurId("op-001")
                                         .statut(StatutSoumission.DEPOSEE).build();
                        MockMultipartFile fichier = new MockMultipartFile(
                                         "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                        DepotOffreFinanciereRequest request = DepotOffreFinanciereRequest.builder()
                                        .hashClient("clientHash")
                                        .signatureEcdsa("sig-base64")
                                        .clePubliqueEcdsaPem("PEM-key")
                                        .lignes(List.of(
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-001")
                                                                        .prixUnitaire(new BigDecimal("1000.00"))
                                                                        .build()
                                        ))
                                        .build();

                        when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));

                        assertThatThrownBy(() -> offreFinanciereService.deposerOffreFinanciere(
                                         "soum-001", "op-001", fichier, request))
                                         .isInstanceOf(IllegalStateException.class);
                }

                @Test
                @DisplayName("Doublon offre financière → supprimée et remplacée (stratégie remplacement)")
                void doublon() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-001").operateurId("op-001").appelOffreId("ao-001")
                                         .statut(StatutSoumission.BROUILLON).build();
                        MockMultipartFile fichier = new MockMultipartFile(
                                         "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                        List<LigneOffreFinanciere> lignesBpu = List.of(
                                        LigneOffreFinanciere.builder()
                                                        .id("ligne-001")
                                                        .designation("Lot 1")
                                                        .quantite(BigDecimal.ONE)
                                                        .unite("LOT")
                                                        .build()
                        );

                        DepotOffreFinanciereRequest request = DepotOffreFinanciereRequest.builder()
                                        .hashClient("clientHash")
                                        .signatureEcdsa("sig-base64")
                                        .clePubliqueEcdsaPem("PEM-key")
                                        .lignes(List.of(
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-001")
                                                                        .prixUnitaire(new BigDecimal("1000.00"))
                                                                        .build()
                                        ))
                                        .build();

                        MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
                        bucketConfig.setOffresFinancieres("offres-financieres");

                        OffreFinanciere offreExistante = new OffreFinanciere();

                        when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
                        when(ligneOffreFinanciereRepository.findBySoumissionId("soum-001")).thenReturn(lignesBpu);
                        when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                         .thenReturn(Optional.of(offreExistante));
                        when(hashService.calculerHash(any(org.springframework.web.multipart.MultipartFile.class)))
                                         .thenReturn("hashCipher");
                        when(minIOProperties.getBucket()).thenReturn(bucketConfig);
                        when(minIOService.uploadFichier(any(), eq("offres-financieres"), eq("soum-001")))
                                         .thenReturn("offres-financieres/soum-001/uuid-offre.enc");
                        when(offreFinanciereRepository.save(any(OffreFinanciere.class))).thenAnswer(inv -> {
                                OffreFinanciere of = inv.getArgument(0);
                                of.setId("of-new");
                                return of;
                        });
                        when(chiffrementService.reconstruireClePubliqueECDSA(anyString()))
                                         .thenThrow(new RuntimeException("Invalid PEM for test"));

                        // Le service supprime l'ancienne et crée une nouvelle — aucune exception
                        OffreFinanciereResponse result = offreFinanciereService.deposerOffreFinanciere(
                                         "soum-001", "op-001", fichier, request);

                        assertThat(result).isNotNull();
                        // L'ancienne offre a bien été supprimée
                        verify(offreFinanciereRepository).delete(offreExistante);
                        verify(offreFinanciereRepository).save(any(OffreFinanciere.class));
                }

                @Test
                @DisplayName("Payload avec designation fournie → acceptée (service utilise la designation du payload)")
                void payloadColonnesDescriptivesInterdites() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-001").operateurId("op-001").appelOffreId("ao-001")
                                         .statut(StatutSoumission.BROUILLON).build();
                        MockMultipartFile fichier = new MockMultipartFile(
                                         "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                        MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
                        bucketConfig.setOffresFinancieres("offres-financieres");

                        DepotOffreFinanciereRequest request = DepotOffreFinanciereRequest.builder()
                                        .hashClient("clientHash")
                                        .signatureEcdsa("sig-base64")
                                        .clePubliqueEcdsaPem("PEM-key")
                                        .lignes(List.of(
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-001")
                                                                        .prixUnitaire(new BigDecimal("1000.00"))
                                                                        .designation("Ma désignation")
                                                                        .build()
                                        ))
                                        .build();

                        when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
                        when(ligneOffreFinanciereRepository.findBySoumissionId("soum-001")).thenReturn(List.of());
                        when(offreFinanciereRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());
                        when(hashService.calculerHash(any(org.springframework.web.multipart.MultipartFile.class)))
                                         .thenReturn("hashCipher");
                        when(minIOProperties.getBucket()).thenReturn(bucketConfig);
                        when(minIOService.uploadFichier(any(), eq("offres-financieres"), eq("soum-001")))
                                         .thenReturn("offres-financieres/soum-001/uuid-offre.enc");
                        when(offreFinanciereRepository.save(any(OffreFinanciere.class))).thenAnswer(inv -> {
                                OffreFinanciere of = inv.getArgument(0);
                                of.setId("of-001");
                                return of;
                        });
                        when(chiffrementService.reconstruireClePubliqueECDSA(anyString()))
                                         .thenThrow(new RuntimeException("Invalid PEM for test"));

                        // La designation fournie est utilisée telle quelle
                        OffreFinanciereResponse result = offreFinanciereService.deposerOffreFinanciere(
                                         "soum-001", "op-001", fichier, request);

                        assertThat(result).isNotNull();
                        // Vérifier que la ligne a été sauvegardée avec la désignation fournie
                        verify(ligneOffreFinanciereRepository).save(argThat(ligne ->
                                "Ma désignation".equals(ligne.getDesignation())));
                }

                @Test
                @DisplayName("Plusieurs lignes soumises → toutes sauvegardées (stratégie remplacement)")
                void structureBpuModifieeNombreLignes() {
                        Soumission soumission = Soumission.builder()
                                         .id("soum-001").operateurId("op-001").appelOffreId("ao-001")
                                         .statut(StatutSoumission.BROUILLON).build();
                        MockMultipartFile fichier = new MockMultipartFile(
                                         "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                        List<LigneOffreFinanciere> lignesBpu = List.of(
                                        LigneOffreFinanciere.builder()
                                                        .id("ligne-001")
                                                        .designation("Lot 1")
                                                        .quantite(BigDecimal.ONE)
                                                        .unite("LOT")
                                                        .build()
                        );

                        MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
                        bucketConfig.setOffresFinancieres("offres-financieres");

                        DepotOffreFinanciereRequest request = DepotOffreFinanciereRequest.builder()
                                        .hashClient("clientHash")
                                        .signatureEcdsa("sig-base64")
                                        .clePubliqueEcdsaPem("PEM-key")
                                        .lignes(List.of(
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-001")
                                                                        .prixUnitaire(new BigDecimal("1000.00"))
                                                                        .build(),
                                                        DepotOffreFinanciereRequest.LigneOffreRequest.builder()
                                                                        .articleId("ligne-002")
                                                                        .prixUnitaire(new BigDecimal("2000.00"))
                                                                        .build()
                                        ))
                                        .build();

                        when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
                        when(ligneOffreFinanciereRepository.findBySoumissionId("soum-001")).thenReturn(lignesBpu);
                        when(offreFinanciereRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());
                        when(hashService.calculerHash(any(org.springframework.web.multipart.MultipartFile.class)))
                                         .thenReturn("hashCipher");
                        when(minIOProperties.getBucket()).thenReturn(bucketConfig);
                        when(minIOService.uploadFichier(any(), eq("offres-financieres"), eq("soum-001")))
                                         .thenReturn("offres-financieres/soum-001/uuid-offre.enc");
                        when(offreFinanciereRepository.save(any(OffreFinanciere.class))).thenAnswer(inv -> {
                                OffreFinanciere of = inv.getArgument(0);
                                of.setId("of-001");
                                return of;
                        });
                        when(chiffrementService.reconstruireClePubliqueECDSA(anyString()))
                                         .thenThrow(new RuntimeException("Invalid PEM for test"));

                        // Le service accepte toutes les lignes et remplace le BPU
                        OffreFinanciereResponse result = offreFinanciereService.deposerOffreFinanciere(
                                         "soum-001", "op-001", fichier, request);

                        assertThat(result).isNotNull();
                        // 2 nouvelles lignes sauvegardées (les 2 du payload)
                        verify(ligneOffreFinanciereRepository, times(2)).save(any(LigneOffreFinanciere.class));
                        // Les anciennes lignes ont été supprimées
                        verify(ligneOffreFinanciereRepository).deleteAll(lignesBpu);
                }
        }

        // ── getOffreFinanciere ───────────────────────────────

        @Nested
        @DisplayName("getOffreFinanciere")
        class GetOffreFinanciere {

                @Test
                @DisplayName("Succès — offre trouvée")
                void succes() {
                        OffreFinanciere offre = OffreFinanciere.builder()
                                         .id("of-001")
                                         .fichierChiffreUrl("url.enc")
                                         .hashFichier("hash")
                                         .signatureEcdsa("sig")
                                         .isDechiffree(false)
                                         .build();

                        when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                         .thenReturn(Optional.of(offre));

                        OffreFinanciereResponse result = offreFinanciereService.getOffreFinanciere("soum-001");

                        assertThat(result.getId()).isEqualTo("of-001");
                        assertThat(result.getIsDechiffree()).isFalse();
                        assertThat(result.getSignatureVerifiee()).isTrue(); // because signatureEcdsa is not null
                }

                @Test
                @DisplayName("Introuvable → RessourceIntrouvableException")
                void introuvable() {
                        when(offreFinanciereRepository.findBySoumissionId("soum-999"))
                                         .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> offreFinanciereService.getOffreFinanciere("soum-999"))
                                         .isInstanceOf(RessourceIntrouvableException.class);
                }
        }

        // ── getOffresNonDechiffrees ──────────────────────────

        @Nested
        @DisplayName("getOffresNonDechiffrees")
        class GetOffresNonDechiffrees {

                @Test
                @DisplayName("Retourne la liste des offres non déchiffrées d'un AO")
                void succes() {
                        OffreFinanciere of1 = OffreFinanciere.builder().id("of-1").isDechiffree(false).build();
                        OffreFinanciere of2 = OffreFinanciere.builder().id("of-2").isDechiffree(false).build();

                        when(offreFinanciereRepository.findBySoumissionAppelOffreIdAndIsDechiffree("ao-001", false))
                                         .thenReturn(List.of(of1, of2));

                        List<OffreFinanciere> result = offreFinanciereService.getOffresNonDechiffrees("ao-001");

                        assertThat(result).hasSize(2);
                }

                @Test
                @DisplayName("Aucune offre → liste vide")
                void aucuneOffre() {
                        when(offreFinanciereRepository.findBySoumissionAppelOffreIdAndIsDechiffree("ao-999", false))
                                         .thenReturn(List.of());

                        List<OffreFinanciere> result = offreFinanciereService.getOffresNonDechiffrees("ao-999");

                        assertThat(result).isEmpty();
                }
        }
}
