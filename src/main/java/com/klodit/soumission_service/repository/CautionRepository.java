package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.Caution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CautionRepository extends JpaRepository<Caution, String> {
    Optional<Caution> findBySoumissionId(String soumissionId);
}
