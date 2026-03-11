package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.OffreTechnique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OffreTechniqueRepository extends JpaRepository<OffreTechnique, String> {
    Optional<OffreTechnique> findBySoumissionId(String soumissionId);
}
