-- ═══════════════════════════════════════════════════════════
-- Service Soumissions — Script d'initialisation de la BDD
-- Base : soumission_db
-- ═══════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS soumission_db;
USE soumission_db;

-- ─── Table des clés de chiffrement ────────────────────────
CREATE TABLE IF NOT EXISTS cles_chiffrement (
    id CHAR(36) PRIMARY KEY,
    appel_offre_id CHAR(36) NOT NULL,
    cle_publique TEXT NOT NULL,
    cle_privee_chiffree TEXT,
    statut ENUM('ACTIVE','UTILISEE','REVOQUEE') DEFAULT 'ACTIVE',
    date_generation DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_utilisation DATETIME NULL,
    UNIQUE KEY uk_appel_offre (appel_offre_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Table des soumissions ────────────────────────────────
CREATE TABLE IF NOT EXISTS soumissions (
    id CHAR(36) PRIMARY KEY,
    appel_offre_id CHAR(36) NOT NULL,
    operateur_id CHAR(36) NOT NULL,
    lot_id CHAR(36) NULL,
    reference VARCHAR(50) NOT NULL UNIQUE,
    statut ENUM('BROUILLON','DEPOSEE','RECUE','OUVERTE','EVALUEE','RETENUE','REJETEE')
           DEFAULT 'BROUILLON',
    horodatage_serveur DATETIME NULL,
    is_electronique BOOLEAN DEFAULT TRUE,
    ip_depot VARCHAR(45) NULL,
    is_dans_delai BOOLEAN NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ao_oe_lot (appel_offre_id, operateur_id, lot_id),
    INDEX idx_operateur (operateur_id),
    INDEX idx_appel_offre (appel_offre_id),
    INDEX idx_statut (statut)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Table des offres techniques ──────────────────────────
CREATE TABLE IF NOT EXISTS offres_techniques (
    id CHAR(36) PRIMARY KEY,
    soumission_id CHAR(36) NOT NULL,
    fichier_url VARCHAR(500) NOT NULL,
    hash_fichier VARCHAR(128) NOT NULL,
    is_conforme BOOLEAN NULL,
    observations TEXT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (soumission_id) REFERENCES soumissions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_soumission_ot (soumission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Table des offres financières ─────────────────────────
CREATE TABLE IF NOT EXISTS offres_financieres (
    id CHAR(36) PRIMARY KEY,
    soumission_id CHAR(36) NOT NULL,
    fichier_chiffre_url VARCHAR(500) NOT NULL,
    fichier_clair_url VARCHAR(500) NULL COMMENT 'URL du PDF en clair après déchiffrement (ouverture des plis)',
    hash_fichier VARCHAR(128) NOT NULL,
    signature_ecdsa TEXT NOT NULL COMMENT 'Signature ECDSA P-384 (Base64) — non-répudiation CSL §4.4.5',
    cle_publique_ecdsa TEXT NOT NULL COMMENT 'Clé publique ECDSA P-384 PEM de l''OE signataire',
    montant_ht DECIMAL(15,2) NULL,
    tva DECIMAL(15,2) NULL,
    montant_ttc DECIMAL(15,2) NULL,
    is_dechiffree BOOLEAN DEFAULT FALSE,
    date_dechiffrement DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (soumission_id) REFERENCES soumissions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_soumission_of (soumission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Table des cautions ───────────────────────────────────
CREATE TABLE IF NOT EXISTS cautions (
    id CHAR(36) PRIMARY KEY,
    soumission_id CHAR(36) NOT NULL,
    montant DECIMAL(15,2) NOT NULL,
    banque VARCHAR(255) NOT NULL,
    reference VARCHAR(100) NOT NULL,
    date_emission DATETIME NOT NULL,
    date_expiration DATETIME NOT NULL,
    statut ENUM('VALIDE','EXPIREE','RESTITUEE') DEFAULT 'VALIDE',
    fichier_url VARCHAR(500) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (soumission_id) REFERENCES soumissions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_soumission_caution (soumission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─── Table des fragments de clé (Shamir) ─────────────────
CREATE TABLE IF NOT EXISTS fragments_cle (
    id CHAR(36) PRIMARY KEY,
    cle_chiffrement_id CHAR(36) NOT NULL,
    membre_commission_id CHAR(36) NOT NULL,
    fragment_index INT NOT NULL,
    fragment_chiffre TEXT NOT NULL,
    est_soumis BOOLEAN DEFAULT FALSE,
    date_soumission DATETIME NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (cle_chiffrement_id) REFERENCES cles_chiffrement(id) ON DELETE CASCADE,
    UNIQUE KEY uk_cle_index (cle_chiffrement_id, fragment_index),
    UNIQUE KEY uk_cle_membre (cle_chiffrement_id, membre_commission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════
--  Table d'idempotence pour les consumers RabbitMQ
-- ═══════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS processed_events (
    id CHAR(36) PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    source_queue VARCHAR(100),
    processed_at DATETIME NOT NULL,
    payload_hash VARCHAR(64),
    INDEX idx_event_id (event_id),
    INDEX idx_processed_at (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════
-- Fin du script d'initialisation
-- ═══════════════════════════════════════════════════════════
