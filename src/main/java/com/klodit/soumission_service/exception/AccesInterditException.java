package com.klodit.soumission_service.exception;

/**
 * Exception levée quand un utilisateur tente d'accéder à une ressource
 * ou une opération non autorisée par son rôle RBAC.
 */
public class AccesInterditException extends RuntimeException {
    public AccesInterditException(String message) {
        super(message);
    }
}
