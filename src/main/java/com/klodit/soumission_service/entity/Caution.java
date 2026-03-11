package com.klodit.soumission_service.entity;

import com.klodit.soumission_service.enums.StatutCaution;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cautions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caution {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soumission_id", nullable = false)
    private Soumission soumission;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private String banque;

    @Column(nullable = false, length = 100)
    private String reference;

    @Column(name = "date_emission", nullable = false)
    private LocalDateTime dateEmission;

    @Column(name = "date_expiration", nullable = false)
    private LocalDateTime dateExpiration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutCaution statut = StatutCaution.VALIDE;

    @Column(name = "fichier_url", nullable = false, length = 500)
    private String fichierUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
