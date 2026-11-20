package com.termux.app.ssh;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable OpenSSH command invocation specification for launching a dedicated SSH session.
 */
public final class SshCommandSpec {

    @NonNull public final String executablePath;
    @NonNull public final List<String> arguments;
    @NonNull public final String sessionTitle;

    public SshCommandSpec(
            @NonNull String executablePath,
            @NonNull List<String> arguments,
            @NonNull String sessionTitle) {
        this.executablePath = executablePath;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.sessionTitle = sessionTitle;
    }

    @NonNull
    public String[] getArgumentsArray() {
        return arguments.toArray(new String[0]);
    }

    @NonNull
    public String toShellCommandLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(SshCommandBuilder.shellQuote(executablePath));
        for (String arg : arguments) {
            sb.append(' ').append(SshCommandBuilder.shellQuote(arg));
        }
        return sb.toString();
    }
}
