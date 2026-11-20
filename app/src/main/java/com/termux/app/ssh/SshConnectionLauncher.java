package com.termux.app.ssh;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemeManager;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates SSH pre-flight checks, ephemeral {@code SSH_ASKPASS} secret provisioning,
 * missing-{@code openssh} prompting, {@link SshSessionLaunchGateway} invocation, and
 * {@code lastUsedAtMillis} recording.
 */
public final class SshConnectionLauncher {

    private static final String LOG_TAG = "SshConnectionLauncher";

    public enum LaunchStatus {
        PENDING,
        LAUNCHED,
        PROMPTED_INSTALL_OPENSSH,
        VALIDATION_ERROR,
        CREDENTIAL_ERROR,
        LAUNCH_FAILED
    }

    public static final class LaunchOutcome {
        @NonNull public final LaunchStatus status;
        @Nullable public final AlertDialog dialog;
        @Nullable public final String errorMessage;

        public LaunchOutcome(
                @NonNull LaunchStatus status,
                @Nullable AlertDialog dialog,
                @Nullable String errorMessage) {
            this.status = status;
            this.dialog = dialog;
            this.errorMessage = errorMessage;
        }
    }

    @FunctionalInterface
    public interface Clock {
        long currentTimeMillis();
    }

    @FunctionalInterface
    public interface MissingOpenSshPrompter {
        @Nullable
        AlertDialog promptInstallOpenSsh(
            @NonNull SshProfile profile,
            @NonNull Runnable onConfirmInstallAndConnect
        );
    }

    public interface LaunchCallback {
        void onOutcome(@NonNull LaunchOutcome outcome);
    }

    @Nullable private final Activity activity;
    @NonNull private final SshProfileRepository repository;
    @NonNull private final File prefixDir;
    @NonNull private final File homeDir;
    @NonNull private final SshAskPassManager askPassManager;
    @NonNull private final SshSessionLaunchGateway gateway;
    @NonNull private final Clock clock;
    @NonNull private final MissingOpenSshPrompter missingOpenSshPrompter;
    private final AtomicBoolean gatewayLaunchInFlight = new AtomicBoolean(false);

    public SshConnectionLauncher(
            @NonNull Activity activity,
            @NonNull SshProfileRepository repository,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            @NonNull File tmpDir,
            @NonNull SshSessionLaunchGateway gateway,
            @NonNull Clock clock) {
        this(
            activity,
            repository,
            prefixDir,
            homeDir,
            tmpDir,
            gateway,
            clock,
            (profile, onConfirm) -> SshOpenSshInstallDialog.show(
                activity,
                profile,
                SshCommandBuilder.getSshBinary(prefixDir),
                onConfirm
            )
        );
    }

    public SshConnectionLauncher(
            @Nullable Activity activity,
            @NonNull SshProfileRepository repository,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            @NonNull File tmpDir,
            @NonNull SshSessionLaunchGateway gateway,
            @NonNull Clock clock,
            @NonNull MissingOpenSshPrompter missingOpenSshPrompter) {
        this.activity = activity;
        this.repository = repository;
        this.prefixDir = prefixDir;
        this.homeDir = homeDir;
        this.askPassManager = new SshAskPassManager(tmpDir);
        this.gateway = gateway;
        this.clock = clock;
        this.missingOpenSshPrompter = missingOpenSshPrompter;
    }

    @NonNull
    public static SshConnectionLauncher createDefault(
            @NonNull Activity activity,
            @NonNull SshProfileRepository repository) {
        return new SshConnectionLauncher(
            activity,
            repository,
            TermuxConstants.TERMUX_PREFIX_DIR,
            TermuxConstants.TERMUX_HOME_DIR,
            TermuxConstants.TERMUX_TMP_PREFIX_DIR,
            TermuxSshSessionLaunchGateway.forActivity(activity),
            System::currentTimeMillis
        );
    }

    public boolean isOpenSshInstalled() {
        return SshCommandBuilder.isSshAvailable(prefixDir);
    }

    @NonNull
    public LaunchOutcome connect(@NonNull SshProfile profile) {
        return connect(profile, null);
    }

    @NonNull
    public LaunchOutcome connect(@NonNull SshProfile profile, @Nullable LaunchCallback callback) {
        SshValidationResult validation = SshProfileValidator.validate(profile);
        if (!validation.isValid()) {
            LaunchOutcome outcome = new LaunchOutcome(
                LaunchStatus.VALIDATION_ERROR, null, validation.getJoinedErrorMessage());
            showErrorDialogIfNeeded(outcome);
            notifyCallback(callback, outcome);
            return outcome;
        }

        if (!isOpenSshInstalled()) {
            AlertDialog dialog = missingOpenSshPrompter.promptInstallOpenSsh(
                profile,
                () -> executePreparedLaunch(profile, true, callback)
            );
            LaunchOutcome outcome = new LaunchOutcome(LaunchStatus.PROMPTED_INSTALL_OPENSSH, dialog, null);
            notifyCallback(callback, outcome);
            return outcome;
        }

        return executePreparedLaunch(profile, false, callback);
    }

