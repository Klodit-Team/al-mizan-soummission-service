package com.klodit.soumission_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configuration sécurité Phase 3 :
     * - CSRF désactivé (API REST stateless derrière API Gateway)
     * - Sessions stateless (pas de session HTTP côté serveur)
     * - Tout le trafic est autorisé au niveau Spring Security
     * - La sécurité est gérée par SessionValidationFilter (@Component @Order(1))
     * qui valide les sessions Redis ou les headers X-User-Id/X-User-Role
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());
        return http.build();
    }
}
