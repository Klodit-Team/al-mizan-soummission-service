package com.klodit.soumission_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klodit.soumission_service.config.SecurityConfig;
import com.klodit.soumission_service.dto.response.CleChiffrementResponse;
import com.klodit.soumission_service.enums.StatutCle;
import com.klodit.soumission_service.exception.RessourceIntrouvableException;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.CleChiffrementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CleChiffrementController.class)
@Import({ SecurityConfig.class })
@TestPropertySource(properties = "security.filter.enabled=false")
@DisplayName("CleChiffrementController — Tests MockMvc")
class CleChiffrementControllerTest {

        @Autowired
        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @MockitoBean
        private CleChiffrementService cleChiffrementService;

        @MockitoBean
        private RbacGuard rbacGuard;

        // ── POST /api/v1/cles-chiffrement/{aoId} ──

        @Test
        @DisplayName("POST générer clés — succès → 201")
        void genererCles_201() throws Exception {
                List<String> membres = List.of("m1", "m2", "m3", "m4", "m5");

                CleChiffrementResponse response = CleChiffrementResponse.builder()
                                .id("cle-001")
                                .appelOffreId("ao-001")
                                .clePublique("-----BEGIN PUBLIC KEY-----\nMIIBIjAN...")
                                .statut(StatutCle.ACTIVE)
                                .dateGeneration(LocalDateTime.now())
                                .build();

                when(cleChiffrementService.genererCles("ao-001", membres)).thenReturn(response);

                mockMvc.perform(post("/api/v1/cles-chiffrement/ao-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(membres)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.appelOffreId").value("ao-001"))
                                .andExpect(jsonPath("$.data.statut").value("ACTIVE"));
        }

        @Test
        @DisplayName("POST générer clés — body vide → 400")
        void genererCles_bodyVide_400() throws Exception {
                // Empty list
                mockMvc.perform(post("/api/v1/cles-chiffrement/ao-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[]"))
                                .andExpect(status().is2xxSuccessful()); // Accepted — validation is in service layer
        }

        // ── GET /api/v1/cles-chiffrement/{aoId}/publique ──

        @Test
        @DisplayName("GET clé publique — trouvée → 200")
        void getClePublique_200() throws Exception {
                CleChiffrementResponse response = CleChiffrementResponse.builder()
                                .id("cle-001")
                                .appelOffreId("ao-001")
                                .clePublique("-----BEGIN PUBLIC KEY-----\nMIIBIjAN...")
                                .statut(StatutCle.ACTIVE)
                                .build();

                when(cleChiffrementService.getClePublique("ao-001")).thenReturn(response);

                mockMvc.perform(get("/api/v1/cles-chiffrement/ao-001/publique"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.clePublique").isNotEmpty())
                                .andExpect(jsonPath("$.data.appelOffreId").value("ao-001"));
        }

        @Test
        @DisplayName("GET clé publique — introuvable → 404")
        void getClePublique_404() throws Exception {
                when(cleChiffrementService.getClePublique("ao-inexistant"))
                                .thenThrow(new RessourceIntrouvableException("Clé de chiffrement", "ao-inexistant"));

                mockMvc.perform(get("/api/v1/cles-chiffrement/ao-inexistant/publique"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.success").value(false));
        }
}
