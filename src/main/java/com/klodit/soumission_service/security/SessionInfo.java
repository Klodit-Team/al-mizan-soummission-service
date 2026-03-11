package com.klodit.soumission_service.security;

import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Représentation d'une session stockée dans Redis par le Service Auth.
 *
 * Architecture JWT du Service Auth :
 * - Authentification via JWT avec accessToken (courte durée ~15-30 min)
 * et refreshToken (longue durée ~24h)
 * - Les deux tokens sont stockés dans l'objet session Redis
 * - Clé Redis : SESSION:{sessionId}
 * - Blacklist des tokens révoqués : BLACKLIST:{accessToken}
 *
 * Correspond à la table Sessions du Service Auth :
 * - id (char 36) → clé Redis SESSION:{id}
 * - userId (char 36)
 * - accessToken (varchar 512) — JWT courte durée
 * - refreshToken (varchar 512) — JWT longue durée
 * - ipAddress (varchar 45)
 * - userAgent (varchar 512)
 * - expiresAt (datetime)
 * - createdAt (datetime)
 *
 * Le champ "role" est résolu côté Gateway à partir du userId.
 * Le sessionId est transmis dans le header "X-Session-Id" par l'API Gateway.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionInfo implements Serializable {
    private String userId;
    private String role; // OPERATEUR_ECONOMIQUE, ADMIN, etc.
    private String email;
    private String nif; // Pour les opérateurs économiques
    private String accessToken; // JWT access token (courte durée ~15-30 min)
    private String refreshToken; // JWT refresh token (longue durée ~24h)
    private String ipAddress; // IP du client (varchar 45)
    private String userAgent; // User-Agent du navigateur (varchar 512)
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
