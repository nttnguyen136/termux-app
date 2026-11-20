package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;

/**
 * Minimal process-execution seam for invoking {@code $PREFIX/bin/ssh-keygen}.
 * Production uses Termux {@code AppShell}; unit tests supply an in-memory fake.
 */
public interface SshKeygenRunner {

    @NonNull
    ExecutionResult runSshKeygen(
        @NonNull File prefixDir,
        @NonNull File homeDir,
        @NonNull File sshKeygenBinary,
        @NonNull List<String> arguments
    );

    final class ExecutionResult {
        public final int exitCode;
        @NonNull public final String stdout;
        @NonNull public final String stderr;
        @Nullable public final String errorMessage;

        private ExecutionResult(
                int exitCode,
                @Nullable String stdout,
                @Nullable String stderr,
                @Nullable String errorMessage) {
            this.exitCode = exitCode;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
            this.errorMessage = errorMessage;
        }

        @NonNull
        public static ExecutionResult success(@Nullable String stdout, @Nullable String stderr) {
            return new ExecutionResult(0, stdout, stderr, null);
        }

        @NonNull
        public static ExecutionResult failure(
                int exitCode,
                @Nullable String stdout,
                @Nullable String errorMessage) {
            return new ExecutionResult(exitCode, stdout, errorMessage, errorMessage);
        }

        @NonNull
        public static ExecutionResult failure(
                int exitCode,
                @Nullable String stdout,
                @Nullable String stderr,
                @Nullable String errorMessage) {
            return new ExecutionResult(exitCode, stdout, stderr, errorMessage);
        }

        public boolean isSuccess() {
            return exitCode == 0 && (errorMessage == null || errorMessage.trim().isEmpty());
        }

        @NonNull
        public String getEffectiveErrorMessage() {
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                return errorMessage.trim();
            }
            if (!stderr.trim().isEmpty()) {
                return stderr.trim();
            }
            if (!stdout.trim().isEmpty()) {
                return stdout.trim();
            }
            return "ssh-keygen exited with status " + exitCode;
        }
    }
}
