package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable session launch specification passed across {@link SshSessionLaunchGateway}.
 */
public final class SshSessionLaunchRequest {

    @NonNull public final String executablePath;
    @NonNull public final List<String> arguments;
    @Nullable public final String workingDirectory;
    @NonNull public final String sessionTitle;
    @NonNull public final Map<String, String> additionalEnvironment;

    public SshSessionLaunchRequest(
            @NonNull String executablePath,
            @NonNull List<String> arguments,
            @Nullable String workingDirectory,
            @NonNull String sessionTitle,
            @Nullable Map<String, String> additionalEnvironment) {
        this.executablePath = executablePath;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.workingDirectory = workingDirectory;
        this.sessionTitle = sessionTitle;
        this.additionalEnvironment = additionalEnvironment == null || additionalEnvironment.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new HashMap<>(additionalEnvironment));
    }

    @NonNull
    public String[] getArgumentsArray() {
        return arguments.toArray(new String[0]);
    }

    @Nullable
    public HashMap<String, String> getAdditionalEnvironmentCopyOrNull() {
        return additionalEnvironment.isEmpty() ? null : new HashMap<>(additionalEnvironment);
    }
}
