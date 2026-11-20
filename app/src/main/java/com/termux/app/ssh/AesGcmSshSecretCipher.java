package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Software {@code AES/GCM/NoPadding} implementation of {@link SshSecretCipher} using a
 * caller-supplied or ephemeral 256-bit {@link SecretKey} and a fresh 96-bit random IV
 * for every encryption.
 */
public final class AesGcmSshSecretCipher implements SshSecretCipher {

    static final String TRANSFORMATION = "AES/GCM/NoPadding";
    static final byte PAYLOAD_VERSION_1 = 1;
    static final int GCM_IV_LENGTH_BYTES = 12;
    static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public AesGcmSshSecretCipher(@NonNull SecretKey secretKey) {
        this(secretKey, new SecureRandom());
    }

    public AesGcmSshSecretCipher(@NonNull SecretKey secretKey, @NonNull SecureRandom secureRandom) {
        this.secretKey = secretKey;
        this.secureRandom = secureRandom;
    }

    @NonNull
    public static AesGcmSshSecretCipher withRandomKey() throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return new AesGcmSshSecretCipher(keyGenerator.generateKey());
    }

    @NonNull
    @Override
    public byte[] encrypt(@NonNull byte[] plaintext, @Nullable byte[] associatedData)
        throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        byte[] encrypted = cipher.doFinal(plaintext);
        return packPayload(iv, encrypted);
    }

    @NonNull
    @Override
    public byte[] decrypt(@NonNull byte[] ciphertextPayload, @Nullable byte[] associatedData)
        throws GeneralSecurityException {
        ParsedPayload parsed = unpackPayload(ciphertextPayload);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            new GCMParameterSpec(GCM_TAG_LENGTH_BITS, parsed.iv)
        );
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        return cipher.doFinal(parsed.ciphertext);
    }

    @NonNull
    static byte[] packPayload(@NonNull byte[] iv, @NonNull byte[] ciphertext)
        throws GeneralSecurityException {
        if (iv.length < GCM_IV_LENGTH_BYTES || iv.length > 255) {
            throw new InvalidAlgorithmParameterException("Unexpected GCM IV length: " + iv.length);
        }
        byte[] out = new byte[2 + iv.length + ciphertext.length];
        out[0] = PAYLOAD_VERSION_1;
        out[1] = (byte) iv.length;
        System.arraycopy(iv, 0, out, 2, iv.length);
        System.arraycopy(ciphertext, 0, out, 2 + iv.length, ciphertext.length);
        return out;
    }

    @NonNull
    static ParsedPayload unpackPayload(@NonNull byte[] payload) throws GeneralSecurityException {
        if (payload.length < 2) {
            throw new GeneralSecurityException("Encrypted secret payload is truncated");
        }
        if (payload[0] != PAYLOAD_VERSION_1) {
            throw new GeneralSecurityException("Unsupported encrypted secret version: " + payload[0]);
        }
        int ivLength = payload[1] & 0xFF;
        if (ivLength < GCM_IV_LENGTH_BYTES || payload.length <= 2 + ivLength) {
            throw new GeneralSecurityException("Invalid encrypted secret IV header");
        }
        byte[] iv = Arrays.copyOfRange(payload, 2, 2 + ivLength);
        byte[] ciphertext = Arrays.copyOfRange(payload, 2 + ivLength, payload.length);
        return new ParsedPayload(iv, ciphertext);
    }

    static final class ParsedPayload {
        final byte[] iv;
        final byte[] ciphertext;

        ParsedPayload(@NonNull byte[] iv, @NonNull byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }
}
