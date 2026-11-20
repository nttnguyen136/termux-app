package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pure-Java parser and validator for SSH profile form inputs, separating field parsing and
 * validation from Android dialog view wiring in {@link SshProfileFormDialog}.
 */
final class SshProfileDraft {

    static final class ParsedForm {
        @NonNull final SshProfile profile;
        @NonNull final SshValidationResult validation;
        final boolean invalidKeepAliveFormat;
        @Nullable final String extraArgsError;

        ParsedForm(
                @NonNull SshProfile profile,
                @NonNull SshValidationResult validation,
                boolean invalidKeepAliveFormat,
                @Nullable String extraArgsError) {
            this.profile = profile;
            this.validation = validation;
            this.invalidKeepAliveFormat = invalidKeepAliveFormat;
            this.extraArgsError = extraArgsError;
        }

        boolean isValid() {
            return validation.isValid() && extraArgsError == null && !invalidKeepAliveFormat;
        }
    }

    private SshProfileDraft() {
    }

    @NonNull
    static ParsedForm parseAndValidate(
            @Nullable SshProfile existingProfile,
            @NonNull String displayName,
            @NonNull String host,
            @NonNull String rawPort,
            @NonNull String username,
            @NonNull SshAuthType authType,
            @NonNull String privateKeyPath,
            @NonNull String startupCommand,
            @NonNull String proxyJump,
            @NonNull String rawKeepAlive,
            @NonNull String rawExtraArgs) {
        String trimmedPort = rawPort.trim();
        int parsedPort;
        if (trimmedPort.isEmpty()) {
            parsedPort = 0;
        } else {
            try {
                parsedPort = Integer.parseInt(trimmedPort);
            } catch (NumberFormatException e) {
                parsedPort = -1;
            }
        }

        String trimmedKeepAlive = rawKeepAlive.trim();
        int parsedKeepAlive = 0;
        boolean invalidKeepAliveFormat = false;
        if (!trimmedKeepAlive.isEmpty()) {
            try {
                parsedKeepAlive = Integer.parseInt(trimmedKeepAlive);
            } catch (NumberFormatException e) {
                parsedKeepAlive = -1;
                invalidKeepAliveFormat = true;
            }
        }

        String trimmedExtraArgs = rawExtraArgs.trim();
        String extraArgsError = null;
        try {
            SshCommandBuilder.parseExtraArgs(trimmedExtraArgs);
        } catch (IllegalArgumentException e) {
            extraArgsError = e.getMessage();
        }

        SshProfile.Builder builder = existingProfile != null ? existingProfile.toBuilder() : SshProfile.builder();
        SshProfile candidate = builder
            .displayName(displayName)
            .host(host)
            .port(parsedPort)
            .username(username)
            .authType(authType)
            .privateKeyPath(authType == SshAuthType.SSH_KEY ? privateKeyPath : "")
            .startupCommand(startupCommand)
            .proxyJump(proxyJump)
            .keepAliveIntervalSeconds(parsedKeepAlive)
            .extraArgs(trimmedExtraArgs)
            .build();

        SshValidationResult validation = SshProfileValidator.validate(candidate);
        return new ParsedForm(candidate, validation, invalidKeepAliveFormat, extraArgsError);
    }
}
