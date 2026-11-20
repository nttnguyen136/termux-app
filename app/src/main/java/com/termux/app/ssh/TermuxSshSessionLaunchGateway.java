package com.termux.app.ssh;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import java.io.File;

/**
 * Production {@link SshSessionLaunchGateway} backed by {@link TermuxService}.
 * Supports launching directly from {@link TermuxActivity} (drawer) as well as from
 * standalone foreground activities such as {@code SshHostsActivity}.
 */
public final class TermuxSshSessionLaunchGateway implements SshSessionLaunchGateway {

    @NonNull private final Activity callerActivity;
    @Nullable private final TermuxService boundService;

    private TermuxSshSessionLaunchGateway(
            @NonNull Activity callerActivity,
            @Nullable TermuxService boundService) {
        this.callerActivity = callerActivity;
        this.boundService = boundService;
    }

    @NonNull
    public static TermuxSshSessionLaunchGateway forActivity(@NonNull Activity activity) {
        if (activity instanceof TermuxActivity) {
            return new TermuxSshSessionLaunchGateway(activity, ((TermuxActivity) activity).getTermuxService());
        }
        return new TermuxSshSessionLaunchGateway(activity, null);
    }

    @NonNull
    public static TermuxSshSessionLaunchGateway forService(
            @NonNull Activity callerActivity,
            @NonNull TermuxService service) {
        return new TermuxSshSessionLaunchGateway(callerActivity, service);
    }

    @Override
    public boolean launchSession(@NonNull SshSessionLaunchRequest request) {
        if (callerActivity.isFinishing() || callerActivity.isDestroyed()) {
            return false;
        }
        if (boundService != null) {
            return launchOnService(callerActivity, boundService, request);
        }
        if (callerActivity instanceof TermuxActivity) {
            TermuxService service = ((TermuxActivity) callerActivity).getTermuxService();
            if (service != null) {
                return launchOnService(callerActivity, service, request);
            }
        }
        return bindAndLaunchAsync(callerActivity, request);
    }

    private static boolean bindAndLaunchAsync(
            @NonNull Activity activity,
            @NonNull SshSessionLaunchRequest request) {
        Context appContext = activity.getApplicationContext();
        Intent serviceIntent = new Intent(appContext, TermuxService.class);
        try {
            appContext.startService(serviceIntent);
        } catch (RuntimeException ignored) {
            return false;
        }

        ServiceConnection connection = new ServiceConnection() {
            private boolean handled = false;

            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                if (handled) {
                    return;
                }
                handled = true;
                try {
                    if (binder instanceof TermuxService.LocalBinder) {
                        TermuxService service = ((TermuxService.LocalBinder) binder).service;
                        if (!launchOnService(activity, service, request)) {
                            cleanupAskPassFilesFromRequest(request);
                        }
                    } else {
                        cleanupAskPassFilesFromRequest(request);
                    }
                } finally {
                    try {
                        appContext.unbindService(this);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        if (!appContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)) {
            return false;
        }
        return true;
    }

    private static boolean launchOnService(
            @NonNull Activity activity,
            @NonNull TermuxService service,
            @NonNull SshSessionLaunchRequest request) {
        if (service.getTermuxSessionsSize() >= TermuxTerminalSessionActivityClient.MAX_SESSIONS) {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.title_max_terminals_reached)
                    .setMessage(R.string.msg_max_terminals_reached)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            }
            return false;
        }

        TermuxSession created = service.createTermuxSession(
            request.executablePath,
            request.getArgumentsArray(),
            null,
            request.workingDirectory,
            false,
            request.sessionTitle,
            request.getAdditionalEnvironmentCopyOrNull()
        );
        if (created == null) {
            return false;
        }

        service.selectTermuxSession(created);

        if (activity instanceof TermuxActivity) {
            TermuxActivity termuxActivity = (TermuxActivity) activity;
            if (termuxActivity.getDrawer() != null) {
                termuxActivity.getDrawer().closeDrawers();
            }
        } else {
            ActivityUtils.startActivity(activity, TermuxActivity.newInstance(activity));
        }
        return true;
    }

    private static void cleanupAskPassFilesFromRequest(@NonNull SshSessionLaunchRequest request) {
        String scriptPath = request.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS);
        if (scriptPath == null || scriptPath.isEmpty()) {
            return;
        }
        File scriptFile = new File(scriptPath);
        if (scriptFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            scriptFile.delete();
        }
        if (scriptPath.endsWith(".sh")) {
            File secretFile = new File(scriptPath.substring(0, scriptPath.length() - 3) + ".secret");
            if (secretFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                secretFile.delete();
            }
        }
    }
}
