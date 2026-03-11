package com.klodit.soumission_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klodit.soumission_service.dto.request.CreateSoumissionRequest;
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

        private static final String OPERATOR_ID = "op-integration-test-001";
        private static final String AO_ID = "ao-integration-test-001";
        private static final String LOT_ID = "lot-integration-test-001";
        private static final String BASE_URL = "/api/v1";

        // Stocké entre les tests (attention : @TestMethodOrder requis)
        private static String soumissionId;

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
                                "montant", 500000.00,
                                "banque", "BNA",
                                "reference", "CAUT-2025-001",
                                "dateEmission", "2026-01-15T00:00:00",
                                "dateExpiration", "2027-07-15T00:00:00"));

                MockMultipartFile donnees = new MockMultipartFile(
                                "donnees",
                                "",
                                "text/plain",
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

                mockMvc.perform(multipart(BASE_URL + "/soumissions/{id}/offre-financiere", soumissionId)
                                .file(fichier)
                                .param("signatureEcdsa", "dummySignature==")
                                .param("clePubliqueEcdsaPem",
                                                "-----BEGIN PUBLIC KEY-----\nMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE\n-----END PUBLIC KEY-----")
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
