package com.klodit.soumission_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration OpenAPI 3.0 complète pour le Soumission Service.
 * Swagger UI accessible sur /api/docs (profil dev uniquement).
 *
 * 14 endpoints documentés avec :
 * - Descriptions détaillées (@Operation)
 * - Paramètres annotés (@Parameter)
 * - Réponses typées (@ApiResponse)
 * - Schémas de sécurité (X-Session-Id / X-User-Id)
 * - Exemples de requêtes/réponses
 */
@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI soumissionServiceOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Soumission Service API")
                                                .description("""
                                                                API REST du Service Soumissions — Projet Al-Mizan.

                                                                Gère le cycle de vie complet d'une soumission :
                                                                création de brouillon, dépôt d'offres (technique + financière chiffrée),
                                                                caution bancaire, validation avec horodatage légal,
                                                                et déchiffrement lors de l'ouverture des plis.

                                                                **Authentification** : Session Redis via X-Session-Id
                                                                ou fallback dev via X-User-Id + X-User-Role.

                                                                **Chiffrement** : AES-256-GCM + RSA-4096, fragmentation Shamir GF(256).
                                                                """)
                                                .version("1.0.0")
                                                .contact(new Contact()
                                                                .name("Équipe Al-Mizan")
                                                                .email("dev@klodit.dz"))
                                                .license(new License()
                                                                .name("Propriétaire")
                                                                .url("https://klodit.dz")))
                                .servers(List.of(
                                                new Server().url("http://localhost:8004")
                                                                .description("Développement local"),
                                                new Server().url("https://api.almizan.dz/soumission")
                                                                .description("Production")))
                                .components(new Components()
                                                .addSecuritySchemes("sessionId", new SecurityScheme()
                                                                .type(SecurityScheme.Type.APIKEY)
                                                                .in(SecurityScheme.In.HEADER)
                                                                .name("X-Session-Id")
                                                                .description("ID de session Redis (production)"))
                                                .addSecuritySchemes("userId", new SecurityScheme()
                                                                .type(SecurityScheme.Type.APIKEY)
                                                                .in(SecurityScheme.In.HEADER)
                                                                .name("X-User-Id")
                                                                .description("ID utilisateur (dev fallback)"))
                                                .addSecuritySchemes("userRole", new SecurityScheme()
                                                                .type(SecurityScheme.Type.APIKEY)
                                                                .in(SecurityScheme.In.HEADER)
                                                                .name("X-User-Role")
                                                                .description("Rôle utilisateur (dev fallback)")))
                                .addSecurityItem(new SecurityRequirement()
                                                .addList("sessionId")
                                                .addList("userId")
                                                .addList("userRole"))
                                .tags(List.of(
                                                new Tag().name("Soumissions")
                                                                .description("CRUD et workflow des soumissions (US-1, US-5, US-8)"),
                                                new Tag().name("Offres Techniques")
                                                                .description("Dépôt et consultation des offres techniques (US-2)"),
                                                new Tag().name("Offres Financières")
                                                                .description("Dépôt et déchiffrement des offres financières (US-3, US-7)"),
                                                new Tag().name("Cautions")
                                                                .description("Gestion des cautions bancaires (US-4)"),
                                                new Tag().name("Clés de Chiffrement")
                                                                .description("Génération et récupération des clés RSA-4096 + Shamir (US-6)")));
        }
}
