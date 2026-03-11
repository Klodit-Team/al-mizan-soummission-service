package com.klodit.soumission_service.service;

import com.klodit.soumission_service.exception.ChiffrementException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Service de chiffrement hybride : AES-256-GCM pour les données,
 * RSA-4096 pour le transport de la clé symétrique.
 *
 * Flux de chiffrement côté client :
 * 1. Générer une clé AES-256 aléatoire
 * 2. Chiffrer le fichier offre financière → ciphertext (AES-256-GCM)
 * 3. Chiffrer la clé AES avec la clé publique RSA-4096 de l'AO → encryptedKey
 * 4. Déposer : ciphertext + encryptedKey + IV/nonce
 *
 * Côté serveur (ouverture des plis) :
 * 1. Reconstituer la clé privée RSA via Shamir (K fragments)
 * 2. Déchiffrer encryptedKey → clé AES
 * 3. Déchiffrer ciphertext → offre financière en clair
 */
@Service
@Slf4j
public class ChiffrementService {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommandé pour GCM
    private static final int AES_KEY_SIZE = 256;

    static {
        // Enregistrer Bouncy Castle comme provider de sécurité
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ── Génération de clés ────────────────────────────────

    /**
     * Génère une paire de clés RSA-4096 pour un appel d'offres.
     */
    public KeyPair genererPaireClésRSA() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA",
                    BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(4096, new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            log.info("Paire de clés RSA-4096 générée");
            return keyPair;
        } catch (Exception e) {
            throw new ChiffrementException("Impossible de générer la paire de clés RSA-4096 : " + e.getMessage());
        }
    }

