package com.klodit.soumission_service.service;

import com.klodit.soumission_service.client.AppelOffreClient;
import com.klodit.soumission_service.client.DocumentsClient;
import com.klodit.soumission_service.client.UtilisateurClient;
import com.klodit.soumission_service.client.dto.AppelOffreExterneDTO;
import com.klodit.soumission_service.client.dto.LotExterneDTO;
import com.klodit.soumission_service.dto.request.CreateSoumissionRequest;
import com.klodit.soumission_service.dto.response.SoumissionResponse;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.OffreDejaDeposeeException;
import com.klodit.soumission_service.exception.SoumissionNotFoundException;
import com.klodit.soumission_service.messaging.publisher.SoumissionEventPublisher;
import com.klodit.soumission_service.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SoumissionService — Tests unitaires")
class SoumissionServiceTest {

        @Mock
        private SoumissionRepository soumissionRepository;
        @Mock
        private OffreTechniqueRepository offreTechniqueRepository;
        @Mock
        private OffreFinanciereRepository offreFinanciereRepository;
        @Mock
        private CautionRepository cautionRepository;
        @Mock
        private LigneOffreFinanciereRepository ligneOffreFinanciereRepository;
        @Mock
        private HorodatageService horodatageService;
        @Mock
        private AuditLogService auditLogService;
        @Mock
        private SoumissionEventPublisher eventPublisher;
        @Mock
        private AppelOffreClient appelOffreClient;
        @Mock
        private UtilisateurClient utilisateurClient;
        @Mock
        private DocumentsClient documentsClient;

        @InjectMocks
        private SoumissionService soumissionService;

        private Soumission sampleSoumission;

        @BeforeEach
        void setUp() {
                sampleSoumission = Soumission.builder()
                                .id("soum-001")
                                .appelOffreId("ao-001")
                                .operateurId("op-001")
                                .lotId("lot-001")
                                .reference("REF-2025-001")
                                .statut(StatutSoumission.BROUILLON)
                                .isElectronique(true)
                                .build();
        }

        @Test
        @DisplayName("Créer un brouillon — succès")
        void creerBrouillon_succes() {
                CreateSoumissionRequest request = CreateSoumissionRequest.builder()
                                .appelOffreId("ao-001")
                                .lotId("lot-001")
                                .build();

                AppelOffreExterneDTO ao = AppelOffreExterneDTO.builder()
                                .id("ao-001")
                                .statut("PUBLIE")
                                .lots(List.of(
                                                LotExterneDTO.builder()
                                                                .id("lot-001")
                                                                .designation("Lot 1")
                                                                .build()
                                ))
                                .build();

                when(soumissionRepository.findByAppelOffreIdAndOperateurIdAndLotId(
                                any(), any(), any())).thenReturn(Optional.empty());
                when(appelOffreClient.getAppelOffre("ao-001")).thenReturn(Optional.of(ao));
                when(soumissionRepository.save(any(Soumission.class))).thenAnswer(invocation -> {
                        Soumission s = invocation.getArgument(0);
                        s.setCreatedAt(LocalDateTime.now());
                        return s;
                });

                SoumissionResponse response = soumissionService.creerBrouillon(request, "oe-456");

                assertThat(response).isNotNull();
                assertThat(response.getStatut()).isEqualTo(StatutSoumission.BROUILLON);
                assertThat(response.getAppelOffreId()).isEqualTo("ao-001");
                assertThat(response.getOperateurId()).isEqualTo("oe-456");
                assertThat(response.getReference()).startsWith("SOUM-");
                verify(soumissionRepository).save(any(Soumission.class));
                verify(ligneOffreFinanciereRepository).save(any());
        }

