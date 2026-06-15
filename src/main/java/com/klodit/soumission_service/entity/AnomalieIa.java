package com.klodit.soumission_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "anomalies_ia", indexes = @Index(name = "idx_anomalie_soumission", columnList = "soumission_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalieIa {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "soumission_id", nullable = false, length = 36)
    private String soumissionId;

    @Column(name = "anomaly_type", nullable = false, length = 100)
    private String anomalyType;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "detected_at", nullable = false)
    @Builder.Default
    private LocalDateTime detectedAt = LocalDateTime.now();
}
