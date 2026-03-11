package com.klodit.soumission_service.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Classe de base pour les tests d'intégration avec Testcontainers.
 *
 * Conteneurs démarrés automatiquement (Singleton pattern) :
 * - MySQL 8.x (soumission_db)
 * - RabbitMQ 3.x-management
 * - MinIO (S3-compatible)
 * - Redis 7.x
 *
 * Les propriétés Spring sont injectées dynamiquement
 * via @DynamicPropertySource.
 * Les conteneurs sont démarrés une seule fois et partagés entre
 * toutes les classes de tests d'intégration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

        // ── MySQL 8 ────────────────────────────────────────────
        static final MySQLContainer<?> mysql = new MySQLContainer<>(
                        DockerImageName.parse("mysql:8.0"))
                        .withDatabaseName("soumission_db")
                        .withUsername("test")
                        .withPassword("test")
                        .withInitScript("init-test.sql");

        // ── RabbitMQ ───────────────────────────────────────────
        static final RabbitMQContainer rabbitmq = new RabbitMQContainer(
                        DockerImageName.parse("rabbitmq:3-management"));

        // ── MinIO (S3-compatible) ──────────────────────────────
        static final GenericContainer<?> minio = new GenericContainer<>(
                        DockerImageName.parse("minio/minio:latest"))
                        .withExposedPorts(9000)
                        .withEnv("MINIO_ROOT_USER", "minioadmin")
                        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                        .withCommand("server /data");

        // ── Redis ──────────────────────────────────────────────
        static final GenericContainer<?> redis = new GenericContainer<>(
                        DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);

        static {
                mysql.start();
                rabbitmq.start();
                minio.start();
                redis.start();
        }

        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                // MySQL
                registry.add("spring.datasource.url", mysql::getJdbcUrl);
                registry.add("spring.datasource.username", mysql::getUsername);
                registry.add("spring.datasource.password", mysql::getPassword);
                registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

                // RabbitMQ
                registry.add("spring.rabbitmq.host", rabbitmq::getHost);
                registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
                registry.add("spring.rabbitmq.username", () -> "guest");
                registry.add("spring.rabbitmq.password", () -> "guest");

                // MinIO
                registry.add("minio.endpoint", () -> "http://" + minio.getHost() + ":" + minio.getMappedPort(9000));
                registry.add("minio.access-key", () -> "minioadmin");
                registry.add("minio.secret-key", () -> "minioadmin");

                // Redis
                registry.add("spring.data.redis.host", redis::getHost);
                registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

                // Activer le fallback dev pour les tests d'intégration (headers
                // X-User-Id/X-User-Role)
                registry.add("security.dev-fallback.enabled", () -> "true");
        }
}
