package com.klodit.soumission_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.*;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;

/**
 * Configuration du RestClient avec mTLS pour la communication inter-services.
 *
 * Activé uniquement quand le profil 'mtls' est actif.
 * Le RestClient présente le certificat client du soumission-service
 * aux autres microservices et vérifie leur certificat serveur
 * contre le truststore commun.
 *
 * En dev (sans profil mtls), le RestClient par défaut est utilisé (HTTP
 * simple).
 */
@Configuration
@ConditionalOnProperty(name = "server.ssl.client-auth", havingValue = "need")
public class RestClientMtlsConfig {

    @Value("${server.ssl.key-store}")
    private String keyStorePath;

    @Value("${server.ssl.key-store-password}")
    private String keyStorePassword;

    @Value("${server.ssl.trust-store}")
    private String trustStorePath;

    @Value("${server.ssl.trust-store-password}")
    private String trustStorePassword;

    @Bean
    public RestClient.Builder mtlsRestClientBuilder() throws Exception {
        // Charger le keystore (identité du service)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(keyStorePath.replace("classpath:", ""))) {
            keyStore.load(is, keyStorePassword.toCharArray());
        }

        // Charger le truststore (CA de confiance)
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(trustStorePath.replace("classpath:", ""))) {
            trustStore.load(is, trustStorePassword.toCharArray());
        }

        // KeyManager (certificat client présenté aux autres services)
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword.toCharArray());

        // TrustManager (vérifie le certificat des autres services)
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Configurer le SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // Configurer le HttpClient JDK avec le SSLContext
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient));
    }
}
