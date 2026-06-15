package com.klodit.soumission_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalerAnomalieRequest {

    @NotBlank(message = "Le type d'anomalie est obligatoire")
    @Size(max = 100, message = "Le type d'anomalie ne peut dépasser 100 caractères")
    private String anomalyType;

    @NotBlank(message = "Le détail de l'anomalie est obligatoire")
    @Size(max = 2000, message = "Le détail ne peut dépasser 2000 caractères")
    private String detail;

    @NotNull(message = "Le score de confiance est obligatoire")
    @DecimalMin(value = "0.0", message = "Le score de confiance doit être >= 0")
    @DecimalMax(value = "1.0", message = "Le score de confiance doit être <= 1")
    private Double confidence;
}
