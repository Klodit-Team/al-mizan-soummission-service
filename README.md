# al-mizan-soummission-service

> **Service de Gestion des Soumissions** — Dépôt d'offres (techniques & financières) chiffrées via Shamir Secret Sharing, gestion du processus d'ouverture des plis et d'évaluation, pour la plateforme Al-Mizan.

---

## Table des matières

1. [Aperçu](#aperçu)
2. [Technologies](#technologies)
3. [Architecture & Réseau](#architecture--réseau)
4. [Variables d'environnement](#variables-denvironnement)
5. [API REST](#api-rest)
6. [Messagerie RabbitMQ](#messagerie-rabbitmq)
7. [Chiffrement Shamir Secret Sharing](#chiffrement-shamir-secret-sharing)
8. [Commandes utiles](#commandes-utiles)
9. [Docker](#docker)

---

## Aperçu

`al-mizan-soummission-service` est le service de gestion des offres soumises par les opérateurs économiques dans le cadre d'un appel d'offres. Il est le **seul service Java Spring Boot** de la plateforme Al-Mizan.

Fonctionnalités principales :

- **Dépôt d'offres** : upload des offres techniques et financières (max 50 MB) stockées chiffrées sur MinIO.
- **Chiffrement avancé** : RSA-4096 + AES-256 + **Shamir Secret Sharing** (K=3, N=5 sur GF(256)) — la clé de déchiffrement est divisée en 5 parts, 3 minimum sont nécessaires pour reconstituer l'offre.
- **Processus d'ouverture des plis** : contrôle d'accès temporel (ouverture uniquement à la date limite de soumission).
- **Pièces administratives** : validation des pièces requises (NIF, NIS, RC, bilans...) avant acceptation.
- **Sessions Redis** : authentification via sessions partagées avec l'API Gateway.
- **Rate Limiting** via Bucket4j.
- **Monitoring** : Spring Boot Actuator + Prometheus/Micrometer.
- **Tests de charge** : Gatling.

---

## Technologies

| Technologie              | Version     | Rôle                                             |
|--------------------------|-------------|--------------------------------------------------|
| Java                     | 21          | Runtime                                          |
| Spring Boot              | 4.0.3       | Framework (MVC, Security, AMQP, Data JPA)        |
| Spring Security          | (inclus SB) | Authentification via sessions Redis              |
| Spring Data JPA / Hibernate | (inclus SB) | ORM MySQL                                     |
| MySQL                    | 8.x         | Base de données principale (`soumission_db`)     |
| Redis                    | (Spring Data)| Cache sessions                                  |
| RabbitMQ (Spring AMQP)   | (inclus SB) | Messagerie asynchrone                            |
| MinIO (SDK Java)         | 8.5.7       | Stockage offres chiffrées (S3-compatible)        |
| BouncyCastle             | 1.78        | Cryptographie RSA-4096 + AES-256                 |
| Shamir (codahale)        | 0.7.0       | Shamir Secret Sharing K=3/N=5 sur GF(256)        |
| Lombok                   | (inclus SB) | Réduction boilerplate Java                       |
| Springdoc OpenAPI        | 3.0.1       | Documentation Swagger UI                         |
| Bucket4j                 | 8.10.1      | Rate Limiting                                    |
| Micrometer + Prometheus  | (inclus SB) | Métriques applicatives                           |
| JaCoCo                   | 0.8.12      | Couverture de code (seuil : 80% instructions)    |
| Gatling                  | 3.11.5      | Tests de charge                                  |
| Testcontainers           | 1.20.4      | Tests d'intégration (MySQL, RabbitMQ)            |

---

## Architecture & Réseau

```
API Gateway (:3000) ──► soumission-service (:8004)
                                │
                                ├── MySQL    (mysql:3306 → soumission_db)
                                ├── Redis    (redis:6379)   [Sessions]
                                ├── MinIO    (minio:9000)   [Offres chiffrées]
                                ├── RabbitMQ (rabbitmq:5672)
                                │
                                ├──[REST]──► appel-offres-service (:8003)
                                ├──[REST]──► users-service (:3002)
                                └──[REST]──► documents-service (:8005)
```

- **Port exposé** : `8004`
- **Réseau Docker** : `al-mizan-network`
- **Nom du conteneur** : `soumission-service`
- **Swagger UI** : `http://localhost:8004/swagger-ui.html`
- **Actuator/Health** : `http://localhost:8004/actuator/health`
- **Prometheus metrics** : `http://localhost:8004/actuator/prometheus`

---

## Variables d'environnement

| Variable                | Défaut                         | Description                                   |
|-------------------------|--------------------------------|-----------------------------------------------|
| `DB_HOST`               | `localhost`                    | Hôte MySQL                                    |
| `DB_PORT`               | `3306`                         | Port MySQL                                    |
| `DB_USERNAME`           | `root`                         | Utilisateur MySQL                             |
| `DB_PASSWORD`           | (vide)                         | Mot de passe MySQL                            |
| `REDIS_HOST`            | `localhost`                    | Hôte Redis                                    |
| `REDIS_PORT`            | `6379`                         | Port Redis                                    |
| `RABBITMQ_HOST`         | `localhost`                    | Hôte RabbitMQ                                 |
| `RABBITMQ_PORT`         | `5672`                         | Port RabbitMQ                                 |
| `RABBITMQ_USERNAME`     | `guest`                        | Utilisateur RabbitMQ                          |
| `RABBITMQ_PASSWORD`     | `guest`                        | Mot de passe RabbitMQ                         |
| `MINIO_ENDPOINT`        | `http://localhost:9000`        | URL MinIO                                     |
| `MINIO_ACCESS_KEY`      | `minioadmin`                   | Clé d'accès MinIO                             |
| `MINIO_SECRET_KEY`      | `minioadmin`                   | Clé secrète MinIO                             |
| `SHAMIR_TOTAL_SHARES`   | `5`                            | Nombre total de parts Shamir (N)              |
| `SHAMIR_THRESHOLD`      | `3`                            | Seuil minimum de parts Shamir (K)             |
| `AO_SERVICE_URL`        | `http://localhost:8003`        | URL appel-offres-service                      |
| `USER_SERVICE_URL`      | `http://localhost:3002`        | URL users-service                             |
| `DOCUMENT_SERVICE_URL`  | `http://localhost:8005`        | URL documents-service                         |

> ⚠️ En production (Docker), remplacer `localhost` par les noms de conteneurs.

> ⚠️ `security.dev-fallback.enabled=true` dans `application.properties` permet de bypasser l'authentification en dev via `X-User-Id` / `X-User-Role`. **NE JAMAIS activer en production.**

---

## API REST

Base URL (via Gateway) : `http://localhost:3000/soumissions`  
Base URL (directe) : `http://localhost:8004`  
Swagger : `http://localhost:8004/swagger-ui.html`

### Soumissions

| Méthode  | Endpoint                                     | Auth | Description                                     |
|----------|----------------------------------------------|------|-------------------------------------------------|
| `POST`   | `/api/v1/soumissions`                        | Oui  | Créer une soumission pour un AO                 |
| `GET`    | `/api/v1/soumissions/:id`                    | Oui  | Détail d'une soumission                         |
| `GET`    | `/api/v1/soumissions?aoId={id}`              | Oui  | Lister les soumissions d'un AO                  |
| `PATCH`  | `/api/v1/soumissions/:id`                    | Oui  | Modifier une soumission en cours                |
| `DELETE` | `/api/v1/soumissions/:id`                    | Oui  | Retirer une soumission (avant date limite)      |

### Offres (upload chiffré)

| Méthode  | Endpoint                                           | Auth | Description                           |
|----------|----------------------------------------------------|------|---------------------------------------|
| `POST`   | `/api/v1/soumissions/:id/offre-technique`          | Oui  | Upload offre technique (chiffrée)     |
| `POST`   | `/api/v1/soumissions/:id/offre-financiere`         | Oui  | Upload offre financière (chiffrée)    |
| `POST`   | `/api/v1/soumissions/:id/caution`                  | Oui  | Upload caution bancaire               |

### Ouverture des plis & Évaluation

| Méthode  | Endpoint                                      | Auth | Description                                |
|----------|-----------------------------------------------|------|--------------------------------------------|
| `POST`   | `/api/v1/soumissions/ouverture-plis`          | Oui  | Déclencher l'ouverture des plis (SC)       |
| `POST`   | `/api/v1/soumissions/:id/dechiffrer`          | Oui  | Déchiffrer une offre (K parts Shamir)      |
| `POST`   | `/api/v1/soumissions/:id/evaluer`             | Oui  | Soumettre une évaluation                   |

---

## Messagerie RabbitMQ

**Exchange** : `al-mizan.events` (type: `topic`, durable: `true`)

### Événements publiés

| Routing Key                    | Déclencheur                          | Consommateurs                   |
|--------------------------------|--------------------------------------|---------------------------------|
| `soumission.deposee`           | Soumission déposée avec succès       | notification-service, audit     |
| `soumission.retiree`           | Soumission retirée par l'OE          | notification-service, audit     |
| `soumission.evaluee`           | Note d'évaluation soumise            | evaluation-service, audit       |
| `plis.ouverts`                 | Ouverture officielle des plis        | notification-service, audit     |

### Événements consommés

| Routing Key          | Source              | Action réalisée                          |
|----------------------|---------------------|------------------------------------------|
| `ao.published`       | appel-offres-service| Activation du dépôt pour l'AO publié    |
| `ao.status_changed`  | appel-offres-service| Mise à jour du statut dans la soumission |

---

## Chiffrement Shamir Secret Sharing

Le `soumission-service` implémente un chiffrement à plusieurs niveaux pour les offres financières :

1. **Chiffrement AES-256** : l'offre financière est chiffrée avec une clé AES-256 aléatoire.
2. **Chiffrement RSA-4096** : la clé AES est chiffrée avec la clé publique RSA de la plateforme.
3. **Shamir Secret Sharing (K=3, N=5) sur GF(256)** : la clé RSA privée est divisée en **5 parts**, dont **3 minimum** sont nécessaires pour reconstituer la clé et déchiffrer l'offre.

Cette architecture garantit qu'**aucune entité seule** ne peut accéder aux offres avant l'ouverture officielle des plis, conformément aux exigences légales de la loi 23-12.

---

## Commandes utiles

### Développement local

```bash
# Compiler (Maven Wrapper)
./mvnw clean compile

# Lancer en dev (Spring DevTools hot-reload)
./mvnw spring-boot:run

# Construire le JAR
./mvnw clean package -DskipTests

# Lancer le JAR
java -jar target/soumission_service-0.0.1-SNAPSHOT.jar
```

### Tests

```bash
# Tests unitaires + intégration
./mvnw test

# Tests avec couverture JaCoCo (seuil 80%)
./mvnw verify

# Tests de charge Gatling
./mvnw gatling:test
```

---

## Docker

### Build de l'image

```bash
docker build -t al-mizan-soumission-service .
```

### Notes importantes sur le Dockerfile

- Image de base : multi-stage avec `eclipse-temurin:21-jdk-alpine` (build) et `eclipse-temurin:21-jre-alpine` (runtime).
- Le JAR Spring Boot est self-contained (pas besoin d'application server externe).
- La base de données est migrée via Flyway/Hibernate au démarrage.

### Déploiement via docker-compose

```bash
docker-compose up -d soumission-service
docker-compose logs -f soumission-service
```

---

*Maintenu par l'équipe Al-Mizan — voir `al-mizan-deployments` pour la configuration de déploiement complète.*
