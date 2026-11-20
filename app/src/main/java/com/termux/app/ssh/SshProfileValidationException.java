package com.termux.app.ssh;

import androidx.annotation.NonNull;

/**
 * Thrown when saving or validating an invalid {@link SshProfile}.
 */
public final class SshProfileValidationException extends IllegalArgumentException {

    @NonNull
    private final SshValidationResult validationResult;

    public SshProfileValidationException(@NonNull SshValidationResult validationResult) {
        super(validationResult.getJoinedErrorMessage());
        this.validationResult = validationResult;
    }

    @NonNull
    public SshValidationResult getValidationResult() {
        return validationResult;
    }
}
