package com.corebanking.openbanking.security;

import com.corebanking.openbanking.config.OpenBankingProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SensitiveValueCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final OpenBankingProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SensitiveValueCipher(OpenBankingProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not encrypt Open Banking secret.", failure);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not decrypt Open Banking secret.", failure);
        }
    }

    public String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not fingerprint Open Banking identifier.", failure);
        }
    }

    private SecretKeySpec key() {
        String configured = properties.encryptionKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("OPEN_BANKING_ENCRYPTION_KEY must be a Base64 encoded 32-byte key.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("OPEN_BANKING_ENCRYPTION_KEY must be valid Base64.", failure);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("OPEN_BANKING_ENCRYPTION_KEY must decode to exactly 32 bytes.");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
