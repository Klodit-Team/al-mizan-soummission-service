package com.klodit.soumission_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klodit.soumission_service.dto.request.CreateSoumissionRequest;
import com.klodit.soumission_service.entity.LigneOffreFinanciere;
import com.klodit.soumission_service.repository.LigneOffreFinanciereRepository;
import com.klodit.soumission_service.repository.SoumissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test d'intégration du flux complet de soumission :
 * US-1 → Créer brouillon
 * US-2 → Déposer offre technique
 * US-4 → Déposer caution
 * US-3 → Déposer offre financière (chiffrée)
 * US-5 → Valider et soumettre
 *
 * Infrastructure : MySQL + RabbitMQ + MinIO + Redis (Testcontainers)
 */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Flux complet de soumission (intégration)")
class SoumissionFlowIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private SoumissionRepository soumissionRepository;

        @Autowired
        private LigneOffreFinanciereRepository ligneOffreFinanciereRepository;

        @Autowired
        private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

        private static final String OPERATOR_ID = "op-integration-test-001";
        private static final String AO_ID = "ao-integration-test-001";
        private static final String LOT_ID = "lot-integration-test-001";
        private static final String BASE_URL = "/api/v1";

        // Stocké entre les tests (attention : @TestMethodOrder requis)
        private static String soumissionId;
        private static String articleId;

        private static boolean isCleaned = false;

        @BeforeEach
        void setUp() {
                if (!isCleaned) {
                        transactionTemplate.executeWithoutResult(status -> {
                                java.util.List<com.klodit.soumission_service.entity.Soumission> soumissions = soumissionRepository.findByAppelOffreIdOrderByCreatedAtDesc(AO_ID);
                                for (com.klodit.soumission_service.entity.Soumission soumission : soumissions) {
                                        ligneOffreFinanciereRepository.deleteBySoumissionId(soumission.getId());
                                        soumissionRepository.delete(soumission);
                                }
                        });
                        isCleaned = true;
                }
        }

        @Test
        @Order(1)
        @DisplayName("US-1 — Créer un brouillon de soumission")
        void creerBrouillon() throws Exception {
                CreateSoumissionRequest request = CreateSoumissionRequest.builder()
                                .appelOffreId(AO_ID)
                                .lotId(LOT_ID)
                                .build();

                String responseJson = mockMvc.perform(post(BASE_URL + "/soumissions")
                                .header("X-User-Id", OPERATOR_ID)
                                .header("X-User-Role", "OPERATEUR_ECONOMIQUE")
                                .requestAttr("userId", OPERATOR_ID)
                                .requestAttr("userRole", "OPERATEUR_ECONOMIQUE")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.statut").value("BROUILLON"))
                                .andExpect(jsonPath("$.data.appelOffreId").value(AO_ID))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                // Extraire l'ID pour les tests suivants
                Map<String, Object> body = objectMapper.readValue(responseJson, Map.class);
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                soumissionId = (String) data.get("id");

                assertThat(soumissionId).isNotNull().isNotBlank();

                // Pré-populer une ligne de BPU pour cette soumission dans l'intégration
                LigneOffreFinanciere line = LigneOffreFinanciere.builder()
                                .soumission(soumissionRepository.findById(soumissionId).get())
                                .designation("Lot de test")
                                .quantite(java.math.BigDecimal.ONE)
                                .unite("LOT")
                                .prixUnitaire(null)
                                .build();
                LigneOffreFinanciere savedLine = ligneOffreFinanciereRepository.save(line);
                articleId = savedLine.getId();
                assertThat(articleId).isNotNull().isNotBlank();
        }

        @Test
        @Order(2)
        @DisplayName("US-2 — Déposer l'offre technique (upload PDF)")
        void deposerOffreTechnique() throws Exception {
                MockMultipartFile fichier = new MockMultipartFile(
                                "fichier",
                                "offre-technique.pdf",
                                "application/pdf",
                                "Contenu PDF de test pour l'offre technique".getBytes());

                mockMvc.perform(multipart(BASE_URL + "/soumissions/{id}/offre-technique", soumissionId)
                                .file(fichier)
                                .header("X-User-Id", OPERATOR_ID)
                                .header("X-User-Role", "OPERATEUR_ECONOMIQUE")
                                .requestAttr("userId", OPERATOR_ID)
                                .requestAttr("userRole", "OPERATEUR_ECONOMIQUE"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.hashFichier").isNotEmpty());
        }

        @Test
        @Order(3)
        @DisplayName("US-4 — Déposer la caution bancaire")
        void deposerCaution() throws Exception {
                MockMultipartFile scanCaution = new MockMultipartFile(
                                "scanCaution",
                                "caution.pdf",
                                "application/pdf",
                                "Attestation de caution bancaire de test".getBytes());

                String cautionJson = objectMapper.writeValueAsString(Map.of(
                                "compteBancaireId", "rib-integration-001",
                                "reference", "CAUT-2025-001",
                                "dateExpiration", "2027-07-15T00:00:00"));

                MockMultipartFile donnees = new MockMultipartFile(
                                "donnees",
                                "",
                                "application/json",
                                cautionJson.getBytes());

                mockMvc.perform(multipart(BASE_URL + "/soumissions/{id}/caution", soumissionId)
                                .file(scanCaution)
                                .file(donnees)
                                .header("X-User-Id", OPERATOR_ID)
                                .header("X-User-Role", "OPERATEUR_ECONOMIQUE")
                                .requestAttr("userId", OPERATOR_ID)
                                .requestAttr("userRole", "OPERATEUR_ECONOMIQUE"))
                                .andDo(result -> System.out.println("=== CAUTION RESPONSE === "
                                                + result.getResponse().getContentAsString()))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @Order(4)
        @DisplayName("US-3 — Déposer l'offre financière (chiffrée)")
        void deposerOffreFinanciere() throws Exception {
                MockMultipartFile fichier = new MockMultipartFile(
                                "fichierChiffre",
                                "offre-financiere.enc",
                                "application/octet-stream",
                                "Contenu chiffré de l'offre financière".getBytes());

                String requestJson = objectMapper.writeValueAsString(Map.of(
                                "signatureEcdsa", "dummySignature==",
                                "clePubliqueEcdsaPem", "-----BEGIN PUBLIC KEY-----\nMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE\n-----END PUBLIC KEY-----",
                                "lignes", java.util.List.of(
                                                 Map.of("articleId", articleId, "prixUnitaire", 1000.00)
                                )
                ));

                MockMultipartFile donnees = new MockMultipartFile(
                                "donnees",
                                "",
                                "application/json",
                                requestJson.getBytes());

                mockMvc.perform(multipart(BASE_URL + "/soumissions/{id}/offre-financiere", soumissionId)
                                .file(fichier)
                                .file(donnees)
                                .header("X-User-Id", OPERATOR_ID)
                                .header("X-User-Role", "OPERATEUR_ECONOMIQUE")
                                .requestAttr("userId", OPERATOR_ID)
                                .requestAttr("userRole", "OPERATEUR_ECONOMIQUE"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @Order(5)
        @DisplayName("US-5 — Valider et soumettre le dossier complet")
        void validerEtSoumettre() throws Exception {
                mockMvc.perform(put(BASE_URL + "/soumissions/{id}/valider", soumissionId)
                                .header("X-User-Id", OPERATOR_ID)
                                .header("X-User-Role", "OPERATEUR_ECONOMIQUE")
                                .requestAttr("userId", OPERATOR_ID)
                                .requestAttr("userRole", "OPERATEUR_ECONOMIQUE"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.statut").value("DEPOSEE"));
        }

        @Test
        @Order(6)
        @DisplayName("Vérifier la soumission après dépôt — détails complets")
        void getDetailSoumission() throws Exception {
                mockMvc.perform(get(BASE_URL + "/soumissions/{id}", soumissionId)
                                .header("X-User-Id", OPERATOR_ID)
                                .header("X-User-Role", "OPERATEUR_ECONOMIQUE")
                                .requestAttr("userId", OPERATOR_ID)
                                .requestAttr("userRole", "OPERATEUR_ECONOMIQUE"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.statut").value("DEPOSEE"))
                                .andExpect(jsonPath("$.data.isDansDelai").value(true));
        }
}
