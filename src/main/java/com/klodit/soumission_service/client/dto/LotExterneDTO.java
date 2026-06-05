package com.klodit.soumission_service.client.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotExterneDTO {
    private String id;
    private String aoId;
    private String numero;
    private String designation;
    private BigDecimal montantEstime;
    private String statut;
}
