package com.klodit.soumission_service.controller;

import com.klodit.soumission_service.config.SecurityConfig;
import com.klodit.soumission_service.dto.response.OffreTechniqueResponse;
import com.klodit.soumission_service.exception.RessourceIntrouvableException;
import com.klodit.soumission_service.exception.SoumissionNotFoundException;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.OffreTechniqueService;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OffreTechniqueController.class)
@Import({ SecurityConfig.class })
@TestPropertySource(properties = "security.filter.enabled=false")
@DisplayName("OffreTechniqueController — Tests MockMvc")
class OffreTechniqueControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private OffreTechniqueService offreTechniqueService;

        @MockitoBean
        private RbacGuard rbacGuard;

        @BeforeEach
        void setupRbacGuard() {
                when(rbacGuard.getUserId(any())).thenReturn("op-001");
        }

        // ── POST /api/v1/soumissions/{soumissionId}/offre-technique ──

        @Test
        @DisplayName("POST offre-technique — upload succès → 201")
        void deposerOffreTechnique_201() throws Exception {
                MockMultipartFile fichier = new MockMultipartFile(
                                "fichier", "cahier.pdf", "application/pdf", "fake pdf content".getBytes());

                OffreTechniqueResponse response = OffreTechniqueResponse.builder()
                                .id("ot-001")
                                .fichierUrl("offres-techniques/soum-001/uuid-cahier.pdf")
                                .hashFichier("abc123hash")
                                .createdAt(LocalDateTime.now())
                                .build();

                when(offreTechniqueService.deposerOffreTechnique(
                                eq("soum-001"), eq("op-001"), any(), isNull()))
                                .thenReturn(response);

                mockMvc.perform(multipart("/api/v1/soumissions/soum-001/offre-technique")
                                .file(fichier))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.id").value("ot-001"))
                                .andExpect(jsonPath("$.data.hashFichier").value("abc123hash"));
        }

        @Test
        @DisplayName("POST offre-technique — avec hashClient → 201")
        void deposerOffreTechnique_avecHash_201() throws Exception {
                MockMultipartFile fichier = new MockMultipartFile(
                                "fichier", "doc.pdf", "application/pdf", "data".getBytes());

                OffreTechniqueResponse response = OffreTechniqueResponse.builder()
                                .id("ot-002").hashFichier("serverHash").build();

                when(offreTechniqueService.deposerOffreTechnique(
                                eq("soum-001"), eq("op-001"), any(), eq("clientHash")))
                                .thenReturn(response);

                mockMvc.perform(multipart("/api/v1/soumissions/soum-001/offre-technique")
                                .file(fichier)
                                .param("hashClient", "clientHash"))
                                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("POST offre-technique — soumission introuvable → 404")
        void deposerOffreTechnique_notFound_404() throws Exception {
                MockMultipartFile fichier = new MockMultipartFile(
                                "fichier", "doc.pdf", "application/pdf", "data".getBytes());

                when(offreTechniqueService.deposerOffreTechnique(
                                eq("soum-999"), anyString(), any(), any()))
                                .thenThrow(new SoumissionNotFoundException("soum-999"));

                mockMvc.perform(multipart("/api/v1/soumissions/soum-999/offre-technique")
                                .file(fichier))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST offre-technique — statut non BROUILLON → 409")
        void deposerOffreTechnique_statutInvalide_409() throws Exception {
                MockMultipartFile fichier = new MockMultipartFile(
                                "fichier", "doc.pdf", "application/pdf", "data".getBytes());

                when(offreTechniqueService.deposerOffreTechnique(
                                eq("soum-001"), anyString(), any(), any()))
                                .thenThrow(new IllegalStateException("Statut non BROUILLON"));

                mockMvc.perform(multipart("/api/v1/soumissions/soum-001/offre-technique")
                                .file(fichier))
                                .andExpect(status().isConflict());
        }

        // ── GET /api/v1/soumissions/{soumissionId}/offre-technique ──

        @Test
        @DisplayName("GET offre-technique — trouvée → 200")
        void getOffreTechnique_200() throws Exception {
                OffreTechniqueResponse response = OffreTechniqueResponse.builder()
                                .id("ot-001").fichierUrl("url").hashFichier("hash")
                                .isConforme(true).observations("OK").build();

                when(offreTechniqueService.getOffreTechnique("soum-001")).thenReturn(response);

                mockMvc.perform(get("/api/v1/soumissions/soum-001/offre-technique"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value("ot-001"))
                                .andExpect(jsonPath("$.data.isConforme").value(true));
        }

        @Test
        @DisplayName("GET offre-technique — introuvable → 404")
        void getOffreTechnique_404() throws Exception {
                when(offreTechniqueService.getOffreTechnique("soum-999"))
                                .thenThrow(new RessourceIntrouvableException("Offre technique", "soumission soum-999"));

                mockMvc.perform(get("/api/v1/soumissions/soum-999/offre-technique"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.success").value(false));
        }
}
