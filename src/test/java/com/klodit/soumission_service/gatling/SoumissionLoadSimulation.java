package com.klodit.soumission_service.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Tests de charge Gatling — Soumission Service.
 *
 * Scénarios CSL :
 * 1. Pic de soumissions : 5 000 soumissions en 5 minutes (≈ 17 req/s)
 * → Latence P95 < 2s, 0% erreurs 5xx
 * 2. Consultation portail : 10 000 requêtes GET simultanées
 * → Temps de réponse < 1s
 * 3. Cible throughput : 500 req/s
 *
 * Prérequis : le service doit être démarré sur localhost:8004
 * avec l'infrastructure Docker (MySQL, Redis, RabbitMQ, MinIO).
 */
public class SoumissionLoadSimulation extends Simulation {

    // ── Configuration HTTP ──────────────────────────────────
    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8004")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .header("X-User-Role", "OPERATEUR_ECONOMIQUE");

    // ── Feeder : génération de données dynamiques ───────────
    Iterator<Map<String, Object>> feeder = Stream.generate(() -> Map.<String, Object>of(
            "operateurId", "op-gatling-" + UUID.randomUUID().toString().substring(0, 8),
            "appelOffreId", "ao-gatling-" + UUID.randomUUID().toString().substring(0, 8),
            "lotId", "lot-gatling-" + UUID.randomUUID().toString().substring(0, 8))).iterator();

    // ═══════════════════════════════════════════════════════════
    // Scénario 1 : Pic de créations de soumissions (US-1)
    // CSL : 5 000 soumissions en 5 min → P95 < 2s, 0% 5xx
    // ═══════════════════════════════════════════════════════════
    ScenarioBuilder scenarioCreation = scenario("Pic création soumissions")
            .feed(feeder)
            .exec(http("POST /soumissions — Créer brouillon")
                    .post("/api/v1/soumissions")
                    .header("X-User-Id", "#{operateurId}")
                    .body(StringBody("""
                            {
                                "appelOffreId": "#{appelOffreId}",
                                "lotId": "#{lotId}"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.success").is("true"))
                    .check(jsonPath("$.data.id").saveAs("soumissionId")))
            .pause(Duration.ofMillis(100), Duration.ofMillis(500));

    // ═══════════════════════════════════════════════════════════
    // Scénario 2 : Consultation massive (US-8)
    // CSL : 10 000 requêtes GET → < 1s
    // ═══════════════════════════════════════════════════════════
    ScenarioBuilder scenarioConsultation = scenario("Pic consultation soumissions")
            .feed(feeder)
            .exec(http("GET /soumissions — Lister mes soumissions")
                    .get("/api/v1/soumissions")
                    .header("X-User-Id", "#{operateurId}")
                    .check(status().is(200))
                    .check(jsonPath("$.success").is("true")))
            .pause(Duration.ofMillis(50), Duration.ofMillis(200));

    // ═══════════════════════════════════════════════════════════
    // Scénario 3 : Health check (baseline)
    // ═══════════════════════════════════════════════════════════
    ScenarioBuilder scenarioHealth = scenario("Health check baseline")
            .exec(http("GET /actuator/health")
                    .get("/actuator/health")
                    .check(status().is(200)));

    // ═══════════════════════════════════════════════════════════
    // Injection — profils de charge
    // ═══════════════════════════════════════════════════════════
    {
        setUp(
                // Scénario 1 : 5 000 soumissions en 5 minutes
                scenarioCreation.injectOpen(
                        rampUsersPerSec(1).to(17).during(Duration.ofMinutes(1)), // ramp-up
                        constantUsersPerSec(17).during(Duration.ofMinutes(4)) // plateau
                ),

                // Scénario 2 : 10 000 consultations en 2 minutes
                scenarioConsultation.injectOpen(
                        rampUsersPerSec(10).to(85).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(85).during(Duration.ofSeconds(90))),

                // Scénario 3 : Health check continu (monitoring)
                scenarioHealth.injectOpen(
                        constantUsersPerSec(5).during(Duration.ofMinutes(5))))
                .protocols(httpProtocol)
                .assertions(
                        // Assertions CSL
                        global().responseTime().percentile3().lt(2000), // P95 < 2s
                        global().successfulRequests().percent().gt(99.0), // > 99% succès
                        global().failedRequests().percent().lt(1.0) // < 1% erreurs
                );
    }
}
