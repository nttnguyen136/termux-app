package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stores encrypted SSH secrets (passwords and key passphrases) separately from profile metadata,
 * keyed by profile ID inside an app-private {@code 0700} directory with {@code 0600} per-secret files.
 */
public final class SshCredentialStore {

    private final File secretsDir;
    private final SshSecretCipher cipher;
    private final Object lock = new Object();

    public SshCredentialStore(@NonNull File secretsDir, @NonNull SshSecretCipher cipher) {
        this.secretsDir = secretsDir;
        this.cipher = cipher;
    }

    public boolean hasSecret(@NonNull String profileId) {
        String id = normalizeProfileId(profileId);
        synchronized (lock) {
            return getSecretFile(id).isFile();
        }
    }

    @Nullable
    public String getSecret(@NonNull String profileId)
        throws IOException, GeneralSecurityException {
        String id = normalizeProfileId(profileId);
        synchronized (lock) {
            File file = getSecretFile(id);
            if (!file.isFile()) {
                return null;
            }
            byte[] payload = SshFsUtils.readBytes(file);
            byte[] aad = id.getBytes(StandardCharsets.UTF_8);
            byte[] plaintext = cipher.decrypt(payload, aad);
            return new String(plaintext, StandardCharsets.UTF_8);
        }
    }

    public void saveSecret(@NonNull String profileId, @Nullable String secret)
        throws IOException, GeneralSecurityException {
        String id = normalizeProfileId(profileId);
        synchronized (lock) {
            if (secret == null || secret.isEmpty()) {
                deleteSecretLocked(id);
                return;
            }
            if (!SshFsUtils.ensureDir0700(secretsDir)) {
                throw new IOException("Unable to create secrets directory: " + secretsDir.getAbsolutePath());
            }
            byte[] plaintext = secret.getBytes(StandardCharsets.UTF_8);
            byte[] aad = id.getBytes(StandardCharsets.UTF_8);
            byte[] payload = cipher.encrypt(plaintext, aad);
            SshFsUtils.writeBytesAtomic0600(getSecretFile(id), payload);
        }
    }

    public boolean deleteSecret(@NonNull String profileId) {
        String id = normalizeProfileId(profileId);
        synchronized (lock) {
            return deleteSecretLocked(id);
        }
    }

    private boolean deleteSecretLocked(@NonNull String id) {
        File file = getSecretFile(id);
        return !file.exists() || file.delete();
    }

    @NonNull
    private File getSecretFile(@NonNull String id) {
        return new File(secretsDir, sha256Hex(id) + ".enc");
    }

    @NonNull
    private static String normalizeProfileId(@NonNull String profileId) {
        String trimmed = profileId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Profile ID must not be blank");
        }
        return trimmed;
    }

    @NonNull
    private static String sha256Hex(@NonNull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                int v = b & 0xFF;
                if (v < 16) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
