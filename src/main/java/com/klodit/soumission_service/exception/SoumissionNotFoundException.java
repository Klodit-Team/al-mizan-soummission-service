package com.klodit.soumission_service.exception;

public class SoumissionNotFoundException extends RuntimeException {
    public SoumissionNotFoundException(String id) {
        super("Soumission introuvable avec l'identifiant : " + id);
    }
}
