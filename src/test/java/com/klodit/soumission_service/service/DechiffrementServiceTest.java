package com.klodit.soumission_service.service;

import com.klodit.soumission_service.client.AppelOffreClient;
import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.dto.request.DechiffrementRequest;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.entity.OffreFinanciere;
import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.exception.ChiffrementException;
import com.klodit.soumission_service.messaging.event.OffreFinanciereAnalyseDemandeEvent;
import com.klodit.soumission_service.messaging.event.OffresDecrypteesEvent;
import com.klodit.soumission_service.messaging.publisher.SoumissionEventPublisher;
import com.klodit.soumission_service.repository.SoumissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
@DisplayName("DechiffrementService — Tests unitaires (flux PDF + OCR)")
class DechiffrementServiceTest {

    @Mock
    private CleChiffrementService cleChiffrementService;
    @Mock
    private OffreFinanciereService offreFinanciereService;
    @Mock
    private ChiffrementService chiffrementService;
    @Mock
    private MinIOService minIOService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private SoumissionEventPublisher eventPublisher;
    @Mock
    private MinIOProperties minIOProperties;
    @Mock
    private AppelOffreClient appelOffreClient;
    @Mock
    private SoumissionRepository soumissionRepository;

    @InjectMocks
    private DechiffrementService dechiffrementService;

    private PrivateKey mockPrivateKey;
    private SecretKey mockAESKey;
    private List<DechiffrementRequest.FragmentSoumis> fragments;

    @BeforeEach
    void setUp() {
        // Préparer des fragments valides
        DechiffrementRequest.FragmentSoumis f1 = new DechiffrementRequest.FragmentSoumis();
        f1.setIndex(1);
        f1.setValeur("fragment1==");
        f1.setMembreId("membre-1");

        DechiffrementRequest.FragmentSoumis f2 = new DechiffrementRequest.FragmentSoumis();
        f2.setIndex(2);
        f2.setValeur("fragment2==");
        f2.setMembreId("membre-2");

        DechiffrementRequest.FragmentSoumis f3 = new DechiffrementRequest.FragmentSoumis();
        f3.setIndex(3);
        f3.setValeur("fragment3==");
        f3.setMembreId("membre-3");

        fragments = List.of(f1, f2, f3);

        // Mocks pour les clés
        mockPrivateKey = mock(PrivateKey.class);
        mockAESKey = mock(SecretKey.class);
    }