    /**
     * Génère une clé AES-256 aléatoire.
     */
    public SecretKey genererCleAES() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_SIZE, new SecureRandom());
            return generator.generateKey();
        } catch (Exception e) {
            throw new ChiffrementException("Impossible de générer la clé AES-256 : " + e.getMessage());
        }
    }

    // ── Chiffrement AES-256-GCM ───────────────────────────

    /**
     * Chiffre des données avec AES-256-GCM.
     *
     * @param données données en clair
     * @param cleAES  clé AES-256
     * @return tableau : [0] = ciphertext, [1] = IV (12 bytes)
     */
    public byte[][] chiffrerAES(byte[] données, SecretKey cleAES) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, cleAES, paramSpec);

            byte[] ciphertext = cipher.doFinal(données);
            return new byte[][] { ciphertext, iv };
        } catch (Exception e) {
            throw new ChiffrementException("Erreur chiffrement AES-256-GCM : " + e.getMessage());
        }
    }

    /**
     * Déchiffre des données avec AES-256-GCM.
     *
     * @param ciphertext données chiffrées
     * @param iv         vecteur d'initialisation (12 bytes)
     * @param cleAES     clé AES-256
     * @return données en clair
     */
    public byte[] dechiffrerAES(byte[] ciphertext, byte[] iv, SecretKey cleAES) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, cleAES, paramSpec);
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new ChiffrementException("Erreur déchiffrement AES-256-GCM : " + e.getMessage());
        }
    }

    // ── Chiffrement RSA-4096 OAEP ─────────────────────────

    /**
     * Chiffre la clé AES avec la clé publique RSA-4096 (OAEP/SHA-256).
     */
    public byte[] chiffrerCleAES(SecretKey cleAES, PublicKey clePublique) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, clePublique);
            return cipher.doFinal(cleAES.getEncoded());
        } catch (Exception e) {
            throw new ChiffrementException("Erreur chiffrement RSA de la clé AES : " + e.getMessage());
        }
    }

    /**
     * Déchiffre la clé AES avec la clé privée RSA-4096.
     */
    public SecretKey dechiffrerCleAES(byte[] cleAESChiffree, PrivateKey clePrivee) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, clePrivee);
            byte[] cleAESBytes = cipher.doFinal(cleAESChiffree);
            return new SecretKeySpec(cleAESBytes, "AES");
        } catch (Exception e) {
            throw new ChiffrementException("Erreur déchiffrement RSA de la clé AES : " + e.getMessage());
        }
    }

    // ── Encodage / Décodage Base64 ────────────────────────

    public String encoderBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public byte[] decoderBase64(String data) {
        return Base64.getDecoder().decode(data);
    }

    /**
     * Reconstruit une PublicKey RSA depuis son encodage PEM Base64.
     */
    public PublicKey reconstruireClePublique(String clePubliquePem) {
        try {
            // Nettoyer le PEM (enlever header/footer et sauts de ligne)
            String cleaned = clePubliquePem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            return factory.generatePublic(spec);
        } catch (Exception e) {
            throw new ChiffrementException("Impossible de reconstruire la clé publique RSA : " + e.getMessage());
        }
    }

    /**
     * Encode une PublicKey au format PEM Base64.
     */
    public String encoderClePubliqueEnPem(PublicKey clePublique) {
        String base64 = Base64.getEncoder().encodeToString(clePublique.getEncoded());
        // PEM standard : lignes de 64 caractères max
        StringBuilder pem = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        pem.append("-----END PUBLIC KEY-----");
        return pem.toString();
    }

    /**
     * Encode une PrivateKey au format Base64.
     */
    public String encoderClePrivee(PrivateKey clePrivee) {
        return Base64.getEncoder().encodeToString(clePrivee.getEncoded());
    }

    // ── Signature ECDSA P-384 (non-répudiation) ──────────

    /**
     * Génère une paire de clés ECDSA sur la courbe P-384.
     * Utilisé côté client (WebCrypto ou Java) pour signer les offres.
     * Côté serveur, cette méthode est disponible pour les tests.
     */
    public KeyPair genererPaireClesECDSA() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC",
                    BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(new java.security.spec.ECGenParameterSpec("secp384r1"),
                    new SecureRandom());
            KeyPair keyPair = generator.generateKeyPair();
            log.info("Paire de clés ECDSA P-384 générée");
            return keyPair;
        } catch (Exception e) {
            throw new ChiffrementException("Impossible de générer la paire de clés ECDSA P-384 : " + e.getMessage());
        }
    }

    /**
     * Signe des données avec une clé privée ECDSA P-384.
     * Algorithme : SHA384withECDSA (conforme CSL §4.4.5).
     *
     * @param donnees   données à signer (typiquement le hash du ciphertext)
     * @param clePrivee clé privée ECDSA P-384 de l'opérateur économique
     * @return signature en bytes (format DER)
     */
    public byte[] signerECDSA(byte[] donnees, PrivateKey clePrivee) {
        try {
            Signature signature = Signature.getInstance("SHA384withECDSA",
                    BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(clePrivee, new SecureRandom());
            signature.update(donnees);
            byte[] sig = signature.sign();
            log.debug("Signature ECDSA P-384 générée ({} bytes)", sig.length);
            return sig;
        } catch (Exception e) {
            throw new ChiffrementException("Erreur signature ECDSA P-384 : " + e.getMessage());
        }
    }

    /**
     * Vérifie une signature ECDSA P-384.
     * Appelé à l'étape 4 du flux E2EE (Table 4.11) :
     * "Soumission Service reçoit le ciphertext, vérifie la signature, stocke sans
     * jamais déchiffrer"
     *
     * @param donnees        données originales signées
     * @param signatureBytes signature ECDSA (format DER)
     * @param clePublique    clé publique ECDSA P-384 de l'opérateur économique
     * @return true si la signature est valide
     */
    public boolean verifierSignatureECDSA(byte[] donnees, byte[] signatureBytes, PublicKey clePublique) {
        try {
            Signature signature = Signature.getInstance("SHA384withECDSA",
                    BouncyCastleProvider.PROVIDER_NAME);
            signature.initVerify(clePublique);
            signature.update(donnees);
            boolean valide = signature.verify(signatureBytes);
            log.info("Vérification signature ECDSA P-384 : {}", valide ? "VALIDE" : "INVALIDE");
            return valide;
        } catch (Exception e) {
            throw new ChiffrementException("Erreur vérification signature ECDSA P-384 : " + e.getMessage());
        }
    }

    /**
     * Reconstruit une PublicKey ECDSA depuis son encodage PEM Base64.
     */
    public PublicKey reconstruireClePubliqueECDSA(String clePubliquePem) {
        try {
            String cleaned = clePubliquePem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            return factory.generatePublic(spec);
        } catch (Exception e) {
            throw new ChiffrementException(
                    "Impossible de reconstruire la clé publique ECDSA P-384 : " + e.getMessage());
        }
    }
}
