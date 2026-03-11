package com.klodit.soumission_service.security;

import com.klodit.soumission_service.exception.AccesInterditException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Garde RBAC — vérifie que l'utilisateur a le rôle requis.
 *
 * Rôles de l'application :
 * 1. ADMIN
 * 2. SERVICE_CONTRACTANT
 * 3. OPERATEUR_ECONOMIQUE
 * 4. MEMBRE_COMMISSION
 * 5. CONTROLEUR
 *
 * Utilisation dans les controllers :
 * rbacGuard.requireRole(request, "OPERATEUR_ECONOMIQUE", "ADMIN");
 */
@Component
@Slf4j
public class RbacGuard {

    /**
     * Vérifie que le rôle de l'utilisateur authentifié fait partie des rôles
     * autorisés.
     *
     * @param request     HttpServletRequest (contient l'attribut "userRole" injecté
     *                    par le filtre)
     * @param rolesPermis rôles autorisés pour cette opération
     * @throws AccesInterditException si le rôle n'est pas autorisé
     */
    public void requireRole(HttpServletRequest request, String... rolesPermis) {
        String userRole = (String) request.getAttribute("userRole");
        String userId = (String) request.getAttribute("userId");

        if (userRole == null) {
            throw new AccesInterditException("Rôle utilisateur non défini dans la session");
        }

        Set<String> allowed = Set.of(rolesPermis);

        // ADMIN a toujours accès (supervision globale)
        if ("ADMIN".equalsIgnoreCase(userRole)) {
            return;
        }

        if (!allowed.contains(userRole.toUpperCase())) {
            log.warn("Accès refusé — userId: {}, rôle: {}, rôles requis: {}",
                    userId, userRole, allowed);
            throw new AccesInterditException(
                    "Accès refusé. Votre rôle (" + userRole + ") ne permet pas cette opération. "
                            + "Rôles autorisés : " + allowed);
        }
    }

    /**
     * Extrait l'ID utilisateur de la requête.
     */
    public String getUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null || userId.isBlank()) {
            throw new AccesInterditException("ID utilisateur non défini dans la session");
        }
        return userId;
    }

    /**
     * Extrait le rôle de la requête.
     */
    public String getUserRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("userRole");
        return role != null ? role : "UNKNOWN";
    }
}
