package com.klodit.soumission_service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de dégradation gracieuse du client REST DocumentsClient.
 *
 * On construit le client avec un port invalide (99999) pour simuler
 * l'indisponibilité du Service Documents. Chaque méthode doit
 * retourner une valeur par défaut sûre, sans lever d'exception.
 */
@DisplayName("DocumentsClient — Dégradation gracieuse")
class DocumentsClientTest {

    private DocumentsClient client;

    @BeforeEach
    void setUp() {
        // Port 99999 → service garanti inaccessible
        client = new DocumentsClient("http://localhost:99999");
    }

    @Test
    @DisplayName("getPiecesAdministratives — service indisponible → Optional.empty()")
    void getPieces_serviceDown_returnsEmpty() {
        Optional<?> result = client.getPiecesAdministratives("soum-inexistant");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("arePiecesAdministrativesValides — service indisponible → true (dégradation gracieuse)")
    void arePiecesValides_serviceDown_returnsTrue() {
        boolean result = client.arePiecesAdministrativesValides("soum-inexistant");
        assertThat(result).isTrue();
    }
}
