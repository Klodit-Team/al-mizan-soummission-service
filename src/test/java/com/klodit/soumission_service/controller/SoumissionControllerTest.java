package com.klodit.soumission_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klodit.soumission_service.config.SecurityConfig;
import com.klodit.soumission_service.dto.request.CreateSoumissionRequest;
import com.klodit.soumission_service.dto.request.StatutSoumissionRequest;
import com.klodit.soumission_service.dto.response.SoumissionDetailResponse;
import com.klodit.soumission_service.dto.response.SoumissionResponse;
import com.klodit.soumission_service.enums.StatutSoumission;
import com.klodit.soumission_service.exception.SoumissionNotFoundException;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.SoumissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SoumissionController.class)
@Import({ SecurityConfig.class })
@TestPropertySource(properties = "security.filter.enabled=false")
@DisplayName("SoumissionController — Tests MockMvc")
class SoumissionControllerTest {

        @Autowired
        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @MockitoBean
        private SoumissionService soumissionService;

        @MockitoBean
        private RbacGuard rbacGuard;

        @BeforeEach
        void setupRbacGuard() {
                when(rbacGuard.getUserId(any())).thenReturn("op-001");
        }

        // ── POST /api/v1/soumissions ──────────────────────────

        @Test
        @DisplayName("POST /api/v1/soumissions — 201 Created")
        void creerBrouillon_201() throws Exception {
                CreateSoumissionRequest request = CreateSoumissionRequest.builder()
                                .appelOffreId("ao-123")
                                .build();

                SoumissionResponse mockResponse = SoumissionResponse.builder()
                                .id("soum-001")
                                .appelOffreId("ao-123")
                                .operateurId("dev-user")
                                .reference("SOUM-20260227-00001")
                                .statut(StatutSoumission.BROUILLON)
                                .createdAt(LocalDateTime.now())
                                .build();

                when(soumissionService.creerBrouillon(any(), anyString())).thenReturn(mockResponse);

                mockMvc.perform(post("/api/v1/soumissions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.statut").value("BROUILLON"))
                                .andExpect(jsonPath("$.data.reference").value("SOUM-20260227-00001"));
        }

