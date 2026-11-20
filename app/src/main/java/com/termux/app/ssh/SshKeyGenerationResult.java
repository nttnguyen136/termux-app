package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * Outcome of generating an Ed25519 SSH key pair via {@link SshKeyManager}.
 */
public final class SshKeyGenerationResult {

    public enum Status {
        SUCCESS,
        MISSING_OPENSSH,
        FAILED
    }

    @NonNull private final Status status;
    @Nullable private final File privateKeyFile;
    @Nullable private final File publicKeyFile;
    @Nullable private final String publicKeyContent;
    @Nullable private final String errorMessage;

    private SshKeyGenerationResult(
            @NonNull Status status,
            @Nullable File privateKeyFile,
            @Nullable File publicKeyFile,
            @Nullable String publicKeyContent,
            @Nullable String errorMessage) {
        this.status = status;
        this.privateKeyFile = privateKeyFile;
        this.publicKeyFile = publicKeyFile;
        this.publicKeyContent = publicKeyContent;
        this.errorMessage = errorMessage;
    }

    @NonNull
    public static SshKeyGenerationResult success(
            @NonNull File privateKeyFile,
            @NonNull File publicKeyFile,
            @NonNull String publicKeyContent) {
        return new SshKeyGenerationResult(
            Status.SUCCESS,
            privateKeyFile,
            publicKeyFile,
            publicKeyContent,
            null
        );
    }

    @NonNull
    public static SshKeyGenerationResult missingOpenssh() {
        return new SshKeyGenerationResult(
            Status.MISSING_OPENSSH,
            null,
            null,
            null,
            "OpenSSH (ssh-keygen) is not installed"
        );
    }

    @NonNull
    public static SshKeyGenerationResult failed(@Nullable String errorMessage) {
        return new SshKeyGenerationResult(
            Status.FAILED,
            null,
            null,
            null,
            errorMessage != null && !errorMessage.trim().isEmpty()
                ? errorMessage.trim()
                : "Failed to generate Ed25519 SSH key"
        );
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isMissingOpenssh() {
        return status == Status.MISSING_OPENSSH;
    }

    @Nullable
    public File getPrivateKeyFile() {
        return privateKeyFile;
    }

    @Nullable
    public String getPrivateKeyPath() {
        return privateKeyFile != null ? privateKeyFile.getAbsolutePath() : null;
    }

    @Nullable
    public File getPublicKeyFile() {
        return publicKeyFile;
    }

    @Nullable
    public String getPublicKeyContent() {
        return publicKeyContent;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }
}
