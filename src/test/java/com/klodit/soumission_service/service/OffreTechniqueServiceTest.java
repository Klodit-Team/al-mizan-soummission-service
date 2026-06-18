package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.response.OffreTechniqueResponse;
import com.klodit.soumission_service.entity.OffreTechnique;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.*;
import com.klodit.soumission_service.messaging.publisher.SoumissionEventPublisher;
import com.klodit.soumission_service.repository.OffreTechniqueRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OffreTechniqueService — Tests unitaires")
class OffreTechniqueServiceTest {

        @Mock
        private SoumissionRepository soumissionRepository;
        @Mock
        private OffreTechniqueRepository offreTechniqueRepository;
        @Mock
        private MinIOService minIOService;
        @Mock
        private HashService hashService;
        @Mock
        private AuditLogService auditLogService;
        @Mock
        private SoumissionEventPublisher eventPublisher;
        @Mock
        private MinIOProperties minIOProperties;

        @InjectMocks
        private OffreTechniqueService offreTechniqueService;

        // ── deposerOffreTechnique — succès ──────────────────

        @Test
        @DisplayName("Dépôt offre technique — succès complet")
        void deposer_succes() {
                Soumission soumission = Soumission.builder()
                                .id("soum-001").operateurId("oe-001").appelOffreId("ao-001")
                                .statut(StatutSoumission.BROUILLON).build();

                MockMultipartFile fichier = new MockMultipartFile(
                                "fichier", "cahier.pdf", "application/pdf", "PDF content".getBytes());

                MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
                bucketConfig.setOffresTechniques("offres-techniques");

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());
                when(hashService.calculerHash(any(org.springframework.web.multipart.MultipartFile.class)))
                                .thenReturn("sha256hash");
                when(minIOProperties.getBucket()).thenReturn(bucketConfig);
                when(minIOService.uploadFichier(any(), eq("offres-techniques"), eq("soum-001")))
                                .thenReturn("offres-techniques/soum-001/uuid-cahier.pdf");
                when(offreTechniqueRepository.save(any(OffreTechnique.class))).thenAnswer(inv -> {
                        OffreTechnique ot = inv.getArgument(0);
                        ot.setId("ot-001");
                        return ot;
                });

                OffreTechniqueResponse result = offreTechniqueService.deposerOffreTechnique(
                                "soum-001", "oe-001", fichier, null);

                assertThat(result).isNotNull();
                assertThat(result.getHashFichier()).isEqualTo("sha256hash");
                assertThat(result.getFichierUrl()).contains("offres-techniques");

