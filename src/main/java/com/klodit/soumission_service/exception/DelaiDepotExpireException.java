package com.klodit.soumission_service.exception;

public class DelaiDepotExpireException extends RuntimeException {
    public DelaiDepotExpireException(String appelOffreId) {
        super("Le délai de dépôt est expiré pour l'appel d'offres : " + appelOffreId);
    }
}
