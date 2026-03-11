package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.OffreFinanciere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OffreFinanciereRepository extends JpaRepository<OffreFinanciere, String> {
    Optional<OffreFinanciere> findBySoumissionId(String soumissionId);

    /**
     * Pour le déchiffrement en masse lors de l'ouverture des plis.
     * Requête JPQL explicite pour éviter les ambiguïtés du parser multimodule (JPA
     * + Redis).
     */
    @Query("SELECT o FROM OffreFinanciere o JOIN o.soumission s " +
            "WHERE s.appelOffreId = :appelOffreId AND o.isDechiffree = :isDechiffree")
    List<OffreFinanciere> findBySoumissionAppelOffreIdAndIsDechiffree(
            @Param("appelOffreId") String appelOffreId,
            @Param("isDechiffree") Boolean isDechiffree);
}
