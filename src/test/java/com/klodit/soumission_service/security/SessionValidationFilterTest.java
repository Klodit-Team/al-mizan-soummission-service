package com.klodit.soumission_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionValidationFilter — Tests unitaires")
class SessionValidationFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private FilterChain filterChain;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SessionValidationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new SessionValidationFilter(redisTemplate, objectMapper, true);
    }

    @Test
    @DisplayName("Path public (Actuator Health) — bypass sans authentification")
    void publicPath_bypassed() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("Aucune authentification → 401 Unauthorized")
    void noAuth_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/soumissions");
        when(request.getHeader("X-Session-Id")).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn(null);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Fallback dev — X-User-Id présent → chaîne poursuivie")
    void fallbackDev_withXUserId() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/soumissions");
        when(request.getHeader("X-Session-Id")).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn("user-dev-1");
        when(request.getHeader("X-User-Role")).thenReturn("OPERATEUR_ECONOMIQUE");

        filter.doFilter(request, response, filterChain);

        verify(request).setAttribute("userId", "user-dev-1");
        verify(request).setAttribute("userRole", "OPERATEUR_ECONOMIQUE");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Session Redis valide → attributs injectés, chaîne poursuivie")
    void redisSession_valid() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/soumissions");
        when(request.getHeader("X-Session-Id")).thenReturn("session-abc-123");

        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setUserId("user-redis-1");
        sessionInfo.setRole("OPERATEUR_ECONOMIQUE");
        sessionInfo.setAccessToken("jwt-access-valid");
        sessionInfo.setExpiresAt(LocalDateTime.now().plusHours(1));

        String sessionJson = objectMapper.writeValueAsString(sessionInfo);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("SESSION:session-abc-123")).thenReturn(sessionJson);
        when(redisTemplate.hasKey("BLACKLIST:jwt-access-valid")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(request).setAttribute("userId", "user-redis-1");
        verify(request).setAttribute("userRole", "OPERATEUR_ECONOMIQUE");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Session Redis avec accessToken blacklisté → 401 + suppression session")
    void redisSession_blacklisted_returns401() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/soumissions");
        when(request.getHeader("X-Session-Id")).thenReturn("session-blacklisted-1");

        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setUserId("user-blacklisted");
        sessionInfo.setRole("OPERATEUR_ECONOMIQUE");
        sessionInfo.setAccessToken("jwt-access-revoked");
        sessionInfo.setExpiresAt(LocalDateTime.now().plusHours(1));

        String sessionJson = objectMapper.writeValueAsString(sessionInfo);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("SESSION:session-blacklisted-1")).thenReturn(sessionJson);
        when(redisTemplate.hasKey("BLACKLIST:jwt-access-revoked")).thenReturn(true);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(401);
        verify(redisTemplate).delete("SESSION:session-blacklisted-1");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Session Redis expirée → 401 + suppression session")
    void redisSession_expired() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/soumissions");
        when(request.getHeader("X-Session-Id")).thenReturn("session-expired-1");

        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setUserId("user-expired");
        sessionInfo.setRole("OPERATEUR_ECONOMIQUE");
        sessionInfo.setExpiresAt(LocalDateTime.now().minusHours(1)); // Expirée !

        String sessionJson = objectMapper.writeValueAsString(sessionInfo);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("SESSION:session-expired-1")).thenReturn(sessionJson);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(401);
        verify(redisTemplate).delete("SESSION:session-expired-1");
        verify(filterChain, never()).doFilter(request, response);
    }
}
