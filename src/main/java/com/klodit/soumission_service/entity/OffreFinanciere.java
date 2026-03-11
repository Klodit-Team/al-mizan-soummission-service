package com.klodit.soumission_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "offres_financieres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreFinanciere {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soumission_id", nullable = false)
    private Soumission soumission;

    @Column(name = "fichier_chiffre_url", nullable = false, length = 500)
    private String fichierChiffreUrl;

    /**
     * URL du fichier PDF en clair stocké dans MinIO après déchiffrement.
     * NULL tant que l'offre n'a pas été déchiffrée (ouverture des plis).
     */
    @Column(name = "fichier_clair_url", length = 500)
    private String fichierClairUrl;

    @Column(name = "hash_fichier", nullable = false, length = 128)
    private String hashFichier;

    /**
     * Signature ECDSA P-384 (Base64) — non-répudiation CSL §4.4.5 / Table 4.11
     * étape 3
     */
    @Column(name = "signature_ecdsa", nullable = false, columnDefinition = "TEXT")
    private String signatureEcdsa;

    /**
     * Clé publique ECDSA P-384 PEM de l'OE signataire (pour vérification
     * ultérieure)
     */
    @Column(name = "cle_publique_ecdsa", nullable = false, columnDefinition = "TEXT")
    private String clePubliqueEcdsa;

    @Column(name = "montant_ht", precision = 15, scale = 2)
    private BigDecimal montantHt;

    @Column(precision = 15, scale = 2)
    private BigDecimal tva;

    @Column(name = "montant_ttc", precision = 15, scale = 2)
    private BigDecimal montantTtc;

    @Column(name = "is_dechiffree")
    @Builder.Default
    private Boolean isDechiffree = false;

    @Column(name = "date_dechiffrement")
    private LocalDateTime dateDechiffrement;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