                verify(offreTechniqueRepository).save(any(OffreTechnique.class));
                verify(auditLogService).logDepot(eq("soum-001"), eq("oe-001"), eq("OFFRE_TECHNIQUE"), eq(true),
                                anyString());
                verify(eventPublisher).publierDemandeAnalyseOCR(any());
        }

        @Test
        @DisplayName("Dépôt offre technique — statut non BROUILLON → exception")
        void deposer_statut_invalide_leve_exception() {
                Soumission soumission = Soumission.builder()
                                .id("soum-001")
                                .operateurId("oe-001")
                                .statut(StatutSoumission.DEPOSEE)
                                .build();

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));

                assertThatThrownBy(() -> offreTechniqueService.deposerOffreTechnique("soum-001", "oe-001", null, null))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("BROUILLON");
        }

        @Test
        @DisplayName("Dépôt offre technique — doublon → suppression et remplacement réussi")
        void deposer_doublon_remplacement_succes() {
                Soumission soumission = Soumission.builder()
                                .id("soum-001")
                                .operateurId("oe-001")
                                .appelOffreId("ao-001")
                                .statut(StatutSoumission.BROUILLON)
                                .build();

                MockMultipartFile fichier = new MockMultipartFile(
                                "fichier", "cahier.pdf", "application/pdf", "PDF content".getBytes());

                MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
                bucketConfig.setOffresTechniques("offres-techniques");

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
                
                OffreTechnique ancienneOffre = new OffreTechnique();
                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(ancienneOffre));

                when(hashService.calculerHash(any(org.springframework.web.multipart.MultipartFile.class)))
                                .thenReturn("sha256hash");
                when(minIOProperties.getBucket()).thenReturn(bucketConfig);
                when(minIOService.uploadFichier(any(), eq("offres-techniques"), eq("soum-001")))
                                .thenReturn("offres-techniques/soum-001/uuid-cahier.pdf");
                when(offreTechniqueRepository.save(any(OffreTechnique.class))).thenAnswer(inv -> {
                        OffreTechnique ot = inv.getArgument(0);
                        ot.setId("ot-002");
                        return ot;
                });

                OffreTechniqueResponse result = offreTechniqueService.deposerOffreTechnique(
                                "soum-001", "oe-001", fichier, null);

                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo("ot-002");

                verify(offreTechniqueRepository).delete(ancienneOffre);
                verify(offreTechniqueRepository).flush();
                verify(offreTechniqueRepository).save(any(OffreTechnique.class));
                verify(auditLogService).logDepot(eq("soum-001"), eq("oe-001"), eq("OFFRE_TECHNIQUE"), eq(true),
                                anyString());
                verify(eventPublisher).publierDemandeAnalyseOCR(any());
        }

        @Test
        @DisplayName("Dépôt offre technique — soumission introuvable → SoumissionNotFoundException")
        void deposer_soumissionIntrouvable() {
                when(soumissionRepository.findById("soum-999")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> offreTechniqueService.deposerOffreTechnique("soum-999", "oe-001", null, null))
                                .isInstanceOf(SoumissionNotFoundException.class);
        }

        @Test
        @DisplayName("Dépôt offre technique — opérateur non propriétaire → AccesRefuseException")
        void deposer_accesRefuse() {
                Soumission soumission = Soumission.builder()
                                .id("soum-001").operateurId("oe-001")
                                .statut(StatutSoumission.BROUILLON).build();

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));

                assertThatThrownBy(
                                () -> offreTechniqueService.deposerOffreTechnique("soum-001", "oe-autre", null, null))
                                .isInstanceOf(AccesRefuseException.class);
        }

        // ── getOffreTechnique ───────────────────────────────

        @Test
        @DisplayName("getOffreTechnique — trouvée → réponse correcte")
        void getOffreTechnique_succes() {
                OffreTechnique ot = OffreTechnique.builder()
                                .id("ot-001").fichierUrl("url").hashFichier("hash")
                                .isConforme(true).observations("OK").build();

                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(ot));

                OffreTechniqueResponse result = offreTechniqueService.getOffreTechnique("soum-001");

                assertThat(result.getId()).isEqualTo("ot-001");
                assertThat(result.getIsConforme()).isTrue();
        }

        @Test
        @DisplayName("getOffreTechnique — introuvable → RessourceIntrouvableException")
        void getOffreTechnique_introuvable() {
                when(offreTechniqueRepository.findBySoumissionId("soum-999"))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> offreTechniqueService.getOffreTechnique("soum-999"))
                                .isInstanceOf(RessourceIntrouvableException.class);
        }

        // ── mettreAJourConformite ───────────────────────────

        @Test
        @DisplayName("mettreAJourConformite — succès")
        void mettreAJourConformite_succes() {
                OffreTechnique ot = OffreTechnique.builder()
                                .id("ot-001").fichierUrl("url").hashFichier("hash").build();

                when(offreTechniqueRepository.findById("ot-001")).thenReturn(Optional.of(ot));
                when(offreTechniqueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                offreTechniqueService.mettreAJourConformite("ot-001", true, "Conforme");

                assertThat(ot.getIsConforme()).isTrue();
                assertThat(ot.getObservations()).isEqualTo("Conforme");
                verify(offreTechniqueRepository).save(ot);
        }

        @Test
        @DisplayName("mettreAJourConformite — introuvable → RessourceIntrouvableException")
        void mettreAJourConformite_introuvable() {
                when(offreTechniqueRepository.findById("ot-999")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> offreTechniqueService.mettreAJourConformite("ot-999", true, ""))
                                .isInstanceOf(RessourceIntrouvableException.class);
        }
}
