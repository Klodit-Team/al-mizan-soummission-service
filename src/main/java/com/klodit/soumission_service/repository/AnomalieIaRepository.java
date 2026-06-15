package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.AnomalieIa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnomalieIaRepository extends JpaRepository<AnomalieIa, String> {
    List<AnomalieIa> findBySoumissionId(String soumissionId);
    List<AnomalieIa> findBySoumissionIdIn(List<String> soumissionIds);
}
