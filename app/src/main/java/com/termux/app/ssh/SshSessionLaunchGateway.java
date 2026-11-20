package com.termux.app.ssh;

import androidx.annotation.NonNull;

/**
 * Minimal process/session launch boundary seam for starting dedicated SSH {@code TerminalSession}s
 * (or {@code pkg install -y openssh} chained sessions) without invoking real OpenSSH in unit tests.
 */
public interface SshSessionLaunchGateway {

    /**
     * Launches a foreground terminal session described by {@code request}.
     *
     * @return {@code true} if the session launch succeeded, {@code false} otherwise.
     */
    boolean launchSession(@NonNull SshSessionLaunchRequest request);
}
