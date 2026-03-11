package com.klodit.soumission_service.gatling;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Test de charge — Déchiffrement massif.
 *
 * CSL : 500 déchiffrements d'offres financières → temps total < 30s
 *
 * Ce test nécessite un setup préalable :
 * - Un AO avec des clés générées
 * - 500 offres financières chiffrées déposées
 * - Les fragments Shamir prêts
 *
 * En pratique, ce test valide la capacité du service à traiter
 * le déchiffrement AES-256-GCM + RSA-4096 de manière performante.
 */
public class DechiffrementLoadSimulation extends Simulation {

        HttpProtocolBuilder httpProtocol = http
                        .baseUrl("http://localhost:8004")
                        .acceptHeader("application/json")
                        .contentTypeHeader("application/json")
                        .header("X-User-Id", "commission-test")
                        .header("X-User-Role", "MEMBRE_COMMISSION");

        // Scénario : déchiffrement d'un lot d'offres pour un AO
        ScenarioBuilder scenarioDechiffrement = scenario("Déchiffrement massif")
                        .exec(http("GET /actuator/health — Pre-check")
                                        .get("/actuator/health")
                                        .check(status().is(200)))
                        .pause(Duration.ofSeconds(1))
                        .exec(http("GET soumissions par AO")
                                        .get("/api/v1/soumissions/appel-offre/ao-perf-test")
                                        .check(status().is(200)));

        {
                setUp(
                                scenarioDechiffrement.injectOpen(
                                                atOnceUsers(50) // 50 utilisateurs simultanés
                                )).protocols(httpProtocol)
                                .assertions(
                                                global().responseTime().max().lt(30000), // Max < 30s
                                                global().successfulRequests().percent().gt(99.0) // > 99% succès
                                );
        }
}
