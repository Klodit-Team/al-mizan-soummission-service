package com.klodit.soumission_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ligne_offres_financieres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneOffreFinanciere {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soumission_id", nullable = false)
    private Soumission soumission;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quantite;

    @Column(nullable = false)
    private String unite;

    @Column(name = "prix_unitaire", precision = 15, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
