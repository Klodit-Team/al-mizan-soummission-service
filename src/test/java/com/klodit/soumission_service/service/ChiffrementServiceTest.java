package com.klodit.soumission_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ChiffrementService — Tests unitaires")
class ChiffrementServiceTest {

    private ChiffrementService chiffrementService;

    @BeforeEach
    void setUp() {
        chiffrementService = new ChiffrementService();
    }

    @Test
    @DisplayName("AES-256-GCM — chiffrement puis déchiffrement → plaintext identique")
    void aes_chiffrer_dechiffrer_roundtrip() {
        SecretKey cle = chiffrementService.genererCleAES();
        byte[] plaintext = "Montant TTC : 1 190 000 DZD".getBytes();

        byte[][] resultat = chiffrementService.chiffrerAES(plaintext, cle);
        byte[] ciphertext = resultat[0];
        byte[] iv = resultat[1];

        byte[] decrypte = chiffrementService.dechiffrerAES(ciphertext, iv, cle);

        assertThat(decrypte).isEqualTo(plaintext);
        assertThat(ciphertext).isNotEqualTo(plaintext);
    }

    @Test
    @DisplayName("RSA-4096 — chiffrement clé AES puis déchiffrement")
    void rsa_chiffrer_cle_aes_roundtrip() {
        // Note : Ce test est lent (~3-5 secondes à cause de RSA-4096)
        KeyPair keyPair = chiffrementService.genererPaireClésRSA();
        SecretKey cleAES = chiffrementService.genererCleAES();

        byte[] cleAESChiffree = chiffrementService.chiffrerCleAES(cleAES, keyPair.getPublic());
        SecretKey cleAESDechiffree = chiffrementService.dechiffrerCleAES(
                cleAESChiffree, keyPair.getPrivate());

        assertThat(cleAESDechiffree.getEncoded()).isEqualTo(cleAES.getEncoded());
    }

    @Test
    @DisplayName("Encodage / décodage PEM de la clé publique")
    void encoderDecoderClePublique() {
        KeyPair keyPair = chiffrementService.genererPaireClésRSA();
        String pem = chiffrementService.encoderClePubliqueEnPem(keyPair.getPublic());

        assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(pem).endsWith("-----END PUBLIC KEY-----");

        var cleReconstituee = chiffrementService.reconstruireClePublique(pem);
        assertThat(cleReconstituee.getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
    }
}
