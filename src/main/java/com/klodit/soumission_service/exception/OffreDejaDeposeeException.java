package com.klodit.soumission_service.exception;

public class OffreDejaDeposeeException extends RuntimeException {
    public OffreDejaDeposeeException(String type) {
        super("Une " + type + " a déjà été déposée pour cette soumission");
    }
}
