package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.GeneralSecurityException;

/**
 * Minimal OS-boundary crypto seam for encrypting and decrypting SSH secrets
 * (passwords and key passphrases). Production uses Android Keystore AES-GCM
 * ({@link AndroidKeystoreSshSecretCipher}); JVM unit tests inject a software
 * AES-GCM implementation ({@link AesGcmSshSecretCipher}).
 */
public interface SshSecretCipher {

    @NonNull
    byte[] encrypt(@NonNull byte[] plaintext, @Nullable byte[] associatedData)
        throws GeneralSecurityException;

    @NonNull
    byte[] decrypt(@NonNull byte[] ciphertextPayload, @Nullable byte[] associatedData)
        throws GeneralSecurityException;
}
