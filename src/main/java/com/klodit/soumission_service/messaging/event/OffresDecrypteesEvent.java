package com.klodit.soumission_service.messaging.event;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffresDecrypteesEvent {
    private String appelOffreId;
    private int nombreOffres;
    private List<String> soumissionIds;
    private String declencheParId; // ID du membre de la commission
}