    @NonNull
    private LaunchOutcome executePreparedLaunch(
            @NonNull SshProfile profile,
            boolean installOpenSshFirst,
            @Nullable LaunchCallback callback) {
        if (!gatewayLaunchInFlight.compareAndSet(false, true)) {
            return new LaunchOutcome(LaunchStatus.PENDING, null, null);
        }

        SshAskPassManager.PreparedAskPass askPass = null;
        try {
            SshCommandSpec spec = SshCommandBuilder.build(profile, prefixDir);
            askPass = prepareAskPassIfSecretExists(profile);
            Map<String, String> env = askPass != null ? askPass.toEnvironment() : Collections.emptyMap();

            SshSessionLaunchRequest request;
            if (installOpenSshFirst) {
                String shellCommand = SshCommandBuilder.buildInstallOpenSshAndConnectCommand(spec, askPass);
                request = new SshSessionLaunchRequest(
                    SshCommandBuilder.getShBinary(prefixDir).getAbsolutePath(),
                    Arrays.asList("-c", shellCommand),
                    homeDir.getAbsolutePath(),
                    spec.sessionTitle,
                    env
                );
            } else {
                request = new SshSessionLaunchRequest(
                    spec.executablePath,
                    spec.arguments,
                    homeDir.getAbsolutePath(),
                    spec.sessionTitle,
                    env
                );
            }

            final SshAskPassManager.PreparedAskPass finalAskPass = askPass;
            final LaunchOutcome[] syncOutcome = new LaunchOutcome[1];
            final AtomicBoolean completed = new AtomicBoolean(false);
            try {
                gateway.launchSession(request, launched -> {
                    if (!completed.compareAndSet(false, true)) {
                        return;
                    }
                    LaunchOutcome resolved;
                    try {
                        if (!launched) {
                            cleanupQuietly(finalAskPass);
                            resolved = new LaunchOutcome(
                                LaunchStatus.LAUNCH_FAILED,
                                null,
                                installOpenSshFirst
                                    ? "Failed to start install-and-connect session"
                                    : "Failed to start SSH session"
                            );
                        } else {
                            try {
                                repository.markUsed(profile.id, clock.currentTimeMillis());
                            } catch (IOException e) {
                                Logger.logStackTraceWithMessage(
                                    LOG_TAG,
                                    "Failed to record lastUsedAtMillis for SSH profile " + profile.id,
                                    e
                                );
                            }
                            resolved = new LaunchOutcome(LaunchStatus.LAUNCHED, null, null);
                        }
                    } finally {
                        gatewayLaunchInFlight.set(false);
                    }
                    syncOutcome[0] = resolved;
                    notifyCallback(callback, resolved);
                });
            } catch (RuntimeException e) {
                if (completed.compareAndSet(false, true)) {
                    gatewayLaunchInFlight.set(false);
                    cleanupQuietly(askPass);
                }
                throw e;
            }

            return syncOutcome[0] != null
                ? syncOutcome[0]
                : new LaunchOutcome(LaunchStatus.PENDING, null, null);
        } catch (IllegalArgumentException e) {
            gatewayLaunchInFlight.set(false);
            cleanupQuietly(askPass);
            LaunchOutcome outcome = new LaunchOutcome(LaunchStatus.VALIDATION_ERROR, null, e.getMessage());
            showErrorDialogIfNeeded(outcome);
            notifyCallback(callback, outcome);
            return outcome;
        } catch (GeneralSecurityException | IOException e) {
            gatewayLaunchInFlight.set(false);
            cleanupQuietly(askPass);
            LaunchOutcome outcome = new LaunchOutcome(LaunchStatus.CREDENTIAL_ERROR, null, e.getMessage());
            showErrorDialogIfNeeded(outcome);
            notifyCallback(callback, outcome);
            return outcome;
        }
    }

    private void showErrorDialogIfNeeded(@NonNull LaunchOutcome outcome) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        String message;
        if (outcome.status == LaunchStatus.CREDENTIAL_ERROR) {
            message = activity.getString(R.string.ssh_launch_error_credential);
        } else if (outcome.status == LaunchStatus.VALIDATION_ERROR) {
            message = outcome.errorMessage != null
                ? outcome.errorMessage
                : activity.getString(R.string.ssh_launch_error_session);
        } else {
            return;
        }
        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(activity);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ssh_launch_error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        SshDialogStyler.showStyledDialog(activity, dialog, palette);
    }

    @Nullable
    private SshAskPassManager.PreparedAskPass prepareAskPassIfSecretExists(@NonNull SshProfile profile)
        throws IOException, GeneralSecurityException {
        String secret = repository.getSecret(profile.id);
        if (secret == null || secret.isEmpty()) {
            return null;
        }
        return askPassManager.createSingleUseHelper(secret);
    }

    private static void cleanupQuietly(@Nullable SshAskPassManager.PreparedAskPass askPass) {
        if (askPass != null) {
            askPass.cleanup();
        }
    }

    private static void notifyCallback(@Nullable LaunchCallback callback, @NonNull LaunchOutcome outcome) {
        if (callback != null) {
            callback.onOutcome(outcome);
        }
    }
}
