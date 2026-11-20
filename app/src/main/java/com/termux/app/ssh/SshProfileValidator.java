package com.termux.app.ssh;

import androidx.annotation.NonNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Validates {@link SshProfile} fields against SSH connection and safety constraints.
 */
public final class SshProfileValidator {

    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;

    private SshProfileValidator() {
    }

    @NonNull
    public static SshValidationResult validate(@NonNull SshProfile profile) {
        Map<SshValidationResult.Field, String> errors = new EnumMap<>(SshValidationResult.Field.class);

        String host = profile.host.trim();
        if (host.isEmpty()) {
            errors.put(SshValidationResult.Field.HOST, "Host is required");
        } else if (containsWhitespace(host)) {
            errors.put(SshValidationResult.Field.HOST, "Host must not contain whitespace");
        } else if (host.startsWith("-")) {
            errors.put(SshValidationResult.Field.HOST, "Host must not start with '-'");
        }

        String username = profile.username.trim();
        if (username.isEmpty()) {
            errors.put(SshValidationResult.Field.USERNAME, "Username is required");
        } else if (containsWhitespace(username)) {
            errors.put(SshValidationResult.Field.USERNAME, "Username must not contain whitespace");
        } else if (username.startsWith("-")) {
            errors.put(SshValidationResult.Field.USERNAME, "Username must not start with '-'");
        }

        if (profile.port < MIN_PORT || profile.port > MAX_PORT) {
            errors.put(SshValidationResult.Field.PORT, "Port must be between 1 and 65535");
        }

        if (profile.keepAliveIntervalSeconds < 0) {
            errors.put(
                SshValidationResult.Field.KEEP_ALIVE_INTERVAL,
                "KeepAlive interval cannot be negative"
            );
        }

        if (containsLineBreak(profile.proxyJump)) {
            errors.put(SshValidationResult.Field.PROXY_JUMP, "ProxyJump must be a single line");
        }

        if (containsLineBreak(profile.privateKeyPath)) {
            errors.put(
                SshValidationResult.Field.PRIVATE_KEY_PATH,
                "Private key path must be a single line"
            );
        }

        return new SshValidationResult(errors);
    }

    public static void requireValid(@NonNull SshProfile profile) {
        SshValidationResult result = validate(profile);
        if (!result.isValid()) {
            throw new SshProfileValidationException(result);
        }
    }

    private static boolean containsWhitespace(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLineBreak(@NonNull String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