        @Test
        @DisplayName("Créer un brouillon — doublon détecté → exception")
        void creerBrouillon_doublon() {
                CreateSoumissionRequest request = CreateSoumissionRequest.builder()
                                .appelOffreId("ao-001")
                                .lotId("lot-001")
                                .build();

                when(soumissionRepository.findByAppelOffreIdAndOperateurIdAndLotId(
                                any(), any(), any())).thenReturn(Optional.of(new Soumission()));

                assertThatThrownBy(() -> soumissionService.creerBrouillon(request, "oe-456"))
                                .isInstanceOf(OffreDejaDeposeeException.class)
                                .hasMessageContaining("déjà été déposée");

                verify(soumissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("validerEtSoumettre — opérateur non valide → exception")
        void validerEtSoumettre_operateurInvalide() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreTechnique()));
                when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreFinanciere()));
                when(appelOffreClient.isCautionRequise("ao-001")).thenReturn(false);
                when(documentsClient.arePiecesAdministrativesValides("soum-001")).thenReturn(true);
                when(utilisateurClient.isOperateurValide("op-001")).thenReturn(false);

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("profil opérateur");
        }

        @Test
        @DisplayName("validerEtSoumettre — dossier incomplet (offre technique manquante) → exception")
        void validerEtSoumettre_dossierIncomplet() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("offre technique");
        }

        @Test
        @DisplayName("changerStatut — soumission introuvable → SoumissionNotFoundException")
        void changerStatut_nonExistant() {
                when(soumissionRepository.findById("soum-999")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> soumissionService.changerStatut("soum-999", StatutSoumission.OUVERTE))
                                .isInstanceOf(SoumissionNotFoundException.class);
        }

        @Test
        @DisplayName("changerStatut — succès + publication événement")
        void changerStatut_success() {
                sampleSoumission.setStatut(StatutSoumission.DEPOSEE);
                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(soumissionRepository.save(any())).thenReturn(sampleSoumission);

                SoumissionResponse result = soumissionService.changerStatut("soum-001", StatutSoumission.RECUE);

                assertThat(result).isNotNull();
                verify(eventPublisher).publierStatutChange(any());
        }

        @Test
        @DisplayName("getDetail — soumission introuvable → exception")
        void getDetail_notFound() {
                when(soumissionRepository.findById("soum-999")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> soumissionService.getDetail("soum-999"))
                                .isInstanceOf(SoumissionNotFoundException.class);
        }

        // ── Tests supplémentaires — chemins de succès ────────

        @Test
        @DisplayName("listerMesSoumissions — retourne la liste filtrée")
        void listerMesSoumissions_succes() {
                Soumission s1 = Soumission.builder().id("s1").appelOffreId("ao-001")
                                .operateurId("op-001").reference("REF-1").statut(StatutSoumission.BROUILLON)
                                .isElectronique(true).build();
                Soumission s2 = Soumission.builder().id("s2").appelOffreId("ao-002")
                                .operateurId("op-001").reference("REF-2").statut(StatutSoumission.DEPOSEE)
                                .isElectronique(true).build();

                when(soumissionRepository.findByOperateurIdOrderByCreatedAtDesc("op-001"))
                                .thenReturn(List.of(s1, s2));

                List<SoumissionResponse> result = soumissionService.listerMesSoumissions("op-001");

                assertThat(result).hasSize(2);
                assertThat(result.get(0).getId()).isEqualTo("s1");
                assertThat(result.get(1).getStatut()).isEqualTo(StatutSoumission.DEPOSEE);
        }

        @Test
        @DisplayName("listerMesSoumissions — opérateur sans soumission → liste vide")
        void listerMesSoumissions_vide() {
                when(soumissionRepository.findByOperateurIdOrderByCreatedAtDesc("op-new"))
                                .thenReturn(List.of());

                List<SoumissionResponse> result = soumissionService.listerMesSoumissions("op-new");

                assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("listerParAppelOffre — retourne les soumissions d'un AO")
        void listerParAppelOffre_succes() {
                Soumission s1 = Soumission.builder().id("s1").appelOffreId("ao-001")
                                .operateurId("op-001").reference("REF-1").statut(StatutSoumission.DEPOSEE)
                                .isElectronique(true).build();

                when(soumissionRepository.findByAppelOffreIdOrderByCreatedAtDesc("ao-001"))
                                .thenReturn(List.of(s1));

                List<SoumissionResponse> result = soumissionService.listerParAppelOffre("ao-001");

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getAppelOffreId()).isEqualTo("ao-001");
        }

        @Test
        @DisplayName("getDetail — soumission trouvée → détails retournés")
        void getDetail_succes() {
                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));

                var result = soumissionService.getDetail("soum-001");

                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo("soum-001");
                assertThat(result.getReference()).isEqualTo("REF-2025-001");
        }

        @Test
        @DisplayName("validerEtSoumettre — succès complet (tout valide)")
        void validerEtSoumettre_succes() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreTechnique()));
                when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreFinanciere()));
                when(appelOffreClient.isCautionRequise("ao-001")).thenReturn(false);
                when(documentsClient.arePiecesAdministrativesValides("soum-001")).thenReturn(true);
                when(utilisateurClient.isOperateurValide("op-001")).thenReturn(true);
                when(horodatageService.maintenant()).thenReturn(LocalDateTime.now());
                when(appelOffreClient.getDateLimiteDepot("ao-001"))
                                .thenReturn(Optional.of(LocalDateTime.now().plusDays(7)));
                when(horodatageService.estDansDelai(any())).thenReturn(true);
                when(horodatageService.formater(any())).thenReturn("2025-06-15 14:30:00.000");
                when(soumissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                SoumissionResponse result = soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1");

                assertThat(result).isNotNull();
                assertThat(result.getStatut()).isEqualTo(StatutSoumission.DEPOSEE);
                verify(eventPublisher).publierSoumissionDeposee(any());
                verify(eventPublisher).publierSoumissionRecue(any());
                verify(auditLogService).logValidation(eq("soum-001"), eq("op-001"), eq("127.0.0.1"), eq(true), any());
        }

        @Test
        @DisplayName("validerEtSoumettre — opérateur non propriétaire → AccesRefuseException")
        void validerEtSoumettre_accesRefuse() {
                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-autre", "127.0.0.1"))
                                .isInstanceOf(com.klodit.soumission_service.exception.AccesRefuseException.class);
        }

        @Test
        @DisplayName("validerEtSoumettre — statut non BROUILLON → IllegalStateException")
        void validerEtSoumettre_statutNonBrouillon() {
                sampleSoumission.setStatut(StatutSoumission.DEPOSEE);
                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("BROUILLON");
        }

        @Test
        @DisplayName("validerEtSoumettre — hors délai → DelaiDepotExpireException")
        void validerEtSoumettre_horsDelai() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreTechnique()));
                when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreFinanciere()));
                when(appelOffreClient.isCautionRequise("ao-001")).thenReturn(false);
                when(documentsClient.arePiecesAdministrativesValides("soum-001")).thenReturn(true);
                when(utilisateurClient.isOperateurValide("op-001")).thenReturn(true);
                when(horodatageService.maintenant()).thenReturn(LocalDateTime.now());
                when(appelOffreClient.getDateLimiteDepot("ao-001"))
                                .thenReturn(Optional.of(LocalDateTime.now().minusDays(1)));
                when(horodatageService.estDansDelai(any())).thenReturn(false);
                when(horodatageService.formater(any())).thenReturn("2025-06-15 14:30:00.000");

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1"))
                                .isInstanceOf(com.klodit.soumission_service.exception.DelaiDepotExpireException.class);
        }

        @Test
        @DisplayName("validerEtSoumettre — offre financière manquante → IllegalStateException")
        void validerEtSoumettre_offreFinanciereManquante() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreTechnique()));
                when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.empty());

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("offre financière");
        }

        @Test
        @DisplayName("validerEtSoumettre — caution requise mais absente → IllegalStateException")
        void validerEtSoumettre_cautionManquante() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreTechnique()));
                when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreFinanciere()));
                when(appelOffreClient.isCautionRequise("ao-001")).thenReturn(true);
                when(cautionRepository.findBySoumissionId("soum-001")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("caution");
        }

        @Test
        @DisplayName("changerStatut — transition invalide → IllegalStateException")
        void changerStatut_transitionInvalide() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);
                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));

                assertThatThrownBy(() -> soumissionService.changerStatut("soum-001", StatutSoumission.RETENUE))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Transition de statut invalide");
        }

        @Test
        @DisplayName("validerEtSoumettre — pièces administratives non validées → IllegalStateException")
        void validerEtSoumettre_piecesNonValidees() {
                sampleSoumission.setStatut(StatutSoumission.BROUILLON);

                when(soumissionRepository.findById("soum-001")).thenReturn(Optional.of(sampleSoumission));
                when(offreTechniqueRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreTechnique()));
                when(offreFinanciereRepository.findBySoumissionId("soum-001"))
                                .thenReturn(Optional.of(new com.klodit.soumission_service.entity.OffreFinanciere()));
                when(appelOffreClient.isCautionRequise("ao-001")).thenReturn(false);
                when(documentsClient.arePiecesAdministrativesValides("soum-001")).thenReturn(false);

                assertThatThrownBy(() -> soumissionService.validerEtSoumettre("soum-001", "op-001", "127.0.0.1"))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("pièces administratives");
        }
}
