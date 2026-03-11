package com.klodit.soumission_service.exception;

import com.klodit.soumission_service.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GlobalExceptionHandler — Tests unitaires")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("SoumissionNotFoundException → 404")
    void handleNotFound() {
        var ex = new SoumissionNotFoundException("Soumission introuvable");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleNotFound(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getMessage()).contains("introuvable");
    }

    @Test
    @DisplayName("RessourceIntrouvableException → 404")
    void handleRessourceIntrouvable() {
        var ex = new RessourceIntrouvableException("Offre introuvable");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleRessourceIntrouvable(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("AccesRefuseException → 403")
    void handleAccesRefuse() {
        var ex = new AccesRefuseException("Non autorisé");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleAccesRefuse(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AccesInterditException → 403")
    void handleAccesInterdit() {
        var ex = new AccesInterditException("Rôle insuffisant");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleAccesInterdit(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("DelaiDepotExpireException → 403")
    void handleDelaiExpire() {
        var ex = new DelaiDepotExpireException("Délai dépassé");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleDelaiExpire(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("OffreDejaDeposeeException → 409")
    void handleDejaDeposee() {
        var ex = new OffreDejaDeposeeException("Offre déjà soumise");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleDejaDeposee(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("IllegalStateException → 409")
    void handleIllegalState() {
        var ex = new IllegalStateException("État invalide");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleIllegalState(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("FichierInvalideException → 400")
    void handleFichierInvalide() {
        var ex = new FichierInvalideException("Format non supporté");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleFichierInvalide(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 avec erreurs de validation")
    void handleValidation() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "nom", "Le nom est obligatoire"));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Map<String, String>>> resp = handler.handleValidation(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getData()).containsKey("nom");
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException → 413")
    void handleMaxUpload() {
        var ex = new MaxUploadSizeExceededException(50_000_000);
        ResponseEntity<ApiResponse<Void>> resp = handler.handleMaxUpload(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("ChiffrementException → 500 sans détails internes")
    void handleChiffrement() {
        var ex = new ChiffrementException("Clé invalide");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleChiffrement(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getMessage()).doesNotContain("Clé invalide");
    }

    @Test
    @DisplayName("Exception générique → 500")
    void handleGeneric() {
        var ex = new RuntimeException("Erreur inconnue");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleGeneric(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