    @Test
    @DisplayName("Déchiffrement réussi — PDF stocké en clair + événement OCR publié")
    void dechiffrerOffres_succes_pdfStockeEtOcrPublie() {
        // Arrange
        String aoId = "ao-001";
        String membreId = "commission-member-001";

        // Date ouverture passée
        when(appelOffreClient.getDateOuverturePlis(aoId))
                .thenReturn(Optional.of(LocalDateTime.now().minusDays(1)));

        // Reconstitution clé privée
        when(cleChiffrementService.reconstituerClePrivee(eq(aoId), anyList()))
                .thenReturn(mockPrivateKey);

        // Offre financière à déchiffrer
        Soumission soumission = Soumission.builder()
                .id("soum-001")
                .appelOffreId(aoId)
                .operateurId("op-001")
                .statut(com.klodit.soumission_service.enums.StatutSoumission.DEPOSEE)
                .build();

        OffreFinanciere offre = OffreFinanciere.builder()
                .id("of-001")
                .soumission(soumission)
                .fichierChiffreUrl("offres-financieres/soum-001/fichier.enc")
                .hashFichier("hash-chiffre")
                .isDechiffree(false)
                .build();

        when(offreFinanciereService.getOffresNonDechiffrees(aoId))
                .thenReturn(List.of(offre));

        // Construire une enveloppe chiffrée simulée
        byte[] fakeCleAESChiffree = new byte[512];
        byte[] fakeIV = new byte[12];
        byte[] fakeCiphertext = new byte[100];
        ByteBuffer enveloppe = ByteBuffer
                .allocate(4 + fakeCleAESChiffree.length + fakeIV.length + fakeCiphertext.length);
        enveloppe.putInt(fakeCleAESChiffree.length);
        enveloppe.put(fakeCleAESChiffree);
        enveloppe.put(fakeIV);
        enveloppe.put(fakeCiphertext);

        when(minIOService.telechargerFichier("offres-financieres", "soum-001/fichier.enc"))
                .thenReturn(new ByteArrayInputStream(enveloppe.array()));

        // Déchiffrement AES => PDF en clair
        byte[] pdfClair = "%PDF-1.4 Offre Financière".getBytes();
        when(chiffrementService.dechiffrerCleAES(any(), eq(mockPrivateKey)))
                .thenReturn(mockAESKey);
        when(chiffrementService.dechiffrerAES(any(), any(), eq(mockAESKey)))
                .thenReturn(pdfClair);

        // MinIO bucket config
        MinIOProperties.BucketConfig bucketConfig = new MinIOProperties.BucketConfig();
        bucketConfig.setOffresFinancieresClaires("offres-financieres-claires");
        when(minIOProperties.getBucket()).thenReturn(bucketConfig);

        // Upload du PDF en clair
        when(minIOService.uploadBytes(eq(pdfClair), eq("offres-financieres-claires"), anyString()))
                .thenReturn("offres-financieres-claires/soum-001/of-001-offre-financiere.pdf");

        // Act
        DechiffrementService.DechiffrementResultat resultat = dechiffrementService.dechiffrerOffres(aoId, fragments,
                membreId);

        // Assert
        assertThat(resultat.offresDecryptees()).hasSize(1);
        assertThat(resultat.erreurs()).isEmpty();
        assertThat(resultat.totalTrouvees()).isEqualTo(1);

        // Vérifier que le PDF en clair a été uploadé dans MinIO
        verify(minIOService).uploadBytes(eq(pdfClair), eq("offres-financieres-claires"), anyString());

        // Vérifier que l'offre a été mise à jour (fichierClairUrl + isDechiffree)
        assertThat(offre.getFichierClairUrl())
                .isEqualTo("offres-financieres-claires/soum-001/of-001-offre-financiere.pdf");
        verify(offreFinanciereService).mettreAJourApresDecryptage(offre);

        // Vérifier que l'événement OCR a été publié
        ArgumentCaptor<OffreFinanciereAnalyseDemandeEvent> ocrCaptor = ArgumentCaptor
                .forClass(OffreFinanciereAnalyseDemandeEvent.class);
        verify(eventPublisher).publierDemandeAnalyseOffreFinanciere(ocrCaptor.capture());

        OffreFinanciereAnalyseDemandeEvent ocrEvent = ocrCaptor.getValue();
        assertThat(ocrEvent.getSoumissionId()).isEqualTo("soum-001");
        assertThat(ocrEvent.getOffreFinanciereId()).isEqualTo("of-001");
        assertThat(ocrEvent.getFichierClairUrl())
                .isEqualTo("offres-financieres-claires/soum-001/of-001-offre-financiere.pdf");
        assertThat(ocrEvent.getAppelOffreId()).isEqualTo(aoId);
        assertThat(ocrEvent.getOperateurId()).isEqualTo("op-001");
        assertThat(ocrEvent.getHashFichierClair()).isNotBlank();

        // Vérifier que l'événement offres.dechiffrees a été publié
        verify(eventPublisher).publierOffresDecryptees(any(OffresDecrypteesEvent.class));

        // Vérifier l'audit
        verify(auditLogService).logDechiffrement(aoId, membreId, 1, true);
    }

    @Test
    @DisplayName("Déchiffrement avant date d'ouverture → ChiffrementException")
    void dechiffrerOffres_avantDateOuverture_exception() {
        String aoId = "ao-002";
        LocalDateTime dateOuverture = LocalDateTime.now().plusDays(5);

        when(appelOffreClient.getDateOuverturePlis(aoId))
                .thenReturn(Optional.of(dateOuverture));

        assertThatThrownBy(() -> dechiffrementService.dechiffrerOffres(aoId, fragments, "membre-1"))
                .isInstanceOf(ChiffrementException.class)
                .hasMessageContaining("date d'ouverture des plis");

        // Aucun déchiffrement ne doit avoir lieu
        verify(cleChiffrementService, never()).reconstituerClePrivee(any(), any());
        verify(auditLogService).logDechiffrement(aoId, "membre-1", 0, false);
    }

