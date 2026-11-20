package com.termux.app.ssh;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * Dependency bundle for {@link SshHostsActivity}, allowing production defaults to be replaced
 * cleanly with {@code TemporaryFolder}-backed repositories and fake process/session gateways in tests.
 */
final class SshHostsEnvironment {

    @NonNull final SshProfileRepository repository;
    @NonNull final File prefixDir;
    @NonNull final File homeDir;
    @NonNull final File tmpDir;
    @NonNull final SshSessionLaunchGateway launchGateway;
    @NonNull final SshKeygenRunner keygenRunner;
    @NonNull final SshConnectionLauncher.Clock clock;

    SshHostsEnvironment(
            @NonNull SshProfileRepository repository,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            @NonNull File tmpDir,
            @NonNull SshSessionLaunchGateway launchGateway,
            @NonNull SshKeygenRunner keygenRunner,
            @NonNull SshConnectionLauncher.Clock clock) {
        this.repository = repository;
        this.prefixDir = prefixDir;
        this.homeDir = homeDir;
        this.tmpDir = tmpDir;
        this.launchGateway = launchGateway;
        this.keygenRunner = keygenRunner;
        this.clock = clock;
    }

    @NonNull
    static SshHostsEnvironment createDefault(@NonNull Activity activity) {
        return new SshHostsEnvironment(
            SshProfileRepository.createDefault(activity),
            TermuxConstants.TERMUX_PREFIX_DIR,
            TermuxConstants.TERMUX_HOME_DIR,
            TermuxConstants.TERMUX_TMP_PREFIX_DIR,
            TermuxSshSessionLaunchGateway.forActivity(activity),
            SshKeyManager.createDefaultRunner(activity),
            System::currentTimeMillis
        );
    }

    @NonNull
    File getSshDir() {
        return new File(homeDir, ".ssh");
    }

    @NonNull
    File getSshConfigFile() {
        return new File(getSshDir(), "config");
    }
}
