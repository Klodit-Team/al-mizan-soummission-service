package com.klodit.soumission_service.dto.request;

import com.klodit.soumission_service.enums.StatutSoumission;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatutSoumissionRequest {
    private StatutSoumission statut;
}
