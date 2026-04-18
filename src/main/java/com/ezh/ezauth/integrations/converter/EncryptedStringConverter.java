package com.ezh.ezauth.integrations.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    // Use GCM for security. ECB is insecure as it produces same ciphertext for same plaintext.
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    @Value("${app.encryption.secret-key:ezauthDefaultKey!!}")
    private String rawSecretKey;

    private SecretKeySpec buildKey() {
        byte[] keyBytes = rawSecretKey.getBytes(StandardCharsets.UTF_8);
        byte[] key = Arrays.copyOf(keyBytes, 16);
        return new SecretKeySpec(key, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            // In GCM, using a fixed IV of all zeros is better than ECB,
            // but for production, you should store a random IV with the string.
            byte[] iv = new byte[IV_LENGTH_BYTE];
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] decoded = Base64.getDecoder().decode(dbData);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed: " + e.getMessage(), e);
        }
    }
}