package com.klodit.soumission_service.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneOffreFinanciereResponse {
    private String id;
    private String designation;
    private BigDecimal quantite;
    private String unite;
    private BigDecimal prixUnitaire;
}
