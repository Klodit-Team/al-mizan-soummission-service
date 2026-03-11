package com.klodit.soumission_service.exception;

/**
 * Exception générique pour toute ressource introuvable (offre technique,
 * offre financière, caution, clé de chiffrement, etc.).
 * Mappée sur HTTP 404 NOT FOUND par le GlobalExceptionHandler.
 */
public class RessourceIntrouvableException extends RuntimeException {
    public RessourceIntrouvableException(String typeRessource, String identifiant) {
        super(typeRessource + " introuvable : " + identifiant);
    }

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
