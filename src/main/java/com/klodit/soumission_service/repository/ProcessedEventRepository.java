package com.klodit.soumission_service.repository;

import com.klodit.soumission_service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Vérifie si un événement a déjà été traité.
     */
    boolean existsByEventId(String eventId);

    /**
     * Purge les événements traités avant une date donnée.
     * Appelé par un @Scheduled pour le nettoyage périodique.
     */
    @Modifying
    @Query("DELETE FROM ProcessedEvent p WHERE p.processedAt < :cutoff")
    int deleteByProcessedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
