package com.klodit.soumission_service.dto.response;

import com.klodit.soumission_service.enums.StatutCaution;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CautionResponse {
    private String id;
    private String compteBancaireId;
    private String reference;
    private LocalDateTime dateExpiration;
    private StatutCaution statut;
    private String fichierUrl;
    private LocalDateTime createdAt;
}
