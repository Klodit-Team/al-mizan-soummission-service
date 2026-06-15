package com.klodit.soumission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomaliesParAoResponse {

    private int totalAnomalies;
    private Map<String, Long> breakdown;
    private List<FlaggedBid> flaggedBids;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlaggedBid {
        private String soumissionId;
        private String anomalyType;
        private String detail;
        private Double confidence;
        private LocalDateTime detectedAt;
    }
}
