package com.klodit.soumission_service.exception;

/**
 * Exception levée lorsqu'un utilisateur tente d'accéder à une ressource
 * dont il n'est pas le propriétaire.
 * Mappée sur HTTP 403 FORBIDDEN par le GlobalExceptionHandler.
 */
public class AccesRefuseException extends RuntimeException {
    public AccesRefuseException(String message) {
        super(message);
    }
}
