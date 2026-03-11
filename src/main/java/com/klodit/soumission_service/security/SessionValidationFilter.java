package com.klodit.soumission_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Filtre de validation des sessions via Redis.
 *
 * Flux de sécurité (JWT + Blacklist) :
 * 1. L'API Gateway authentifie l'utilisateur via JWT (access + refresh tokens)
 * 2. Le Service Auth crée une session Redis contenant les deux tokens
 * 3. L'API Gateway ajoute le header X-Session-Id à la requête proxied
 * 4. Ce filtre vérifie :
 * a) Existence de la session dans Redis
 * b) Non-expiration de la session
 * c) Non-blacklist de l'accessToken (BLACKLIST:{accessToken})
 * 5. Si valide : les attributs userId et role sont injectés dans la requête
 * 6. Si invalide : retour 401
 *
 * Blacklist : lors d'un logout ou rotation de token, le Service Auth
 * inscrit l'accessToken dans Redis avec la clé BLACKLIST:{accessToken}.
 * Ce filtre vérifie cette clé pour refuser les tokens révoqués.
 *
 * Fallback (développement) : si X-Session-Id est absent mais
 * X-User-Id/X-User-Role sont présents, le filtre les accepte en mode dev.
 */
@Component
@Order(1)
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "security.filter.enabled", havingValue = "true", matchIfMissing = true)
public class SessionValidationFilter implements Filter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean devFallbackEnabled;

    public SessionValidationFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${security.dev-fallback.enabled:false}") boolean devFallbackEnabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.devFallbackEnabled = devFallbackEnabled;
    }

    /**
     * Endpoints publics exemptés de validation de session.
     * Inclut les chemins Swagger configurés dans application.properties
     * (springdoc.api-docs.path et springdoc.swagger-ui.path).
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/actuator/health",
            "/v3/api-docs",
            "/api/v1/api-docs",
            "/swagger-ui",
            "/api/docs");

    @Override
    public void doFilter(ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 1. Vérifier si le path est public (Swagger, Actuator)
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Essayer la validation par session Redis (production)
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId != null && !sessionId.isBlank()) {
            SessionInfo session = getSessionFromRedis(sessionId);
            if (session == null) {
                log.warn("Session invalide ou expirée — sessionId: {}", sessionId);
                sendUnauthorized(response, "Session invalide ou expirée");
                return;
            }

            // Vérifier l'expiration
            if (session.getExpiresAt() != null
                    && session.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Session expirée — sessionId: {}, expiredAt: {}",
                        sessionId, session.getExpiresAt());
                redisTemplate.delete("SESSION:" + sessionId);
                sendUnauthorized(response, "Session expirée");
                return;
            }

            // Vérifier si l'accessToken est blacklisté (révoqué)
            if (session.getAccessToken() != null
                    && Boolean.TRUE.equals(
                            redisTemplate.hasKey("BLACKLIST:" + session.getAccessToken()))) {
                log.warn("Access token blacklisté (révoqué) — sessionId: {}, userId: {}",
                        sessionId, session.getUserId());
                redisTemplate.delete("SESSION:" + sessionId);
                sendUnauthorized(response, "Token révoqué — veuillez vous reconnecter");
                return;
            }

            // Injecter les attributs dans la requête
            request.setAttribute("userId", session.getUserId());
            request.setAttribute("userRole", session.getRole());

            log.debug("Session validée — userId: {}, role: {}", session.getUserId(), session.getRole());
            chain.doFilter(request, response);
            return;
        }

        // 3. Fallback : headers X-User-Id + X-User-Role (dev uniquement)
        if (devFallbackEnabled) {
            String userId = request.getHeader("X-User-Id");
            String userRole = request.getHeader("X-User-Role");

            if (userId != null && !userId.isBlank()) {
                log.debug("Fallback dev — X-User-Id: {}, X-User-Role: {}", userId, userRole);
                request.setAttribute("userId", userId);
                request.setAttribute("userRole", userRole != null ? userRole : "OPERATEUR_ECONOMIQUE");
                chain.doFilter(request, response);
                return;
            }
        }

        // 4. Aucune authentification fournie
        sendUnauthorized(response, "Authentification requise. Fournir X-Session-Id ou X-User-Id.");
    }

    /**
     * Récupère et désérialise une session depuis Redis.
     *
     * @param sessionId ID de session (UUID)
     * @return SessionInfo ou null si la session n'existe pas
     */
    private SessionInfo getSessionFromRedis(String sessionId) {
        try {
            String key = "SESSION:" + sessionId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, SessionInfo.class);
        } catch (Exception e) {
            log.error("Erreur lecture session Redis : {}", e.getMessage());
            return null;
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"data\":null}");
    }
}
