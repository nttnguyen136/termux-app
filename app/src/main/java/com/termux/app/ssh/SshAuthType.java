package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Authentication method supported by saved SSH account profiles.
 */
public enum SshAuthType {
    SSH_KEY,
    PASSWORD;

    @NonNull
    public static SshAuthType fromString(@Nullable String raw) {
        if (raw == null) {
            return SSH_KEY;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (PASSWORD.name().equals(normalized)) {
            return PASSWORD;
        }
        return SSH_KEY;
    }
}
