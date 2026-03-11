package com.klodit.soumission_service.service;

import com.klodit.soumission_service.exception.FichierInvalideException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Service de calcul et de vérification d'empreinte SHA-256.
 * Garantit l'intégrité des fichiers déposés (offres techniques et financières).
 */
@Service
@Slf4j
public class HashService {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Calcule le hash SHA-256 d'un fichier multipart.
     *
     * @param file le fichier à hacher
     * @return la chaîne hexadécimale du hash (64 caractères)
     */
    public String calculerHash(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return calculerHash(is);
        } catch (Exception e) {
            log.error("Erreur calcul SHA-256 : {}", e.getMessage(), e);
            throw new FichierInvalideException("Impossible de calculer le hash du fichier");
        }
    }

    /**
     * Calcule le hash SHA-256 d'un flux d'entrée.
     */
    public String calculerHash(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            log.error("Erreur calcul SHA-256 sur InputStream : {}", e.getMessage(), e);
            throw new FichierInvalideException("Impossible de calculer le hash");
        }
    }

    /**
     * Calcule le hash SHA-256 d'un tableau d'octets.
     */
    public String calculerHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new FichierInvalideException("Impossible de calculer le hash des données");
        }
    }

    /**
     * Vérifie qu'un fichier correspond à un hash connu.
     *
     * @param file        le fichier à vérifier
     * @param hashAttendu le hash SHA-256 attendu
     * @return true si le fichier correspond au hash
     */
    public boolean verifierIntegrite(MultipartFile file, String hashAttendu) {
        String hashCalcule = calculerHash(file);
        boolean integre = hashCalcule.equalsIgnoreCase(hashAttendu);
        if (!integre) {
            log.warn("Intégrité compromise — attendu: {}, calculé: {}", hashAttendu, hashCalcule);
        }
        return integre;
    }
}
