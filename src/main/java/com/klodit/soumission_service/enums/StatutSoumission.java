package com.klodit.soumission_service.enums;

public enum StatutSoumission {
    BROUILLON, // Soumission en cours de préparation
    DEPOSEE, // Soumission validée et déposée (horodatée)
    RECUE, // Accusé de réception généré
    OUVERTE, // Offres ouvertes par la commission
    EVALUEE, // Évaluation technique terminée
    RETENUE, // Soumission retenue (adjudication)
    REJETEE // Soumission rejetée
}