    @Test
    @DisplayName("Aucune offre à déchiffrer → résultat vide")
    void dechiffrerOffres_aucuneOffre_resultatVide() {
        String aoId = "ao-003";

        when(appelOffreClient.getDateOuverturePlis(aoId)).thenReturn(Optional.empty());
        when(cleChiffrementService.reconstituerClePrivee(eq(aoId), anyList()))
                .thenReturn(mockPrivateKey);
        when(offreFinanciereService.getOffresNonDechiffrees(aoId))
                .thenReturn(List.of());

        DechiffrementService.DechiffrementResultat resultat = dechiffrementService.dechiffrerOffres(aoId, fragments,
                "membre-1");

        assertThat(resultat.offresDecryptees()).isEmpty();
        assertThat(resultat.totalTrouvees()).isEqualTo(0);
        verify(minIOService, never()).uploadBytes(any(), any(), any());
        verify(eventPublisher, never()).publierDemandeAnalyseOffreFinanciere(any());
    }

    @Test
    @DisplayName("Échec reconstitution clé → ChiffrementException propagée")
    void dechiffrerOffres_echecReconstitution_exception() {
        String aoId = "ao-004";

        when(appelOffreClient.getDateOuverturePlis(aoId)).thenReturn(Optional.empty());
        when(cleChiffrementService.reconstituerClePrivee(eq(aoId), anyList()))
                .thenThrow(new ChiffrementException("Fragments invalides"));

        assertThatThrownBy(() -> dechiffrementService.dechiffrerOffres(aoId, fragments, "membre-1"))
                .isInstanceOf(ChiffrementException.class)
                .hasMessageContaining("Fragments invalides");

        verify(auditLogService).logDechiffrement(aoId, "membre-1", 0, false);
    }

    @Test
    @DisplayName("Erreur déchiffrement d'une offre → erreur capturée, autres offres traitées")
    void dechiffrerOffres_erreurPartielle_autresTraitees() {
        String aoId = "ao-005";

        when(appelOffreClient.getDateOuverturePlis(aoId)).thenReturn(Optional.empty());
        when(cleChiffrementService.reconstituerClePrivee(eq(aoId), anyList()))
                .thenReturn(mockPrivateKey);

        // Deux offres : la première échoue, la seconde réussit
        Soumission soum1 = Soumission.builder()
                .id("soum-fail")
                .appelOffreId(aoId)
                .operateurId("op-1")
                .statut(com.klodit.soumission_service.enums.StatutSoumission.DEPOSEE)
                .build();
        OffreFinanciere offreFail = OffreFinanciere.builder()
                .id("of-fail")
                .soumission(soum1)
                .fichierChiffreUrl("offres-financieres/fail/fichier.enc")
                .isDechiffree(false)
                .build();

        Soumission soum2 = Soumission.builder()
                .id("soum-ok")
                .appelOffreId(aoId)
                .operateurId("op-2")
                .statut(com.klodit.soumission_service.enums.StatutSoumission.DEPOSEE)
                .build();
        OffreFinanciere offreOk = OffreFinanciere.builder()
                .id("of-ok")
                .soumission(soum2)
                .fichierChiffreUrl("offres-financieres/ok/fichier.enc")
                .hashFichier("hash")
                .isDechiffree(false)
                .build();

        when(offreFinanciereService.getOffresNonDechiffrees(aoId))
                .thenReturn(List.of(offreFail, offreOk));

        // Premier fichier : erreur de téléchargement
        when(minIOService.telechargerFichier("offres-financieres", "fail/fichier.enc"))
                .thenThrow(new RuntimeException("MinIO indisponible"));

        // Second fichier : OK
        byte[] fakeCleAES = new byte[512];
        byte[] fakeIV = new byte[12];
        byte[] fakeCipher = new byte[50];
        ByteBuffer env = ByteBuffer.allocate(4 + fakeCleAES.length + fakeIV.length + fakeCipher.length);
        env.putInt(fakeCleAES.length);
        env.put(fakeCleAES);
        env.put(fakeIV);
        env.put(fakeCipher);

        when(minIOService.telechargerFichier("offres-financieres", "ok/fichier.enc"))
                .thenReturn(new ByteArrayInputStream(env.array()));

        byte[] pdfClair = "%PDF-1.4 OK".getBytes();
        when(chiffrementService.dechiffrerCleAES(any(), eq(mockPrivateKey))).thenReturn(mockAESKey);
        when(chiffrementService.dechiffrerAES(any(), any(), eq(mockAESKey))).thenReturn(pdfClair);

        MinIOProperties.BucketConfig bc = new MinIOProperties.BucketConfig();
        bc.setOffresFinancieresClaires("offres-financieres-claires");
        when(minIOProperties.getBucket()).thenReturn(bc);
        when(minIOService.uploadBytes(any(), any(), any())).thenReturn("url-clair");

        // Act
        DechiffrementService.DechiffrementResultat resultat = dechiffrementService.dechiffrerOffres(aoId, fragments,
                "membre-1");

        // Assert
        assertThat(resultat.offresDecryptees()).hasSize(1);
        assertThat(resultat.erreurs()).hasSize(1);
        assertThat(resultat.erreurs().get(0)).contains("of-fail");
        assertThat(resultat.totalTrouvees()).isEqualTo(2);

        // L'événement OCR ne doit être publié que pour l'offre réussie
        verify(eventPublisher, times(1)).publierDemandeAnalyseOffreFinanciere(any());
    }

