package com.klodit.soumission_service.service;

import com.klodit.soumission_service.entity.CleChiffrement;
import com.klodit.soumission_service.enums.StatutCle;
import com.klodit.soumission_service.exception.ChiffrementException;
import com.klodit.soumission_service.exception.FichierInvalideException;
import com.klodit.soumission_service.repository.CleChiffrementRepository;
import com.klodit.soumission_service.repository.FragmentCleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CleChiffrementService — Tests unitaires Phase 3")
class CleChiffrementServiceTest {

    @Mock
    private CleChiffrementRepository cleChiffrementRepository;
    @Mock
    private FragmentCleRepository fragmentCleRepository;
    @Mock
    private ChiffrementService chiffrementService;

    @InjectMocks
    private CleChiffrementService cleChiffrementService;

    @BeforeEach
    void setUp() {
        // Injecter les valeurs @Value manuellement car Mockito ne traite pas @Value
        ReflectionTestUtils.setField(cleChiffrementService, "totalShares", 5);
        ReflectionTestUtils.setField(cleChiffrementService, "threshold", 3);
    }

    @Test
    @DisplayName("genererCles — clés existantes → IllegalStateException")
    void genererCles_clesExistantes_exception() {
        CleChiffrement existing = CleChiffrement.builder()
                .id("cle-001")
                .appelOffreId("ao-001")
                .statut(StatutCle.ACTIVE)
                .build();

        when(cleChiffrementRepository.findByAppelOffreId("ao-001"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> cleChiffrementService.genererCles("ao-001",
                List.of("m1", "m2", "m3", "m4", "m5")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("existent déjà");
    }

    @Test
    @DisplayName("genererCles — membres insuffisants → FichierInvalideException")
    void genererCles_membresInsuffisants_exception() {
        when(cleChiffrementRepository.findByAppelOffreId("ao-002"))
                .thenReturn(Optional.empty());

        // Fournir seulement 2 membres alors que totalShares=5
        assertThatThrownBy(() -> cleChiffrementService.genererCles("ao-002",
                List.of("m1", "m2")))
                .isInstanceOf(FichierInvalideException.class)
                .hasMessageContaining("insuffisant");
    }

    @Test
    @DisplayName("reconstituer — clé déjà utilisée → ChiffrementException")
    void reconstituer_cleUtilisee_exception() {
        CleChiffrement cle = CleChiffrement.builder()
                .id("cle-003")
                .appelOffreId("ao-003")
                .statut(StatutCle.UTILISEE)
                .build();

        when(cleChiffrementRepository.findByAppelOffreId("ao-003"))
                .thenReturn(Optional.of(cle));

        assertThatThrownBy(() -> cleChiffrementService.reconstituerClePrivee("ao-003", List.of()))
                .isInstanceOf(ChiffrementException.class)
                .hasMessageContaining("déjà été utilisées");
    }

    @Test
    @DisplayName("reconstituer — fragments insuffisants → ChiffrementException")
    void reconstituer_fragmentsInsuffisants_exception() {
        CleChiffrement cle = CleChiffrement.builder()
                .id("cle-004")
                .appelOffreId("ao-004")
                .statut(StatutCle.ACTIVE)
                .build();

        when(cleChiffrementRepository.findByAppelOffreId("ao-004"))
                .thenReturn(Optional.of(cle));

        // Fournir un seul fragment alors que threshold=3
        var fragment = new com.klodit.soumission_service.dto.request.DechiffrementRequest.FragmentSoumis();
        fragment.setIndex(1);
        fragment.setValeur(java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));

        assertThatThrownBy(() -> cleChiffrementService.reconstituerClePrivee("ao-004", List.of(fragment)))
                .isInstanceOf(ChiffrementException.class)
                .hasMessageContaining("insuffisants");
    }
}
