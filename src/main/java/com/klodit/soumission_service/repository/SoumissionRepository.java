package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.Soumission;
import com.klodit.soumission_service.enums.StatutSoumission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoumissionRepository extends JpaRepository<Soumission, String> {

    // Lister les soumissions d'un opérateur économique
    List<Soumission> findByOperateurIdOrderByCreatedAtDesc(String operateurId);

    // Lister les soumissions d'un appel d'offres
    List<Soumission> findByAppelOffreIdOrderByCreatedAtDesc(String appelOffreId);

    // Vérifier unicité : un OE ne soumet qu'une fois par AO+lot
    Optional<Soumission> findByAppelOffreIdAndOperateurIdAndLotId(
            String appelOffreId, String operateurId, String lotId);

    // Chercher par référence
    Optional<Soumission> findByReference(String reference);

    // Lister par statut pour un AO
    List<Soumission> findByAppelOffreIdAndStatut(String appelOffreId, StatutSoumission statut);

    // Compter les soumissions d'un AO
    long countByAppelOffreId(String appelOffreId);
}
