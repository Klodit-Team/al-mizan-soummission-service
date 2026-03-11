package com.klodit.soumission_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * Table de déduplication pour l'idempotence des consumers RabbitMQ.
 *
 * Chaque message entrant possède un identifiant unique (eventId).
 * Avant le traitement, on vérifie si l'eventId existe déjà dans cette table :
 * - Si oui → le message est ignoré (déjà traité)
 * - Si non → on insère l'eventId puis on traite le message
 *
 * La vérification + insertion sont faites dans une même transaction
 * pour éviter les race conditions.
 *
 * Rétention : les entrées de + de 30 jours peuvent être purgées
 * par un job scheduled (nettoyage périodique).
 */
@Entity
@Table(name = "processed_events", uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @UuidGenerator
    private String id;

    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "source_queue", length = 100)
    private String sourceQueue;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash; // SHA-256 du payload (pour audit)
}
