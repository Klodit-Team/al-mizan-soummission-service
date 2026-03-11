package com.klodit.soumission_service.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreTechniqueResponse {
    private String id;
    private String fichierUrl;
    private String hashFichier;
    private Boolean isConforme;
    private String observations;
    private LocalDateTime createdAt;
}
