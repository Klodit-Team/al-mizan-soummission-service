package com.klodit.soumission_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSoumissionRequest {

    @NotBlank(message = "L'identifiant de l'appel d'offres est obligatoire")
    private String appelOffreId;

    private String lotId; // Nullable — soumission globale si absent
}
