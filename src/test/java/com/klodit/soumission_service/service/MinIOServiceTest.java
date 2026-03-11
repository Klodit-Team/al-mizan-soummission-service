package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.exception.FichierInvalideException;
import io.minio.*;
import io.minio.http.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinIOService — Tests unitaires")
class MinIOServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinIOProperties minioProperties;

    @InjectMocks
    private MinIOService minIOService;

    // ── uploadFichier ────────────────────────────────────

    @Nested
    @DisplayName("uploadFichier")
    class UploadFichier {

        @Test
        @DisplayName("Upload succès — bucket existant")
        void uploadSucces_bucketExistant() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "fichier", "test.pdf", "application/pdf", "pdf data".getBytes());

            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

            String result = minIOService.uploadFichier(file, "offres-techniques", "soum-001");

            assertThat(result).startsWith("offres-techniques/soum-001/");
            assertThat(result).contains("test.pdf");
            verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("Upload succès — bucket créé automatiquement")
        void uploadSucces_bucketCree() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "fichier", "doc.pdf", "application/pdf", "data".getBytes());

            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

            String result = minIOService.uploadFichier(file, "new-bucket", "prefix");

            assertThat(result).startsWith("new-bucket/prefix/");
            verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("Fichier null → FichierInvalideException")
        void fichierNull() {
            assertThatThrownBy(() -> minIOService.uploadFichier(null, "bucket", "prefix"))
                    .isInstanceOf(FichierInvalideException.class)
                    .hasMessageContaining("vide ou absent");
        }

        @Test
        @DisplayName("Fichier vide → FichierInvalideException")
        void fichierVide() {
            MockMultipartFile file = new MockMultipartFile(
                    "fichier", "empty.pdf", "application/pdf", new byte[0]);

            assertThatThrownBy(() -> minIOService.uploadFichier(file, "bucket", "prefix"))
                    .isInstanceOf(FichierInvalideException.class);
        }

        @Test
        @DisplayName("Fichier trop gros (>50Mo) → FichierInvalideException")
        void fichierTropGros() {
            // Create a mock that reports 60 MB
            MockMultipartFile file = mock(MockMultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(60L * 1024 * 1024);

            assertThatThrownBy(() -> minIOService.uploadFichier(file, "bucket", "prefix"))
                    .isInstanceOf(FichierInvalideException.class)
                    .hasMessageContaining("50 Mo");
        }

        @Test
        @DisplayName("Erreur MinIO → FichierInvalideException")
        void erreurMinIO() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "fichier", "test.pdf", "application/pdf", "data".getBytes());

            when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> minIOService.uploadFichier(file, "bucket", "prefix"))
                    .isInstanceOf(FichierInvalideException.class)
                    .hasMessageContaining("Impossible d'uploader");
        }
    }

    // ── genererUrlPresignee ──────────────────────────────

    @Nested
    @DisplayName("genererUrlPresignee")
    class GenererUrlPresignee {

        @Test
        @DisplayName("Succès → URL retournée")
        void succes() throws Exception {
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenReturn("https://minio:9000/bucket/object?token=abc");

            String url = minIOService.genererUrlPresignee("bucket", "object");

            assertThat(url).contains("minio");
            assertThat(url).contains("token");
        }

        @Test
        @DisplayName("Erreur MinIO → FichierInvalideException")
        void erreur() throws Exception {
            when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                    .thenThrow(new RuntimeException("erreur"));

            assertThatThrownBy(() -> minIOService.genererUrlPresignee("bucket", "object"))
                    .isInstanceOf(FichierInvalideException.class);
        }
    }

    // ── telechargerFichier ───────────────────────────────

    @Nested
    @DisplayName("telechargerFichier")
    class TelechargerFichier {

        @Test
        @DisplayName("Succès → InputStream retourné")
        void succes() throws Exception {
            GetObjectResponse mockResponse = mock(GetObjectResponse.class);
            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

            InputStream result = minIOService.telechargerFichier("bucket", "object");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Erreur MinIO → FichierInvalideException")
        void erreur() throws Exception {
            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenThrow(new RuntimeException("not found"));

            assertThatThrownBy(() -> minIOService.telechargerFichier("bucket", "object"))
                    .isInstanceOf(FichierInvalideException.class);
        }
    }

    // ── supprimerFichier ─────────────────────────────────

    @Nested
    @DisplayName("supprimerFichier")
    class SupprimerFichier {

        @Test
        @DisplayName("Succès — pas d'exception")
        void succes() throws Exception {
            doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

            assertThatCode(() -> minIOService.supprimerFichier("bucket", "object"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Erreur MinIO — log warning, pas d'exception")
        void erreur_noException() throws Exception {
            doThrow(new RuntimeException("erreur")).when(minioClient)
                    .removeObject(any(RemoveObjectArgs.class));

            // supprimerFichier catches exceptions, should not throw
            assertThatCode(() -> minIOService.supprimerFichier("bucket", "object"))
                    .doesNotThrowAnyException();
        }
    }

    // ── uploadBytes ──────────────────────────────────────

    @Nested
    @DisplayName("uploadBytes")
    class UploadBytes {

        @Test
        @DisplayName("Succès — bytes uploadés")
        void succes() throws Exception {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

            String result = minIOService.uploadBytes(
                    "test data".getBytes(), "bucket", "file.bin");

            assertThat(result).isEqualTo("bucket/file.bin");
        }

        @Test
        @DisplayName("Erreur MinIO → FichierInvalideException")
        void erreur() throws Exception {
            when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                    .thenThrow(new RuntimeException("boom"));

            assertThatThrownBy(() -> minIOService.uploadBytes(
                    "data".getBytes(), "bucket", "file.bin"))
                    .isInstanceOf(FichierInvalideException.class);
        }
    }
}