    @Test
    @DisplayName("Déchiffrement — les montants ne sont PAS remplis directement (délégation OCR)")
    void dechiffrerOffres_montantsNonRemplisDirectement() {
        String aoId = "ao-006";

        when(appelOffreClient.getDateOuverturePlis(aoId)).thenReturn(Optional.empty());
        when(cleChiffrementService.reconstituerClePrivee(eq(aoId), anyList()))
                .thenReturn(mockPrivateKey);

        Soumission soum = Soumission.builder()
                .id("soum-montants")
                .appelOffreId(aoId)
                .operateurId("op-1")
                .statut(com.klodit.soumission_service.enums.StatutSoumission.DEPOSEE)
                .build();
        OffreFinanciere offre = OffreFinanciere.builder()
                .id("of-montants")
                .soumission(soum)
                .fichierChiffreUrl("offres-financieres/test/fichier.enc")
                .hashFichier("hash")
                .isDechiffree(false)
                .build();

        when(offreFinanciereService.getOffresNonDechiffrees(aoId)).thenReturn(List.of(offre));

        byte[] enveloppeBytes = creerEnveloppeChiffreeSimulee();
        when(minIOService.telechargerFichier(any(), any()))
                .thenReturn(new ByteArrayInputStream(enveloppeBytes));

        byte[] pdfClair = "%PDF-1.4 Montant HT: 1500000".getBytes();
        when(chiffrementService.dechiffrerCleAES(any(), any())).thenReturn(mockAESKey);
        when(chiffrementService.dechiffrerAES(any(), any(), any())).thenReturn(pdfClair);

        MinIOProperties.BucketConfig bc = new MinIOProperties.BucketConfig();
        bc.setOffresFinancieresClaires("offres-financieres-claires");
        when(minIOProperties.getBucket()).thenReturn(bc);
        when(minIOService.uploadBytes(any(), any(), any())).thenReturn("url-clair");

        // Simuler le comportement réel de mettreAJourApresDecryptage
        doAnswer(invocation -> {
            OffreFinanciere of = invocation.getArgument(0);
            of.setIsDechiffree(true);
            of.setDateDechiffrement(LocalDateTime.now());
            return null;
        }).when(offreFinanciereService).mettreAJourApresDecryptage(any());

        DechiffrementService.DechiffrementResultat resultat = dechiffrementService.dechiffrerOffres(aoId, fragments,
                "membre-1");

        // Les montants doivent être NULL car c'est l'OCR qui les remplira
        OffreFinanciereResponse response = resultat.offresDecryptees().get(0);
        assertThat(response.getMontantHt()).isNull();
        assertThat(response.getTva()).isNull();
        assertThat(response.getMontantTtc()).isNull();

        // Mais isDechiffree doit être true et le fichier clair doit être défini
        assertThat(response.getIsDechiffree()).isTrue();
        assertThat(response.getFichierClairUrl()).isNotNull();
    }

    // ── Helper ─────────────────────────────────────

    private byte[] creerEnveloppeChiffreeSimulee() {
        byte[] fakeCleAES = new byte[512];
        byte[] fakeIV = new byte[12];
        byte[] fakeCipher = new byte[50];
        ByteBuffer env = ByteBuffer.allocate(4 + fakeCleAES.length + fakeIV.length + fakeCipher.length);
        env.putInt(fakeCleAES.length);
        env.put(fakeCleAES);
        env.put(fakeIV);
        env.put(fakeCipher);
        return env.array();
    }
}
