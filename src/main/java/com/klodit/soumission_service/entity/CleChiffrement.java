package com.klodit.soumission_service.entity;

import com.klodit.soumission_service.enums.StatutCle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "cles_chiffrement", uniqueConstraints = @UniqueConstraint(columnNames = "appel_offre_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleChiffrement {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "appel_offre_id", nullable = false, length = 36, unique = true)
    private String appelOffreId;

    @Column(name = "cle_publique", nullable = false, columnDefinition = "TEXT")
    private String clePublique;

    @Column(name = "cle_privee_chiffree", columnDefinition = "TEXT")
    private String clePriveeChiffree;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StatutCle statut = StatutCle.ACTIVE;

    @Column(name = "date_generation", nullable = false)
    private LocalDateTime dateGeneration;

    @Column(name = "date_utilisation")
    private LocalDateTime dateUtilisation;

    @PrePersist
    protected void onCreate() {
        dateGeneration = LocalDateTime.now();
    }
}
