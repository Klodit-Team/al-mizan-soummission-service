package com.klodit.soumission_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCautionRequest {

    @NotBlank(message = "Le compte bancaire est obligatoire")
    private String compteBancaireId;

    @NotBlank(message = "La référence de la caution est obligatoire")
    private String reference;

    @NotNull(message = "La date d'expiration est obligatoire")
    private LocalDateTime dateExpiration;

    // Fichier scan de la caution : géré via @RequestParam MultipartFile dans le
    // contrôleur
}
