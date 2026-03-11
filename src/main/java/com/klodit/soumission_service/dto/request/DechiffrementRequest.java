package com.klodit.soumission_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DechiffrementRequest {

    // Note : appelOffreId n'est PAS dans le body (il est dans le @PathVariable)

    /**
     * Fragments de clé soumis par les membres de la commission.
     * Au moins K fragments requis (seuil Shamir configuré = 3 sur 5).
     */
    @NotEmpty(message = "Au moins un fragment de clé est requis")
    private List<FragmentSoumis> fragments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FragmentSoumis {
        @NotNull
        private Integer index;
        @NotBlank
        private String valeur; // fragment en base64
        @NotBlank
        private String membreId; // ID du membre de la commission
    }
}
