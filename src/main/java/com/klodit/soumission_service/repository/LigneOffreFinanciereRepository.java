package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.LigneOffreFinanciere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LigneOffreFinanciereRepository extends JpaRepository<LigneOffreFinanciere, String> {
    List<LigneOffreFinanciere> findBySoumissionId(String soumissionId);
    void deleteBySoumissionId(String soumissionId);
}
