package com.klodit.soumission_service.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RateLimitingFilter — Tests unitaires")
class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Requête normale → passée avec header X-RateLimit-Remaining")
    void requeteNormale_passee() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/soumissions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isNotNull();
    }

    @Test
    @DisplayName("Endpoint sensible /valider → bucket sensible utilisé")
    void endpointSensible_bucketSensible() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/soumissions/123/valider");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Endpoint sensible /dechiffrer → bucket sensible utilisé")
    void endpointDechiffrer_sensible() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/dechiffrer");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Endpoint /cles-chiffrement → bucket sensible utilisé")
    void endpointClesChiffrement_sensible() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cles-chiffrement");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Dépassement rate limit sensible (11 requêtes) → 429")
    void depassementRateLimit_sensible_retourne429() throws Exception {
        // Envoyer 10 requêtes pour épuiser le bucket sensible (10/min)
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/soumissions/123/valider");
            req.setRemoteAddr("192.168.1.100");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, chain);
        }

        // La 11ème requête devrait être rejetée
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/soumissions/123/valider");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("Trop de requêtes");
    }

    @Test
    @DisplayName("IP différente → buckets séparés")
    void ipDifferente_bucketsSepares() throws Exception {
        // IP 1 — épuiser le bucket sensible
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/soumissions/x/valider");
            req.setRemoteAddr("10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), chain);
        }

        // IP 2 — devrait toujours passer
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/soumissions/x/valider");
        request.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, times(11)).doFilter(any(), any()); // 10 IP1 + 1 IP2
    }

    @Test
    @DisplayName("X-Forwarded-For → utilise l'IP du proxy")
    void xForwardedFor_utiliseIpProxy() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/soumissions");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
