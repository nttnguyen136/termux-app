package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable metadata model for a saved SSH account profile.
 * Passwords and key passphrases are never stored on this object; they live separately
 * in {@link SshCredentialStore} encrypted by profile ID.
 */
public final class SshProfile {

    public static final int DEFAULT_PORT = 22;
    public static final SshAuthType DEFAULT_AUTH_TYPE = SshAuthType.SSH_KEY;

    @NonNull public final String id;
    @NonNull public final String displayName;
    @NonNull public final String host;
    public final int port;
    @NonNull public final String username;
    @NonNull public final SshAuthType authType;
    @NonNull public final String privateKeyPath;
    public final boolean pinned;
    public final long lastUsedAtMillis;
    @NonNull public final String startupCommand;
    @NonNull public final String proxyJump;
    public final int keepAliveIntervalSeconds;
    @NonNull public final String extraArgs;

    private SshProfile(@NonNull Builder builder) {
        this.id = (builder.id != null && !builder.id.trim().isEmpty())
            ? builder.id.trim()
            : UUID.randomUUID().toString();
        this.displayName = clean(builder.displayName);
        this.host = clean(builder.host);
        this.port = builder.port;
        this.username = clean(builder.username);
        this.authType = builder.authType != null ? builder.authType : DEFAULT_AUTH_TYPE;
        this.privateKeyPath = clean(builder.privateKeyPath);
        this.pinned = builder.pinned;
        this.lastUsedAtMillis = Math.max(0L, builder.lastUsedAtMillis);
        this.startupCommand = clean(builder.startupCommand);
        this.proxyJump = clean(builder.proxyJump);
        this.keepAliveIntervalSeconds = builder.keepAliveIntervalSeconds;
        this.extraArgs = clean(builder.extraArgs);
    }

    @NonNull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    @NonNull
    public SshProfile withPinned(boolean pinned) {
        return toBuilder().pinned(pinned).build();
    }

    @NonNull
    public SshProfile withLastUsedAtMillis(long lastUsedAtMillis) {
        return toBuilder().lastUsedAtMillis(lastUsedAtMillis).build();
    }

    /**
     * Returns {@link #displayName} if non-empty, otherwise falls back to {@code username@host}
     * (or {@code host} / {@code id} when incomplete).
     */
    @NonNull
    public String getEffectiveDisplayName() {
        if (!displayName.isEmpty()) {
            return displayName;
        }
        if (!username.isEmpty() && !host.isEmpty()) {
            return username + "@" + host;
        }
        if (!host.isEmpty()) {
            return host;
        }
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SshProfile)) return false;
        SshProfile that = (SshProfile) o;
        return port == that.port
            && pinned == that.pinned
            && lastUsedAtMillis == that.lastUsedAtMillis
            && keepAliveIntervalSeconds == that.keepAliveIntervalSeconds
            && id.equals(that.id)
            && displayName.equals(that.displayName)
            && host.equals(that.host)
            && username.equals(that.username)
            && authType == that.authType
            && privateKeyPath.equals(that.privateKeyPath)
            && startupCommand.equals(that.startupCommand)
            && proxyJump.equals(that.proxyJump)
            && extraArgs.equals(that.extraArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, host, port, username, authType, privateKeyPath,
            pinned, lastUsedAtMillis, startupCommand, proxyJump, keepAliveIntervalSeconds, extraArgs);
    }

    @NonNull
    @Override
    public String toString() {
        return "SshProfile{" +
            "id='" + id + '\'' +
            ", displayName='" + displayName + '\'' +
            ", host='" + host + '\'' +
            ", port=" + port +
            ", username='" + username + '\'' +
            ", authType=" + authType +
            ", pinned=" + pinned +
            '}';
    }

    public static final class Builder {
        @Nullable private String id;
        @Nullable private String displayName;
        @Nullable private String host;
        private int port = DEFAULT_PORT;
        @Nullable private String username;
        @NonNull private SshAuthType authType = DEFAULT_AUTH_TYPE;
        @Nullable private String privateKeyPath;
        private boolean pinned = false;
        private long lastUsedAtMillis = 0L;
        @Nullable private String startupCommand;
        @Nullable private String proxyJump;
        private int keepAliveIntervalSeconds = 0;
        @Nullable private String extraArgs;

        public Builder() {
        }

        private Builder(@NonNull SshProfile source) {
            this.id = source.id;
            this.displayName = source.displayName;
            this.host = source.host;
            this.port = source.port;
            this.username = source.username;
            this.authType = source.authType;
            this.privateKeyPath = source.privateKeyPath;
            this.pinned = source.pinned;
            this.lastUsedAtMillis = source.lastUsedAtMillis;
            this.startupCommand = source.startupCommand;
            this.proxyJump = source.proxyJump;
            this.keepAliveIntervalSeconds = source.keepAliveIntervalSeconds;
            this.extraArgs = source.extraArgs;
        }

        @NonNull
        public Builder id(@Nullable String id) {
            this.id = id;
            return this;
        }

        @NonNull
        public Builder displayName(@Nullable String displayName) {
            this.displayName = displayName;
            return this;
        }

        @NonNull
        public Builder host(@Nullable String host) {
            this.host = host;
            return this;
        }

        @NonNull
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        @NonNull
        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        @NonNull
        public Builder authType(@Nullable SshAuthType authType) {
            this.authType = authType != null ? authType : DEFAULT_AUTH_TYPE;
            return this;
        }

        @NonNull
        public Builder privateKeyPath(@Nullable String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        @NonNull
        public Builder pinned(boolean pinned) {
            this.pinned = pinned;
            return this;
        }

        @NonNull
        public Builder lastUsedAtMillis(long lastUsedAtMillis) {
            this.lastUsedAtMillis = lastUsedAtMillis;
            return this;
        }

        @NonNull
        public Builder startupCommand(@Nullable String startupCommand) {
            this.startupCommand = startupCommand;
            return this;
        }

        @NonNull
        public Builder proxyJump(@Nullable String proxyJump) {
            this.proxyJump = proxyJump;
            return this;
        }

        @NonNull
        public Builder keepAliveIntervalSeconds(int keepAliveIntervalSeconds) {
            this.keepAliveIntervalSeconds = keepAliveIntervalSeconds;
            return this;
        }

        @NonNull
        public Builder extraArgs(@Nullable String extraArgs) {
            this.extraArgs = extraArgs;
            return this;
        }

        @NonNull
        public SshProfile build() {
            return new SshProfile(this);
        }
    }
}
