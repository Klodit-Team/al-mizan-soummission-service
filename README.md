# soumission-service

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen?logo=springboot)
![Tests](https://img.shields.io/badge/tests-193%20passing-success?logo=junit5)
![Coverage](https://img.shields.io/badge/coverage-≥80%25-brightgreen?logo=jacoco)
![License](https://img.shields.io/badge/license-Proprietary-red)

Microservice de gestion des soumissions aux appels d'offres publics — Plateforme **Al-Mizan** (Klodit).

Il couvre le cycle de vie complet d'une soumission : création du dossier, dépôt des pièces chiffrées, ouverture des plis par la commission et déchiffrement ECDSA/RSA-4096.

---

## Sommaire

- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Démarrage rapide (Docker Compose)](#démarrage-rapide)
- [Configuration](#configuration)
- [API REST](#api-rest)
- [Sécurité](#sécurité)
- [Messagerie RabbitMQ](#messagerie-rabbitmq)
- [Cryptographie](#cryptographie)
- [Tests](#tests)
- [Kubernetes](#kubernetes)
- [Monitoring](#monitoring)
- [Licence](#licence)

---

## Architecture

```
soumission-service  (port 8004)
│
├── MySQL 8.0          → base soumission_db (schéma exclusif)
├── Redis 7            → validation de sessions inter-services
├── RabbitMQ 3.x       → événements asynchrones (publish/consume)
└── MinIO              → stockage S3 des fichiers d'offres
```

Le service fait partie d'une architecture microservices et communique avec :

| Service                 | Rôle                                                | Protocole  |
| ----------------------- | --------------------------------------------------- | ---------- |
| **auth-service**        | Validation des sessions Redis                       | Redis      |
| **utilisateur-service** | Vérification d'éligibilité des opérateurs           | REST/Feign |
| **appel-offre-service** | Récupération des données AO                         | REST/Feign |
| **documents-service**   | Vérification des pièces administratives (port 8005) | REST/Feign |

---

## Prérequis

| Outil                   | Version       |
| ----------------------- | ------------- |
| Java                    | 21            |
| Maven                   | 3.9+          |
| Docker & Docker Compose | 24+           |
| MySQL                   | 8.0           |
| Redis                   | 7             |
| RabbitMQ                | 3.x           |
| MinIO                   | RELEASE.2024+ |

---

## Démarrage rapide

### Étape 1 — Se placer dans le répertoire du projet

```powershell
cd c:\Users\Bureau\Desktop\soumission_service
```

### Étape 2 — Lancer tous les services d'infrastructure

```powershell
docker compose up -d
```

Cette commande démarre **7 conteneurs** :

| Conteneur               | Rôle                                                                                                    |
| ----------------------- | ------------------------------------------------------------------------------------------------------- |
| `soumission-mysql`      | Base de données MySQL 8.0 — crée automatiquement `soumission_db` et les 7 tables via `init.sql`         |
| `soumission-redis`      | Stockage de sessions Redis                                                                              |
| `soumission-rabbitmq`   | Broker de messages RabbitMQ avec UI de management                                                       |
| `soumission-minio`      | Stockage objet S3-compatible                                                                            |
| `soumission-minio-init` | Conteneur éphémère qui crée les 3 buckets MinIO (`offres-techniques`, `offres-financieres`, `cautions`) |
| `prometheus`            | Collecte des métriques applicatives                                                                     |
| `grafana`               | Visualisation des tableaux de bord                                                                      |

### Étape 3 — Attendre que tout soit prêt

```powershell
# Vérifier l'état de tous les conteneurs
docker compose ps
```

**Résultat attendu** : Tous les conteneurs en état `running` (sauf `minio-init` qui sera `exited (0)` après avoir créé les buckets — c'est normal).

Attendre ~30 secondes pour que les healthchecks passent, surtout MySQL.

```powershell
# Vérifier les healthchecks
docker inspect --format='{{.Name}} → {{.State.Health.Status}}' soumission-mysql soumission-redis soumission-rabbitmq soumission-minio
```

Tous doivent afficher `healthy`.

### Étape 4 — Vérification de l'infrastructure

**MySQL** — vérifier que les 7 tables existent :

```powershell
docker exec soumission-mysql mysql -uroot -proot -e "USE soumission_db; SHOW TABLES;"
```

Résultat attendu : `cles_chiffrement`, `soumissions`, `offres_techniques`, `offres_financieres`, `cautions`, `fragments_cle`, `processed_events`

**Redis** :

```powershell
docker exec soumission-redis redis-cli ping
# → PONG
```

**RabbitMQ** : [http://localhost:15672](http://localhost:15672) — login `guest` / `guest`

**MinIO** : [http://localhost:9001](http://localhost:9001) — login `minioadmin` / `minioadmin` — vérifier les 3 buckets

**Prometheus** : [http://localhost:9090](http://localhost:9090)

**Grafana** : [http://localhost:3001](http://localhost:3001) — login `admin` / `admin`

### Étape 5 — Lancer l'application Spring Boot

**Option A — Mode développement (recommandé)**

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Le profil `dev` active : `ddl-auto=update`, SQL visible dans les logs, Swagger UI activé, filtre de sécurité désactivé (pas besoin de session Redis).

**Option B — Build + JAR**

```powershell
.\mvnw.cmd clean package "-DskipTests"
java -jar target\soumission_service-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

**Option C — Docker (image complète)**

```powershell
docker build -t soumission-service:latest .

docker run -d --name soumission-app ^
  --network soumission_service_default ^
  -p 8004:8004 ^
  -e DB_HOST=soumission-mysql ^
  -e DB_PORT=3306 ^
  -e REDIS_HOST=soumission-redis ^
  -e RABBITMQ_HOST=soumission-rabbitmq ^
  -e MINIO_ENDPOINT=http://soumission-minio:9000 ^
  -e SPRING_PROFILES_ACTIVE=dev ^
  soumission-service:latest
```

Le service met ~15-30 secondes à démarrer. Indicateur de succès dans les logs :

```
Started SoumissionServiceApplication in X.XXX seconds
```

### Étape 6 — Vérification du service

```powershell
curl http://localhost:8004/actuator/health
# → {"status":"UP"}
```

**Swagger UI** : [http://localhost:8004/swagger-ui/index.html](http://localhost:8004/swagger-ui/index.html)

**Métriques Prometheus** :

```powershell
curl http://localhost:8004/actuator/prometheus
```

---

## Configuration

Toutes les propriétés sont externalisées via variables d'environnement.

### Variables d'environnement

| Variable            | Défaut                  | Description           |
| ------------------- | ----------------------- | --------------------- |
| `DB_HOST`           | `localhost`             | Hôte MySQL            |
| `DB_PORT`           | `3307`                  | Port MySQL            |
| `DB_USERNAME`       | `root`                  | Utilisateur MySQL     |
| `DB_PASSWORD`       | `root`                  | Mot de passe MySQL    |
| `REDIS_HOST`        | `localhost`             | Hôte Redis            |
| `REDIS_PORT`        | `6379`                  | Port Redis            |
| `REDIS_PASSWORD`    | _(vide)_                | Mot de passe Redis    |
| `RABBITMQ_HOST`     | `localhost`             | Hôte RabbitMQ         |
| `RABBITMQ_PORT`     | `5672`                  | Port AMQP             |
| `RABBITMQ_USERNAME` | `guest`                 | Utilisateur RabbitMQ  |
| `RABBITMQ_PASSWORD` | `guest`                 | Mot de passe RabbitMQ |
| `MINIO_ENDPOINT`    | `http://localhost:9000` | URL MinIO             |
| `MINIO_ACCESS_KEY`  | `minioadmin`            | Clé d'accès MinIO     |
| `MINIO_SECRET_KEY`  | `minioadmin`            | Clé secrète MinIO     |

### Profils Spring

| Profil     | Usage                                                |
| ---------- | ---------------------------------------------------- |
| _(défaut)_ | Développement local                                  |
| `dev`      | Filtre de session désactivé, fallback headers activé |
| `prod`     | Tous les filtres actifs, logs JSON structurés        |
| `mtls`     | mTLS activé (certificats clients obligatoires)       |

> **Important** : `security.dev-fallback.enabled` est `true` uniquement en dev/test. Ne jamais l'activer en production.

---

## API REST

Base URL : `http://localhost:8004/api/v1`

Documentation interactive : `http://localhost:8004/swagger-ui/index.html`

### Soumissions — `/soumissions`

| Méthode | Endpoint              | Rôle requis                         | Description                      |
| ------- | --------------------- | ----------------------------------- | -------------------------------- |
| `POST`  | `/`                   | `OPERATEUR_ECONOMIQUE`              | Créer une soumission (brouillon) |
| `GET`   | `/`                   | `OPERATEUR_ECONOMIQUE`              | Lister mes soumissions           |
| `GET`   | `/{id}`               | `OE, COMMISSION, ADMIN, CONTROLEUR` | Détail d'une soumission          |
| `GET`   | `/appel-offre/{aoId}` | `COMMISSION, ADMIN, CONTROLEUR`     | Lister par AO                    |
| `POST`  | `/{id}/valider`       | `OPERATEUR_ECONOMIQUE`              | Valider et déposer               |
| `PATCH` | `/{id}/statut`        | `ADMIN, MEMBRE_COMMISSION`          | Changer le statut                |

### Offre Technique — `/offres-techniques`

| Méthode | Endpoint          | Rôle requis                         | Description                     |
| ------- | ----------------- | ----------------------------------- | ------------------------------- |
| `POST`  | `/`               | `OPERATEUR_ECONOMIQUE`              | Déposer l'offre technique (PDF) |
| `GET`   | `/{soumissionId}` | `OE, COMMISSION, ADMIN, CONTROLEUR` | Récupérer l'offre technique     |

### Offre Financière — `/offres-financieres`

| Méthode | Endpoint          | Rôle requis                         | Description                         |
| ------- | ----------------- | ----------------------------------- | ----------------------------------- |
| `POST`  | `/`               | `OPERATEUR_ECONOMIQUE`              | Déposer l'offre financière chiffrée |
| `GET`   | `/{soumissionId}` | `OE, COMMISSION, ADMIN, CONTROLEUR` | Récupérer l'offre financière        |

### Caution — `/cautions`

| Méthode | Endpoint          | Rôle requis                         | Description          |
| ------- | ----------------- | ----------------------------------- | -------------------- |
| `POST`  | `/`               | `OPERATEUR_ECONOMIQUE`              | Déposer la caution   |
| `GET`   | `/{soumissionId}` | `OE, COMMISSION, ADMIN, CONTROLEUR` | Récupérer la caution |

### Clés de Chiffrement — `/cles-chiffrement`

| Méthode | Endpoint           | Rôle requis                   | Description                                          |
| ------- | ------------------ | ----------------------------- | ---------------------------------------------------- |
| `POST`  | `/{aoId}`          | `ADMIN`                       | Générer les clés RSA-4096 + Shamir (fallback manuel) |
| `GET`   | `/{aoId}/publique` | `OPERATEUR_ECONOMIQUE, ADMIN` | Récupérer la clé publique                            |

> La génération des clés est normalement déclenchée **automatiquement** par RabbitMQ lors de la publication d'un AO.

---

## Sécurité

### Chaîne de filtres

```
Request
  │
  ├── SecurityHeadersFilter  (@Order -1)  → HSTS, CSP, X-Frame-Options…
  ├── RateLimitingFilter     (@Order  0)  → 100 req/min par IP (Bucket4j)
  └── SessionValidationFilter(@Order  1)  → Validation session Redis
        │
        ├── Token Redis valide → inject userId + userRole dans request
        └── Aucun token       → HTTP 401
              │
              └── Controllers → RbacGuard.requireRole(...)
```

### Authentification

Les sessions sont stockées dans **Redis** par l'auth-service. En production, chaque requête doit porter l'en-tête :

```
X-Session-Id: <session-token>
```

Le filtre (`SessionValidationFilter`) effectue dans l'ordre :

1. Vérifie l'existence de la session (`SESSION:{sessionId}`) dans Redis
2. Vérifie que la session n'est pas expirée
3. Vérifie que l'`accessToken` n'est pas blacklisté (`BLACKLIST:{accessToken}`) — révocation lors d'un logout ou rotation de token
4. Injecte `userId` et `userRole` comme attributs de la requête

**Mode développement** (`security.dev-fallback.enabled=true`) : les en-têtes `X-User-Id` et `X-User-Role` sont acceptés directement, sans session Redis.

### Rôles

| Rôle                   | Accès                                       |
| ---------------------- | ------------------------------------------- |
| `ADMIN`                | Accès universel                             |
| `OPERATEUR_ECONOMIQUE` | Création et dépôt de soumissions            |
| `MEMBRE_COMMISSION`    | Lecture des soumissions, ouverture des plis |
| `CONTROLEUR`           | Lecture seule                               |
| `SERVICE_CONTRACTANT`  | Gestion des AO (service externe)            |

---

## Messagerie RabbitMQ

### Événements consommés

| Queue                      | Event                      | Action                                            |
| -------------------------- | -------------------------- | ------------------------------------------------- |
| `appel_offre.publie`       | `AppelOffrePublieEvent`    | Génération automatique des clés RSA-4096 + Shamir |
| `appel_offre.cloture`      | —                          | Log de clôture (plus de soumissions acceptées)    |
| `commission.ouverture`     | `CommissionOuvertureEvent` | Déchiffrement des offres financières              |
| `ia.analyse.resultat`      | `IaAnalyseEvent`           | Réception des résultats d'analyse IA              |
| `offre_financiere.analyse` | —                          | Traitement de l'analyse financière                |

### Événements publiés

| Routing Key          | Event                   | Consommateurs                        |
| -------------------- | ----------------------- | ------------------------------------ |
| `soumission.deposee` | `SoumissionDeposeEvent` | Service Notifications, Service Audit |
| `offres.dechiffrees` | `OffresDecrypteesEvent` | Service Évaluations, Service Audit   |

### Idempotence

Chaque consumer est protégé par la table `processed_events` — un même message ne sera jamais traité deux fois.

---

## Cryptographie

### Chiffrement des offres financières

1. L'**opérateur** récupère la clé publique RSA-4096 de l'AO via `GET /cles-chiffrement/{aoId}/publique`
2. Il chiffre son fichier avec cette clé publique
3. Il signe le fichier avec sa clé privée **ECDSA P-384**
4. Il dépose le fichier chiffré + la signature + sa clé publique ECDSA

### Shamir Secret Sharing (K-of-N)

La clé privée RSA-4096 de chaque AO est fragmentée selon le schéma de Shamir sur GF(256) :

- **N** fragments distribués aux membres de la commission
- **K** fragments suffisent pour reconstituer la clé (seuil configurable)
- Aucun membre seul ne peut ouvrir les plis

### Ouverture des plis

1. L'événement `commission.ouverture` transporte les K fragments
2. Le service reconstitue la clé privée RSA
3. Il déchiffre les offres financières et vérifie les signatures ECDSA
4. Les PDF en clair sont stockés dans MinIO (`offres-financieres-claires`)

---

## Tests

```bash
# Tests unitaires uniquement
./mvnw test -Dtest="com.klodit.soumission_service.controller.**,\
com.klodit.soumission_service.security.**,\
com.klodit.soumission_service.service.**,\
com.klodit.soumission_service.messaging.**,\
com.klodit.soumission_service.crypto.**,\
com.klodit.soumission_service.exception.**,\
com.klodit.soumission_service.client.**,\
com.klodit.soumission_service.util.**" -DfailIfNoTests=false

# Tests d'intégration (nécessite Docker)
./mvnw test -Dtest="com.klodit.soumission_service.integration.**"

# Tous les tests + rapport JaCoCo
./mvnw verify
# Rapport : target/site/jacoco/index.html
```

**Résultats** : 193 tests (184 unitaires + 9 intégration), 0 échecs, 0 erreurs.

| Classe de test                       | Tests | Catégorie   |
| ------------------------------------ | ----- | ----------- |
| `SoumissionControllerTest`           | 12    | Controller  |
| `SoumissionServiceTest`              | 19    | Service     |
| `OffreTechniqueServiceTest`          | 9     | Service     |
| `OffreFinanciereControllerTest`      | 7     | Controller  |
| `GlobalExceptionHandlerTest`         | 12    | Exception   |
| `AppelOffreEventConsumerTest`        | 7     | Messaging   |
| `OffreFinanciereAnalyseConsumerTest` | 7     | Messaging   |
| `RateLimitingFilterTest`             | 7     | Sécurité    |
| `RbacGuardTest`                      | 9     | Sécurité    |
| `MetricsServiceTest`                 | 9     | Service     |
| `SoumissionFlowIntegrationTest`      | 6     | Intégration |
| `ChiffrementIntegrationTest`         | 3     | Intégration |
| _(+ 22 autres classes)_              | …     | …           |

---

## Kubernetes

Les manifests sont dans le dossier `k8s/` :

```
k8s/
├── namespace.yaml        # namespace: al-mizan
├── deployment.yaml       # 2 replicas, ressources CPU/mémoire
├── service.yaml          # ClusterIP port 8004
├── hpa.yaml              # HorizontalPodAutoscaler
├── configmap.yaml        # Variables d'environnement non-sensibles
├── secret.yaml           # Secrets (DB, Redis, MinIO, RabbitMQ)
└── serviceaccount.yaml   # ServiceAccount dédié
```

```bash
# Déployer
kubectl apply -f k8s/

# Vérifier
kubectl get pods -n al-mizan -l app=soumission-service
```

Image Docker : `registry.klodit.dz/al-mizan/soumission-service:latest`

---

## Monitoring

Le service expose des métriques Prometheus sur `/actuator/prometheus`.

Annotations Kubernetes pour la découverte automatique :

```yaml
prometheus.io/scrape: "true"
prometheus.io/port: "8004"
prometheus.io/path: "/actuator/prometheus"
```

Métriques personnalisées disponibles :

- `soumission.creations.total` — nombre total de soumissions créées
- `soumission.validations.total` — nombre de soumissions validées
- `soumission.dechiffrements.total` — nombre d'ouvertures de plis

Endpoint de santé : `GET /actuator/health`

---

## Licence

Propriétaire — **Klodit SARL** © 2025–2026. Tous droits réservés.

Ce projet est développé dans le cadre de la plateforme Al-Mizan pour la gestion des marchés publics en Algérie (Loi 23-12).
Toute reproduction ou utilisation sans autorisation écrite est interdite.
