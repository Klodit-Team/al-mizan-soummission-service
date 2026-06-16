package com.klodit.soumission_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.klodit.soumission_service.config.JacksonConfig;
import com.klodit.soumission_service.config.SecurityConfig;
import com.klodit.soumission_service.dto.response.CautionResponse;
import com.klodit.soumission_service.enums.StatutCaution;
import com.klodit.soumission_service.exception.RessourceIntrouvableException;
import com.klodit.soumission_service.exception.SoumissionNotFoundException;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.CautionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CautionController.class)
@Import({ SecurityConfig.class, JacksonConfig.class })
@TestPropertySource(properties = "security.filter.enabled=false")
@DisplayName("CautionController — Tests MockMvc")
class CautionControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private CautionService cautionService;

        @MockitoBean
        private RbacGuard rbacGuard;

        @BeforeEach
        void setupRbacGuard() {
                when(rbacGuard.getUserId(any())).thenReturn("op-001");
        }

        // ── POST /api/v1/soumissions/{soumissionId}/caution ──

        @Test
        @DisplayName("POST caution — succès → 201")
        void ajouterCaution_201() throws Exception {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());

                LocalDateTime emission = LocalDateTime.of(2025, 1, 1, 0, 0);
                LocalDateTime expiration = LocalDateTime.of(2026, 1, 1, 0, 0);

                String donnees = mapper.writeValueAsString(
                                java.util.Map.of(
                                                "banque", "CPA",
                                                "montant", 1000000,
                                                "reference", "CB-2025-001",
                                                "dateEmission", "2025-01-01T00:00:00",
                                                "dateExpiration", "2026-01-01T00:00:00"));

                MockMultipartFile donneesFile = new MockMultipartFile(
                                "donnees", "", "application/json", donnees.getBytes());
                MockMultipartFile scanFile = new MockMultipartFile(
                                "scanCaution", "caution.pdf", "application/pdf", "scan-content".getBytes());

                CautionResponse response = CautionResponse.builder()
                                .id("cau-001")
                                .banque("CPA")
                                .montant(java.math.BigDecimal.valueOf(1000000))
                                .dateEmission(emission)
                                .reference("CB-2025-001")
                                .statut(StatutCaution.VALIDE)
                                .dateExpiration(expiration)
                                .build();

                when(cautionService.ajouterCaution(eq("soum-001"), eq("op-001"), any(), any()))
                                .thenReturn(response);

                mockMvc.perform(multipart("/api/v1/soumissions/soum-001/caution")
                                .file(donneesFile)
                                .file(scanFile))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.id").value("cau-001"))
                                .andExpect(jsonPath("$.data.banque").value("CPA"));
        }

        @Test
        @DisplayName("POST caution — soumission introuvable → 404")
        void ajouterCaution_notFound_404() throws Exception {
                MockMultipartFile donneesFile = new MockMultipartFile(
                                "donnees", "", "application/json",
                                "{\"banque\":\"CPA\",\"montant\":1000000,\"reference\":\"ref\",\"dateEmission\":\"2025-01-01T00:00:00\",\"dateExpiration\":\"2026-01-01T00:00:00\"}"
                                                .getBytes());
                MockMultipartFile scanFile = new MockMultipartFile(
                                "scanCaution", "scan.pdf", "application/pdf", "data".getBytes());

                when(cautionService.ajouterCaution(eq("soum-999"), anyString(), any(), any()))
                                .thenThrow(new SoumissionNotFoundException("soum-999"));

                mockMvc.perform(multipart("/api/v1/soumissions/soum-999/caution")
                                .file(donneesFile)
                                .file(scanFile))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST caution — statut non BROUILLON → 409")
        void ajouterCaution_statutInvalide_409() throws Exception {
                MockMultipartFile donneesFile = new MockMultipartFile(
                                "donnees", "", "application/json",
                                "{\"banque\":\"CPA\",\"montant\":1000000,\"reference\":\"ref\",\"dateEmission\":\"2025-01-01T00:00:00\",\"dateExpiration\":\"2026-01-01T00:00:00\"}"
                                                .getBytes());
                MockMultipartFile scanFile = new MockMultipartFile(
                                "scanCaution", "scan.pdf", "application/pdf", "data".getBytes());

                when(cautionService.ajouterCaution(eq("soum-001"), anyString(), any(), any()))
                                .thenThrow(new IllegalStateException("caution ne peut être ajoutée qu'en BROUILLON"));

                mockMvc.perform(multipart("/api/v1/soumissions/soum-001/caution")
                                .file(donneesFile)
                                .file(scanFile))
                                .andExpect(status().isConflict());
        }

        // ── GET /api/v1/soumissions/{soumissionId}/caution ──

        @Test
        @DisplayName("GET caution — trouvée → 200")
        void getCaution_200() throws Exception {
                CautionResponse response = CautionResponse.builder()
                                .id("cau-001")
                                .banque("CPA")
                                .montant(java.math.BigDecimal.valueOf(1000000))
                                .dateEmission(LocalDateTime.of(2025, 1, 1, 0, 0))
                                .reference("CB-001")
                                .statut(StatutCaution.VALIDE)
                                .build();

                when(cautionService.getCaution("soum-001")).thenReturn(response);

                mockMvc.perform(get("/api/v1/soumissions/soum-001/caution"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.banque").value("CPA"))
                                .andExpect(jsonPath("$.data.statut").value("VALIDE"));
        }

        @Test
        @DisplayName("GET caution — introuvable → 404")
        void getCaution_404() throws Exception {
                when(cautionService.getCaution("soum-999"))
                                .thenThrow(new RessourceIntrouvableException("Caution", "soumission soum-999"));

                mockMvc.perform(get("/api/v1/soumissions/soum-999/caution"))
                                .andExpect(status().isNotFound());
        }
}
