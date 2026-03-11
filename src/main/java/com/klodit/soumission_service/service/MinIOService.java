package com.klodit.soumission_service.service;

import com.klodit.soumission_service.config.MinIOProperties;
import com.klodit.soumission_service.exception.FichierInvalideException;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinIOService {

    private final MinioClient minioClient;
    private final MinIOProperties minioProperties;

    /**
     * Upload un fichier vers un bucket MinIO et retourne son URL de stockage.
     *
     * @param file       le fichier multipart reçu
     * @param bucketName le nom du bucket cible
     * @param prefix     préfixe de nom (ex: "offre-technique")
     * @return l'URL de stockage (ex: "offres-techniques/UUID-filename.pdf")
     */
    public String uploadFichier(MultipartFile file, String bucketName, String prefix) {
        validerFichier(file);
        String objectName = prefix + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            // Créer le bucket s'il n'existe pas
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Bucket créé : {}", bucketName);
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            log.info("Fichier uploadé → bucket: {}, objet: {}", bucketName, objectName);
            return bucketName + "/" + objectName;

        } catch (Exception e) {
            log.error("Erreur upload MinIO : {}", e.getMessage(), e);
            throw new FichierInvalideException("Impossible d'uploader le fichier : " + e.getMessage());
        }
    }

    /**
     * Génère une URL présignée (accès temporaire sécurisé) valable 15 minutes.
     */
    public String genererUrlPresignee(String bucketName, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(15, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Erreur génération URL présignée : {}", e.getMessage(), e);
            throw new FichierInvalideException("Impossible de générer l'URL d'accès : " + e.getMessage());
        }
    }

    /**
     * Télécharge un fichier depuis MinIO en tant que flux d'octets.
     */
    public InputStream telechargerFichier(String bucketName, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            log.error("Erreur téléchargement MinIO : {}", e.getMessage(), e);
            throw new FichierInvalideException("Impossible de télécharger le fichier : " + e.getMessage());
        }
    }

    /**
     * Supprime un fichier d'un bucket MinIO.
     */
    public void supprimerFichier(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            log.info("Fichier supprimé → bucket: {}, objet: {}", bucketName, objectName);
        } catch (Exception e) {
            log.warn("Impossible de supprimer le fichier MinIO : {}", e.getMessage());
        }
    }

    /**
     * Upload des bytes bruts vers un bucket MinIO (pour tests / dev).
     */
    public String uploadBytes(byte[] data, String bucketName, String objectName) {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Bucket créé : {}", bucketName);
            }

            var bais = new java.io.ByteArrayInputStream(data);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(bais, data.length, -1)
                            .contentType("application/octet-stream")
                            .build());

            log.info("Bytes uploadés → bucket: {}, objet: {} ({} bytes)", bucketName, objectName, data.length);
            return bucketName + "/" + objectName;
        } catch (Exception e) {
            log.error("Erreur upload bytes MinIO : {}", e.getMessage(), e);
            throw new FichierInvalideException("Impossible d'uploader les bytes : " + e.getMessage());
        }
    }

    // ── Validation interne ─────────────────────────────────

    private void validerFichier(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FichierInvalideException("Le fichier est vide ou absent");
        }
        // Limite : 50 Mo (configuré aussi dans application.properties)
        long maxSize = 50L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new FichierInvalideException("Le fichier dépasse la taille maximale de 50 Mo");
        }
        // Types autorisés
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf")
                && !contentType.startsWith("application/zip")
                && !contentType.startsWith("application/octet-stream"))) {
            log.warn("Type MIME reçu : {} — accepté par tolérance", contentType);
            // En prod, lever une exception si le type n'est pas autorisé
        }
    }
}
