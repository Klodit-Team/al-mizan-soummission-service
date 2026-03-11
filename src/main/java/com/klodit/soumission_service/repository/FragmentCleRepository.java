package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.FragmentCle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FragmentCleRepository extends JpaRepository<FragmentCle, String> {
    List<FragmentCle> findByCleChiffrementId(String cleChiffrementId);

    List<FragmentCle> findByCleChiffrementIdAndEstSoumisTrue(String cleChiffrementId);

    long countByCleChiffrementIdAndEstSoumisTrue(String cleChiffrementId);
}
