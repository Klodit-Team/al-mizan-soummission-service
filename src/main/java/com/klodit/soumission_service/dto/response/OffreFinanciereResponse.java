package com.klodit.soumission_service.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreFinanciereResponse {
    private String id;
    private String fichierChiffreUrl;
    private String fichierClairUrl;
    private String hashFichier;
    private Boolean signatureVerifiee;
    private BigDecimal montantHt;
    private BigDecimal tva;
    private BigDecimal montantTtc;
    private Boolean isDechiffree;
    private LocalDateTime dateDechiffrement;
    private LocalDateTime createdAt;
}
