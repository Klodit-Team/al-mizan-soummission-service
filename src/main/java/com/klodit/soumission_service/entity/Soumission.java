package com.klodit.soumission_service.entity;

import com.klodit.soumission_service.enums.StatutSoumission;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;

@Entity
@Table(name = "soumissions", 
       uniqueConstraints = @UniqueConstraint(columnNames = { "appel_offre_id", "operateur_id",
        "lot_id" })) //les combinaison qui doit être unique pour éviter les doublons
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Soumission {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "appel_offre_id", nullable = false, length = 36)
    private String appelOffreId;

    @Column(name = "operateur_id", nullable = false, length = 36)
    private String operateurId;

    @Column(name = "lot_id", length = 36)
    private String lotId;

    @Column(nullable = false, length = 50, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutSoumission statut = StatutSoumission.BROUILLON;

    @Column(name = "horodatage_serveur")
    private LocalDateTime horodatageServeur;

    @Column(name = "is_electronique")
    @Builder.Default
    private Boolean isElectronique = true;

    @Column(name = "ip_depot", length = 45)
    private String ipDepot;

    @Column(name = "is_dans_delai")
    private Boolean isDansDelai;

    // ── Relations ──────────────────────────────
    @OneToOne(mappedBy = "soumission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OffreTechnique offreTechnique;

    @OneToOne(mappedBy = "soumission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private OffreFinanciere offreFinanciere;

    @OneToOne(mappedBy = "soumission", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Caution caution;

    // ── Timestamps ─────────────────────────────
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
