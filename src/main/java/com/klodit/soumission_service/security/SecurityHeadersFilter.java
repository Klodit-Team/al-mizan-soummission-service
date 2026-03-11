package com.klodit.soumission_service.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtre ajoutant les headers de sécurité HTTP recommandés par OWASP.
 *
 * Headers ajoutés :
 * - Strict-Transport-Security (HSTS) : force HTTPS pendant 1 an
 * - X-Content-Type-Options : empêche le MIME sniffing
 * - X-Frame-Options : empêche le clickjacking
 * - X-XSS-Protection : protection XSS (legacy browsers)
 * - Content-Security-Policy : politique CSP stricte
 * - Referrer-Policy : limite les informations dans le Referer
 * - Permissions-Policy : désactive les API navigateur inutiles
 * - Cache-Control : empêche le cache de données sensibles
 */
@Component
@Order(-1) // Tout premier filtre
public class SecurityHeadersFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                        FilterChain chain) throws IOException, ServletException {

                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                String path = httpRequest.getRequestURI();
                boolean isSwaggerPath = path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");

                // HSTS — force HTTPS pendant 1 an, incluant les sous-domaines
                httpResponse.setHeader("Strict-Transport-Security",
                                "max-age=31536000; includeSubDomains; preload");

                // Empêche le MIME type sniffing
                httpResponse.setHeader("X-Content-Type-Options", "nosniff");

                // Empêche l'embedding dans un iframe (clickjacking)
                httpResponse.setHeader("X-Frame-Options", "DENY");

                // Protection XSS pour les navigateurs legacy
                httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

                // CSP — permissive pour Swagger UI (scripts/styles inline requis), stricte
                // sinon
                if (isSwaggerPath) {
                        httpResponse.setHeader("Content-Security-Policy",
                                        "default-src 'self'; script-src 'self' 'unsafe-inline'; " +
                                                        "style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
                                                        "font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'");
                } else {
                        httpResponse.setHeader("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'");
                }

                // Limite les informations transmises dans le header Referer
                httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // Désactive les API navigateur inutiles pour une API REST
                httpResponse.setHeader("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=()");

                // Empêche le cache de données sensibles
                httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
                httpResponse.setHeader("Pragma", "no-cache");

                chain.doFilter(request, response);
        }
}
