package com.klodit.soumission_service.enums;

public enum StatutCle {
    ACTIVE, // Clé prête à être utilisée pour le chiffrement
    UTILISEE, // Clé utilisée pour le déchiffrement (irrécupérable)
    REVOQUEE // Clé révoquée (bloquee pour tout usage futur) 
}
