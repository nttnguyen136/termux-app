package com.termux.app.ssh;

import androidx.annotation.NonNull;

/**
 * Minimal process/session launch boundary seam for starting dedicated SSH {@code TerminalSession}s
 * (or {@code pkg install -y openssh} chained sessions) without invoking real OpenSSH in unit tests.
 */
public interface SshSessionLaunchGateway {

    @FunctionalInterface
    interface GatewayCallback {
        void onComplete(boolean launched);
    }

    /**
     * Launches a foreground terminal session described by {@code request}.
     *
     * @return {@code true} if the session launch succeeded, {@code false} otherwise.
     */
    boolean launchSession(@NonNull SshSessionLaunchRequest request);

    /**
     * Launches a foreground terminal session described by {@code request} and notifies
     * {@code callback} only once the session launch attempt has completed (including async service binds).
     */
    default void launchSession(
            @NonNull SshSessionLaunchRequest request,
            @NonNull GatewayCallback callback) {
        callback.onComplete(launchSession(request));
    }
}
