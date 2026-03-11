package com.klodit.soumission_service.exception;

import com.klodit.soumission_service.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

        // ── 404 NOT FOUND ─────────────────────────────────────

        @ExceptionHandler(SoumissionNotFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleNotFound(SoumissionNotFoundException ex) {
                log.warn("Ressource introuvable : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(RessourceIntrouvableException.class)
        public ResponseEntity<ApiResponse<Void>> handleRessourceIntrouvable(RessourceIntrouvableException ex) {
                log.warn("Ressource introuvable : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        // ── 403 FORBIDDEN ─────────────────────────────────────

        @ExceptionHandler(AccesRefuseException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccesRefuse(AccesRefuseException ex) {
                log.warn("Accès refusé : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(AccesInterditException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccesInterdit(AccesInterditException ex) {
                log.warn("Accès interdit : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(DelaiDepotExpireException.class)
        public ResponseEntity<ApiResponse<Void>> handleDelaiExpire(DelaiDepotExpireException ex) {
                log.warn("Délai expiré : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        // ── 409 CONFLICT ──────────────────────────────────────

        @ExceptionHandler(OffreDejaDeposeeException.class)
        public ResponseEntity<ApiResponse<Void>> handleDejaDeposee(OffreDejaDeposeeException ex) {
                log.warn("Doublon : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(IllegalStateException.class)
        public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
                log.warn("État invalide : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        // ── 400 BAD REQUEST ───────────────────────────────────

        @ExceptionHandler(FichierInvalideException.class)
        public ResponseEntity<ApiResponse<Void>> handleFichierInvalide(FichierInvalideException ex) {
                log.warn("Requête invalide : {}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
                        MethodArgumentNotValidException ex) {
                Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                                .collect(Collectors.toMap(
                                                FieldError::getField,
                                                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage()
                                                                : "Invalide",
                                                (a, b) -> a));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.<Map<String, String>>builder()
                                                .success(false)
                                                .message("Erreur de validation")
                                                .data(errors)
                                                .build());
        }

        // ── 413 PAYLOAD TOO LARGE ─────────────────────────────

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                                .body(ApiResponse.error("Le fichier dépasse la taille maximale autorisée (50 Mo)"));
        }

        // ── 500 INTERNAL SERVER ERROR (chiffrement) ───────────

        @ExceptionHandler(ChiffrementException.class)
        public ResponseEntity<ApiResponse<Void>> handleChiffrement(ChiffrementException ex) {
                log.error("Erreur de chiffrement : {}", ex.getMessage(), ex);
                // Ne PAS exposer les détails internes crypto au client
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(
                                                "Erreur de chiffrement — consultez les logs serveur pour plus de détails"));
        }

        // ── 500 CATCH-ALL ─────────────────────────────────────

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
                log.error("Erreur inattendue : {}", ex.getMessage(), ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(
                                                "Erreur interne du serveur — consultez les logs pour plus de détails"));
        }
}
