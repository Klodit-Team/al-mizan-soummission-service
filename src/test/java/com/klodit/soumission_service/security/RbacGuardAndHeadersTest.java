package com.klodit.soumission_service.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SecurityHeadersFilter — Tests unitaires")
class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    @DisplayName("HSTS header ajouté")
    void hstsHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Strict-Transport-Security"))
                .contains("max-age=31536000");
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("X-Content-Type-Options: nosniff")
    void xContentTypeOptions() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("X-Frame-Options: DENY")
    void xFrameOptions() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    @DisplayName("Content-Security-Policy strict sur les endpoints API")
    void csp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/soumissions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("default-src 'none'");
    }

    @Test
    @DisplayName("Content-Security-Policy permissive sur /swagger-ui")
    void cspSwagger() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("Content-Security-Policy"))
                .contains("script-src 'self' 'unsafe-inline'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    @DisplayName("Cache-Control: no-store")
    void cacheControl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
    }

    @Test
    @DisplayName("Tous les headers OWASP présents")
    void tousLesHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader("X-XSS-Protection")).isNotNull();
        assertThat(response.getHeader("Referrer-Policy")).isNotNull();
        assertThat(response.getHeader("Permissions-Policy")).isNotNull();
    }
}
