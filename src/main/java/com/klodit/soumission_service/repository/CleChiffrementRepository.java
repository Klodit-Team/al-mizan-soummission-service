package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.CleChiffrement;
import com.klodit.soumission_service.enums.StatutCle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CleChiffrementRepository extends JpaRepository<CleChiffrement, String> {
    Optional<CleChiffrement> findByAppelOffreId(String appelOffreId);

    Optional<CleChiffrement> findByAppelOffreIdAndStatut(String appelOffreId, StatutCle statut);
}
