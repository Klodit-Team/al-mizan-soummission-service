package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.request.CreateCautionRequest;
import com.klodit.soumission_service.dto.response.CautionResponse;
import com.klodit.soumission_service.entity.Caution;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutCaution;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.*;
import com.klodit.soumission_service.repository.CautionRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CautionService — Tests unitaires")
class CautionServiceTest {

    @Mock
    private SoumissionRepository soumissionRepository;
    @Mock
    private CautionRepository cautionRepository;
    @Mock
    private MinIOService minIOService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private MinIOProperties minIOProperties;

    @InjectMocks
    private CautionService cautionService;

    private Soumission soumission;
    private CreateCautionRequest request;
    private MockMultipartFile scanFile;

    @BeforeEach
    void setUp() {
        soumission = Soumission.builder()
                .id("soum-001")
                .appelOffreId("ao-001")
                .operateurId("op-001")
                .statut(StatutSoumission.BROUILLON)
                .build();

        request = CreateCautionRequest.builder()
                .compteBancaireId("rib-123456789")
                .reference("CB-2026-001")
                .dateExpiration(LocalDateTime.now().plusYears(1))
                .build();

        scanFile = new MockMultipartFile(
                "scanCaution", "caution.pdf", "application/pdf", "scan-content".getBytes());
    }

    // ── ajouterCaution ──────────────────────────────────

    @Nested
    @DisplayName("ajouterCaution")
    class AjouterCaution {

        @Test
        @DisplayName("Succès — caution ajoutée")
        void succes() {
            MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
            bucketConfig.setCautions("cautions");

            when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
            when(cautionRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());
            when(minIOProperties.getBucket()).thenReturn(bucketConfig);
            when(minIOService.uploadFichier(any(), eq("cautions"), eq("soum-001")))
                    .thenReturn("cautions/soum-001/uuid-caution.pdf");
            when(cautionRepository.save(any(Caution.class))).thenAnswer(inv -> {
                Caution c = inv.getArgument(0);
                c.setId("cau-001");
                c.setCreatedAt(LocalDateTime.now());
                return c;
            });

            CautionResponse result = cautionService.ajouterCaution(
                    "soum-001", "op-001", request, scanFile);

            assertThat(result).isNotNull();
            assertThat(result.getCompteBancaireId()).isEqualTo("rib-123456789");
            assertThat(result.getReference()).isEqualTo("CB-2026-001");
            assertThat(result.getStatut()).isEqualTo(StatutCaution.VALIDE);

            verify(cautionRepository).save(any(Caution.class));
            verify(auditLogService).logDepot(eq("soum-001"), eq("op-001"), eq("CAUTION"), eq(true), anyString());
        }

        @Test
        @DisplayName("Soumission introuvable → SoumissionNotFoundException")
        void soumissionIntrouvable() {
            when(soumissionRepository.findById("soum-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cautionService.ajouterCaution(
                    "soum-999", "op-001", request, scanFile))
                    .isInstanceOf(SoumissionNotFoundException.class);
        }

        @Test
        @DisplayName("Opérateur non propriétaire → AccesRefuseException")
        void operateurNonProprietaire() {
            when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));

            assertThatThrownBy(() -> cautionService.ajouterCaution(
                    "soum-001", "op-autre", request, scanFile))
                    .isInstanceOf(AccesRefuseException.class);
        }

        @Test
        @DisplayName("Statut non BROUILLON → IllegalStateException")
        void statutNonBrouillon() {
            soumission.setStatut(StatutSoumission.DEPOSEE);
            when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));

            assertThatThrownBy(() -> cautionService.ajouterCaution(
                    "soum-001", "op-001", request, scanFile))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BROUILLON");
        }

        @Test
        @DisplayName("Doublon caution → OffreDejaDeposeeException")
        void doublon() {
            when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
            when(cautionRepository.findBySoumissionId("soum-001"))
                    .thenReturn(Optional.of(new Caution()));

            assertThatThrownBy(() -> cautionService.ajouterCaution(
                    "soum-001", "op-001", request, scanFile))
                    .isInstanceOf(OffreDejaDeposeeException.class);
        }

        @Test
        @DisplayName("Date expiration déjà passée → FichierInvalideException")
        void dateExpirationPassee() {
            request.setDateExpiration(LocalDateTime.now().minusDays(1)); // passée
            when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(soumission));
            when(cautionRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cautionService.ajouterCaution(
                    "soum-001", "op-001", request, scanFile))
                    .isInstanceOf(FichierInvalideException.class)
                    .hasMessageContaining("passée");
        }
    }

    // ── getCaution ──────────────────────────────────────

    @Nested
    @DisplayName("getCaution")
    class GetCaution {

        @Test
        @DisplayName("Succès — caution trouvée")
        void succes() {
            Caution caution = Caution.builder()
                    .id("cau-001")
                    .soumission(soumission)
                    .compteBancaireId("rib-123456789")
                    .reference("CB-001")
                    .dateExpiration(LocalDateTime.now().plusYears(1))
                    .statut(StatutCaution.VALIDE)
                    .fichierUrl("cautions/soum-001/file.pdf")
                    .build();

            when(cautionRepository.findBySoumissionId("soum-001"))
                    .thenReturn(Optional.of(caution));

            CautionResponse result = cautionService.getCaution("soum-001");

            assertThat(result.getId()).isEqualTo("cau-001");
            assertThat(result.getCompteBancaireId()).isEqualTo("rib-123456789");
            assertThat(result.getStatut()).isEqualTo(StatutCaution.VALIDE);
        }

        @Test
        @DisplayName("Introuvable → RessourceIntrouvableException")
        void introuvable() {
            when(cautionRepository.findBySoumissionId("soum-999"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> cautionService.getCaution("soum-999"))
                    .isInstanceOf(RessourceIntrouvableException.class);
        }
    }
}
