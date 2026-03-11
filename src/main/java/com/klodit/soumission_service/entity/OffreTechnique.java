package com.klodit.soumission_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "offres_techniques")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreTechnique {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soumission_id", nullable = false)
    private Soumission soumission;

    @Column(name = "fichier_url", nullable = false, length = 500)
    private String fichierUrl;

    @Column(name = "hash_fichier", nullable = false, length = 128)
    private String hashFichier;

    @Column(name = "is_conforme")
    private Boolean isConforme;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
