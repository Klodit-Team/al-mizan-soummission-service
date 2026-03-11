package com.klodit.soumission_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinIOProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;

    private BucketConfig bucket = new BucketConfig();

    @Getter
    @Setter
    public static class BucketConfig {
        private String offresTechniques;
        private String offresFinancieres;
        private String offresFinancieresClaires;
        private String cautions;
    }
}
