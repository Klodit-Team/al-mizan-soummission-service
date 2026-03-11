package com.klodit.soumission_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "fragments_cle", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "cle_chiffrement_id", "fragment_index" }),
        @UniqueConstraint(columnNames = { "cle_chiffrement_id", "membre_commission_id" })
        //les combinaison qui doit être unique pour éviter les doublons
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FragmentCle {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cle_chiffrement_id", nullable = false)
    private CleChiffrement cleChiffrement;

    @Column(name = "membre_commission_id", nullable = false, length = 36)
    private String membreCommissionId;

    @Column(name = "fragment_index", nullable = false)
    private Integer fragmentIndex;

    @Column(name = "fragment_chiffre", nullable = false, columnDefinition = "TEXT")
    private String fragmentChiffre;

    @Column(name = "est_soumis")
    @Builder.Default
    private Boolean estSoumis = false;

    @Column(name = "date_soumission")
    private LocalDateTime dateSoumission;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
