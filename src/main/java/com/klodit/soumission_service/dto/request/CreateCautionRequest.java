package com.klodit.soumission_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCautionRequest {

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être positif")
    private BigDecimal montant;

    @NotBlank(message = "Le nom de la banque est obligatoire")
    private String banque;

    @NotBlank(message = "La référence de la caution est obligatoire")
    private String reference;

    @NotNull(message = "La date d'émission est obligatoire")
    private LocalDateTime dateEmission;

    @NotNull(message = "La date d'expiration est obligatoire")
    private LocalDateTime dateExpiration;

    // Fichier scan de la caution : géré via @RequestParam MultipartFile dans le
    // contrôleur
}
