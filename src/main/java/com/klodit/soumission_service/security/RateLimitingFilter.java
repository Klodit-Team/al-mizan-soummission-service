package com.klodit.soumission_service.security;

import io.github.bucket4j.*;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtre de rate limiting par adresse IP.
 *
 * Protège contre les abus (brute-force, DoS) au niveau du service.
 * Le rate limiting principal est géré par l'API Gateway, mais
 * ce filtre fournit une protection locale supplémentaire (defense-in-depth).
 *
 * Limites par défaut :
 * - 100 requêtes / minute / IP (général)
 * - 10 requêtes / minute / IP (endpoints sensibles : POST /valider,
 * /dechiffrer)
 *
 * Utilise Bucket4j (token bucket algorithm).
 */
@Component
@Order(0) // Avant SessionValidationFilter (Order=1)
@Slf4j
public class RateLimitingFilter implements Filter {

    /**
     * Cache des buckets par IP. En production avec plusieurs replicas,
     * utiliser Redis Bucket4j (bucket4j-redis) pour un rate limiting distribué.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Endpoints sensibles soumis à un rate limiting plus strict.
     */
    private static final String[] SENSITIVE_PATHS = {
            "/valider", "/dechiffrer", "/cles-chiffrement"
    };

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        String path = httpRequest.getRequestURI();

        // Déterminer la limite applicable
        boolean isSensitive = isSensitivePath(path);
        String bucketKey = clientIp + (isSensitive ? ":sensitive" : ":general");

        Bucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> isSensitive ? createSensitiveBucket() : createGeneralBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1); // Consommer 1 token pour cette requête

        if (probe.isConsumed()) { // Requête autorisée
            httpResponse.setHeader(
                    "X-RateLimit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit dépassé — IP: {}, path: {}, retry après: {}s",
                    clientIp, path,
                    Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());

            httpResponse.setStatus(429); // Too Many Requests
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.setHeader("Retry-After",
                    String.valueOf(Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds()));
            httpResponse.getWriter().write(
                    "{\"success\":false,\"message\":\"Trop de requêtes. Réessayez dans "
                            + Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds()
                            + " secondes.\",\"data\":null}");
        }
    }

    private Bucket createGeneralBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
                .build();
    }

    private Bucket createSensitiveBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                .build();
    }

    private boolean isSensitivePath(String path) {
        for (String sensitive : SENSITIVE_PATHS) {
            if (path.contains(sensitive))
                return true;
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
