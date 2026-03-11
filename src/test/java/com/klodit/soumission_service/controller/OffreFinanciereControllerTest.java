package com.klodit.soumission_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.klodit.soumission_service.config.SecurityConfig;
import com.klodit.soumission_service.dto.request.DechiffrementRequest;
import com.klodit.soumission_service.dto.response.OffreFinanciereResponse;
import com.klodit.soumission_service.exception.RessourceIntrouvableException;
import com.klodit.soumission_service.exception.SoumissionNotFoundException;
import com.klodit.soumission_service.security.RbacGuard;
import com.klodit.soumission_service.service.DechiffrementService;
import com.klodit.soumission_service.service.OffreFinanciereService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OffreFinanciereController.class)
@Import({ SecurityConfig.class })
@TestPropertySource(properties = "security.filter.enabled=false")
@DisplayName("OffreFinanciereController — Tests MockMvc")
class OffreFinanciereControllerTest {

        @Autowired
        private MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @MockitoBean
        private OffreFinanciereService offreFinanciereService;

        @MockitoBean
        private DechiffrementService dechiffrementService;

        @MockitoBean
        private RbacGuard rbacGuard;

        // ── POST /api/v1/soumissions/{id}/offre-financiere ──

        @Test
        @DisplayName("POST offre-financiere — upload chiffré → 201")
        void deposerOffreFinanciere_201() throws Exception {
                when(rbacGuard.getUserId(any())).thenReturn("op-001");

                MockMultipartFile fichier = new MockMultipartFile(
                                "fichierChiffre", "offre.enc", "application/octet-stream", "ciphertext".getBytes());

                OffreFinanciereResponse response = OffreFinanciereResponse.builder()
                                .id("of-001")
                                .fichierChiffreUrl("offres-financieres/soum-001/uuid-offre.enc")
                                .hashFichier("hash123")
                                .signatureVerifiee(true)
                                .isDechiffree(false)
                                .createdAt(LocalDateTime.now())
                                .build();

                when(offreFinanciereService.deposerOffreFinanciere(
                                eq("soum-001"), eq("op-001"), any(), eq("clientHash"),
                                eq("sig-base64"), eq("PEM-key")))
                                .thenReturn(response);

                mockMvc.perform(multipart("/api/v1/soumissions/soum-001/offre-financiere")
                                .file(fichier)
                                .param("hashClient", "clientHash")
                                .param("signatureEcdsa", "sig-base64")
                                .param("clePubliqueEcdsaPem", "PEM-key"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.id").value("of-001"))
                                .andExpect(jsonPath("$.data.isDechiffree").value(false));
        }

        @Test
        @DisplayName("POST offre-financiere — soumission introuvable → 404")
        void deposerOffreFinanciere_notFound_404() throws Exception {
                when(rbacGuard.getUserId(any())).thenReturn("op-001");

                MockMultipartFile fichier = new MockMultipartFile(
                                "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                when(offreFinanciereService.deposerOffreFinanciere(
                                eq("soum-999"), anyString(), any(), any(), any(), any()))
                                .thenThrow(new SoumissionNotFoundException("soum-999"));

                mockMvc.perform(multipart("/api/v1/soumissions/soum-999/offre-financiere")
                                .file(fichier)
                                .param("signatureEcdsa", "sig")
                                .param("clePubliqueEcdsaPem", "pem"))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST offre-financiere — statut invalide → 409")
        void deposerOffreFinanciere_statutInvalide_409() throws Exception {
                when(rbacGuard.getUserId(any())).thenReturn("op-001");

                MockMultipartFile fichier = new MockMultipartFile(
                                "fichierChiffre", "offre.enc", "application/octet-stream", "data".getBytes());

                when(offreFinanciereService.deposerOffreFinanciere(
                                eq("soum-001"), anyString(), any(), any(), any(), any()))
                                .thenThrow(new IllegalStateException("pas en BROUILLON"));

                mockMvc.perform(multipart("/api/v1/soumissions/soum-001/offre-financiere")
                                .file(fichier)
                                .param("signatureEcdsa", "sig")
                                .param("clePubliqueEcdsaPem", "pem"))
                                .andExpect(status().isConflict());
        }

        // ── GET /api/v1/soumissions/{id}/offre-financiere ──

        @Test
        @DisplayName("GET offre-financiere — trouvée → 200")
        void getOffreFinanciere_200() throws Exception {
                OffreFinanciereResponse response = OffreFinanciereResponse.builder()
                                .id("of-001")
                                .fichierChiffreUrl("url.enc")
                                .hashFichier("hash")
                                .isDechiffree(true)
                                .montantHt(new BigDecimal("1000000"))
                                .tva(new BigDecimal("190000"))
                                .montantTtc(new BigDecimal("1190000"))
                                .build();

                when(offreFinanciereService.getOffreFinanciere("soum-001")).thenReturn(response);

                mockMvc.perform(get("/api/v1/soumissions/soum-001/offre-financiere"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.isDechiffree").value(true))
                                .andExpect(jsonPath("$.data.montantHt").value(1000000));
        }

        @Test
        @DisplayName("GET offre-financiere — introuvable → 404")
        void getOffreFinanciere_404() throws Exception {
                when(offreFinanciereService.getOffreFinanciere("soum-999"))
                                .thenThrow(new RessourceIntrouvableException("Offre financière",
                                                "soumission soum-999"));

                mockMvc.perform(get("/api/v1/soumissions/soum-999/offre-financiere"))
                                .andExpect(status().isNotFound());
        }

        // ── POST /api/v1/offres-financieres/dechiffrer/{aoId} ──

        @Test
        @DisplayName("POST dechiffrer — succès → 200")
        void dechiffrer_200() throws Exception {
                when(rbacGuard.getUserId(any())).thenReturn("comm-001");

                DechiffrementRequest request = DechiffrementRequest.builder()
                                .fragments(List.of(
                                                DechiffrementRequest.FragmentSoumis.builder()
                                                                .index(1).valeur("frag1base64").membreId("m1").build(),
                                                DechiffrementRequest.FragmentSoumis.builder()
                                                                .index(2).valeur("frag2base64").membreId("m2").build(),
                                                DechiffrementRequest.FragmentSoumis.builder()
                                                                .index(3).valeur("frag3base64").membreId("m3").build()))
                                .build();

                OffreFinanciereResponse of1 = OffreFinanciereResponse.builder().id("of-001").isDechiffree(true).build();
                OffreFinanciereResponse of2 = OffreFinanciereResponse.builder().id("of-002").isDechiffree(true).build();

                DechiffrementService.DechiffrementResultat resultat = new DechiffrementService.DechiffrementResultat(
                                List.of(of1, of2), java.util.Collections.emptyList(), 2);

                when(dechiffrementService.dechiffrerOffres(eq("ao-001"), any(), eq("comm-001")))
                                .thenReturn(resultat);

                mockMvc.perform(post("/api/v1/offres-financieres/dechiffrer/ao-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("POST dechiffrer — fragments vides → 400")
        void dechiffrer_fragmentsVides_400() throws Exception {
                String body = "{\"fragments\":[]}";

                mockMvc.perform(post("/api/v1/offres-financieres/dechiffrer/ao-001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isBadRequest());
        }
}