        @Test
        @DisplayName("POST /api/v1/soumissions — validation échouée → 400")
        void creerBrouillon_validation_400() throws Exception {
                String invalidBody = "{}";

                mockMvc.perform(post("/api/v1/soumissions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidBody))
                                .andExpect(status().isBadRequest());
        }

        // ── GET /api/v1/soumissions ───────────────────────────

        @Test
        @DisplayName("GET /api/v1/soumissions — liste vide → 200")
        void listerMesSoumissions_vide_200() throws Exception {
                when(soumissionService.listerMesSoumissions(anyString()))
                                .thenReturn(List.of());

                mockMvc.perform(get("/api/v1/soumissions"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("GET /api/v1/soumissions — avec résultats → 200")
        void listerMesSoumissions_avecResultats_200() throws Exception {
                SoumissionResponse s1 = SoumissionResponse.builder()
                                .id("soum-001").appelOffreId("ao-001").statut(StatutSoumission.BROUILLON).build();
                SoumissionResponse s2 = SoumissionResponse.builder()
                                .id("soum-002").appelOffreId("ao-002").statut(StatutSoumission.DEPOSEE).build();

                when(soumissionService.listerMesSoumissions("op-001"))
                                .thenReturn(List.of(s1, s2));

                mockMvc.perform(get("/api/v1/soumissions"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(2))
                                .andExpect(jsonPath("$.data[0].id").value("soum-001"));
        }

        // ── GET /api/v1/soumissions/{id} ──────────────────────

        @Test
        @DisplayName("GET /api/v1/soumissions/{id} — trouvée → 200")
        void getDetail_200() throws Exception {
                SoumissionDetailResponse detail = SoumissionDetailResponse.builder()
                                .id("soum-001").appelOffreId("ao-001")
                                .statut(StatutSoumission.BROUILLON).reference("REF-001")
                                .build();

                when(soumissionService.getDetail("soum-001")).thenReturn(detail);

                mockMvc.perform(get("/api/v1/soumissions/soum-001"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value("soum-001"))
                                .andExpect(jsonPath("$.data.reference").value("REF-001"));
        }

        @Test
        @DisplayName("GET /api/v1/soumissions/{id} — introuvable → 404")
        void getDetail_404() throws Exception {
                when(soumissionService.getDetail("soum-999"))
                                .thenThrow(new SoumissionNotFoundException("soum-999"));

                mockMvc.perform(get("/api/v1/soumissions/soum-999"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.success").value(false));
        }

        // ── GET /api/v1/soumissions/appel-offre/{aoId} ───────

        @Test
        @DisplayName("GET /api/v1/soumissions/appel-offre/{aoId} — 200")
        void listerParAO_200() throws Exception {
                SoumissionResponse s1 = SoumissionResponse.builder()
                                .id("soum-001").appelOffreId("ao-001")
                                .statut(StatutSoumission.DEPOSEE).build();

                when(soumissionService.listerParAppelOffre("ao-001"))
                                .thenReturn(List.of(s1));

                mockMvc.perform(get("/api/v1/soumissions/appel-offre/ao-001"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.length()").value(1))
                                .andExpect(jsonPath("$.data[0].appelOffreId").value("ao-001"));
        }

        // ── PUT /api/v1/soumissions/{id}/valider ──────────────

        @Test
        @DisplayName("PUT /api/v1/soumissions/{id}/valider — succès → 200")
        void valider_200() throws Exception {
                SoumissionResponse response = SoumissionResponse.builder()
                                .id("soum-001").statut(StatutSoumission.DEPOSEE)
                                .reference("REF-001").build();

                when(soumissionService.validerEtSoumettre(eq("soum-001"), eq("op-001"), anyString()))
                                .thenReturn(response);

                mockMvc.perform(put("/api/v1/soumissions/soum-001/valider"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.statut").value("DEPOSEE"));
        }

        @Test
        @DisplayName("PUT /api/v1/soumissions/{id}/valider — introuvable → 404")
        void valider_notFound_404() throws Exception {
                when(soumissionService.validerEtSoumettre(eq("soum-999"), anyString(), anyString()))
                                .thenThrow(new SoumissionNotFoundException("soum-999"));

                mockMvc.perform(put("/api/v1/soumissions/soum-999/valider"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("PUT /api/v1/soumissions/{id}/valider — dossier incomplet → 409")
        void valider_incomplet_409() throws Exception {
                when(soumissionService.validerEtSoumettre(eq("soum-001"), anyString(), anyString()))
                                .thenThrow(new IllegalStateException("L'offre technique est manquante"));

                mockMvc.perform(put("/api/v1/soumissions/soum-001/valider"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.success").value(false));
        }

        // ── PUT /api/v1/soumissions/{id}/statut ──────────────

        @Test
        @DisplayName("PUT /api/v1/soumissions/{id}/statut — succès → 200")
        void changerStatut_200() throws Exception {
                StatutSoumissionRequest req = new StatutSoumissionRequest();
                req.setStatut(StatutSoumission.RECUE);

                SoumissionResponse response = SoumissionResponse.builder()
                                .id("soum-001").statut(StatutSoumission.RECUE).build();

                when(soumissionService.changerStatut("soum-001", StatutSoumission.RECUE))
                                .thenReturn(response);

                mockMvc.perform(put("/api/v1/soumissions/soum-001/statut")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.statut").value("RECUE"));
        }

        @Test
        @DisplayName("PUT /api/v1/soumissions/{id}/statut — transition invalide → 409")
        void changerStatut_transitionInvalide_409() throws Exception {
                StatutSoumissionRequest req = new StatutSoumissionRequest();
                req.setStatut(StatutSoumission.RETENUE);

                when(soumissionService.changerStatut("soum-001", StatutSoumission.RETENUE))
                                .thenThrow(new IllegalStateException("Transition de statut invalide"));

                mockMvc.perform(put("/api/v1/soumissions/soum-001/statut")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isConflict());
        }
}
