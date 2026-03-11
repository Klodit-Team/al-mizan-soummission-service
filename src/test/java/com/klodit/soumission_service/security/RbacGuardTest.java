package com.klodit.soumission_service.security;

import com.klodit.soumission_service.exception.AccesInterditException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RbacGuard — Tests unitaires")
class RbacGuardTest {

    private final RbacGuard rbacGuard = new RbacGuard();

    @Test
    @DisplayName("Rôle autorisé → pas d'exception")
    void roleAutorise_pasException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userRole", "OPERATEUR_ECONOMIQUE");
        request.setAttribute("userId", "user-001");

        assertThatCode(() -> rbacGuard.requireRole(request, "OPERATEUR_ECONOMIQUE", "ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN bypass → toujours autorisé")
    void admin_toujoursAutorise() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userRole", "ADMIN");
        request.setAttribute("userId", "admin-001");

        assertThatCode(() -> rbacGuard.requireRole(request, "OPERATEUR_ECONOMIQUE"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN bypass avec rôle différent → toujours autorisé")
    void admin_toujoursAutorise2() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userRole", "ADMIN");
        request.setAttribute("userId", "admin-002");

        assertThatCode(() -> rbacGuard.requireRole(request, "MEMBRE_COMMISSION"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rôle non autorisé → AccesInterditException")
    void roleNonAutorise_exception() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userRole", "CONTROLEUR");
        request.setAttribute("userId", "user-002");

        assertThatThrownBy(() -> rbacGuard.requireRole(request, "OPERATEUR_ECONOMIQUE", "SERVICE_CONTRACTANT"))
                .isInstanceOf(AccesInterditException.class)
                .hasMessageContaining("CONTROLEUR");
    }

    @Test
    @DisplayName("Rôle null → AccesInterditException")
    void roleNull_exception() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // userRole not set

        assertThatThrownBy(() -> rbacGuard.requireRole(request, "ADMIN"))
                .isInstanceOf(AccesInterditException.class)
                .hasMessageContaining("non défini");
    }

    @Test
    @DisplayName("getUserId → retourne l'ID")
    void getUserId_retourneId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", "user-123");

        assertThat(rbacGuard.getUserId(request)).isEqualTo("user-123");
    }

    @Test
    @DisplayName("getUserId null → AccesInterditException")
    void getUserId_null_exception() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> rbacGuard.getUserId(request))
                .isInstanceOf(AccesInterditException.class);
    }

    @Test
    @DisplayName("getUserRole → retourne le rôle")
    void getUserRole_retourneRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userRole", "MEMBRE_COMMISSION");

        assertThat(rbacGuard.getUserRole(request)).isEqualTo("MEMBRE_COMMISSION");
    }

    @Test
    @DisplayName("getUserRole null → retourne UNKNOWN")
    void getUserRole_null_retourneUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(rbacGuard.getUserRole(request)).isEqualTo("UNKNOWN");
    }
}
