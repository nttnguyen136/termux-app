package com.termux.app.ssh;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.theme.TermuxThemeManager;

/**
 * Production {@link SshSessionLaunchGateway} backed by {@link TermuxService}.
 * Supports launching directly from {@link TermuxActivity} (drawer) as well as from
 * standalone foreground activities such as {@code SshHostsActivity}.
 */
public final class TermuxSshSessionLaunchGateway implements SshSessionLaunchGateway {

    @FunctionalInterface
    interface SessionCreator {
        @Nullable
        TermuxSession createSession(
            @NonNull TermuxService service,
            @NonNull SshSessionLaunchRequest request
        );
    }

    @NonNull private final Activity callerActivity;
    @Nullable private final TermuxService boundService;
    @NonNull private final SessionCreator sessionCreator;

    private TermuxSshSessionLaunchGateway(
            @NonNull Activity callerActivity,
            @Nullable TermuxService boundService,
            @NonNull SessionCreator sessionCreator) {
        this.callerActivity = callerActivity;
        this.boundService = boundService;
        this.sessionCreator = sessionCreator;
    }

    @NonNull
    public static TermuxSshSessionLaunchGateway forActivity(@NonNull Activity activity) {
        if (activity instanceof TermuxActivity) {
            return new TermuxSshSessionLaunchGateway(
                activity,
                ((TermuxActivity) activity).getTermuxService(),
                TermuxSshSessionLaunchGateway::defaultCreateSession
            );
        }
        return new TermuxSshSessionLaunchGateway(
            activity,
            null,
            TermuxSshSessionLaunchGateway::defaultCreateSession
        );
    }

    @NonNull
    static TermuxSshSessionLaunchGateway forActivityForTests(
            @NonNull Activity activity,
            @NonNull SessionCreator sessionCreator) {
        return new TermuxSshSessionLaunchGateway(activity, null, sessionCreator);
    }

    @NonNull
    public static TermuxSshSessionLaunchGateway forService(
            @NonNull Activity callerActivity,
            @NonNull TermuxService service) {
        return new TermuxSshSessionLaunchGateway(
            callerActivity,
            service,
            TermuxSshSessionLaunchGateway::defaultCreateSession
        );
    }

    @Nullable
    private static TermuxSession defaultCreateSession(
            @NonNull TermuxService service,
            @NonNull SshSessionLaunchRequest request) {
        return service.createTermuxSession(
            request.executablePath,
            request.getArgumentsArray(),
            null,
            request.workingDirectory,
            false,
            request.sessionTitle,
            request.getAdditionalEnvironmentCopyOrNull()
        );
    }

    @Override
    public boolean launchSession(@NonNull SshSessionLaunchRequest request) {
        final boolean[] initiatedOrSucceeded = {false};
        launchSessionInternal(request, launched -> initiatedOrSucceeded[0] = launched, initiatedOrSucceeded);
        return initiatedOrSucceeded[0];
    }

    @Override
    public void launchSession(
            @NonNull SshSessionLaunchRequest request,
            @NonNull GatewayCallback callback) {
        launchSessionInternal(request, callback, null);
    }

    private void launchSessionInternal(
            @NonNull SshSessionLaunchRequest request,
            @NonNull GatewayCallback callback,
            @Nullable boolean[] syncBindInitiatedOut) {
        if (callerActivity.isFinishing() || callerActivity.isDestroyed()) {
            cleanupAskPass(request);
            callback.onComplete(false);
            return;
        }
        if (boundService != null) {
            boolean ok = launchOnService(callerActivity, boundService, request, sessionCreator);
            if (!ok) {
                cleanupAskPass(request);
            }
            callback.onComplete(ok);
            return;
        }
        if (callerActivity instanceof TermuxActivity) {
            TermuxService service = ((TermuxActivity) callerActivity).getTermuxService();
            if (service != null) {
                boolean ok = launchOnService(callerActivity, service, request, sessionCreator);
                if (!ok) {
                    cleanupAskPass(request);
                }
                callback.onComplete(ok);
                return;
            }
        }
        boolean bindStarted = bindAndLaunchAsync(callerActivity, request, sessionCreator, callback);
        if (syncBindInitiatedOut != null) {
            syncBindInitiatedOut[0] = bindStarted;
        }
    }

    private static boolean bindAndLaunchAsync(
            @NonNull Activity activity,
            @NonNull SshSessionLaunchRequest request,
            @NonNull SessionCreator sessionCreator,
            @NonNull GatewayCallback callback) {
        Context appContext = activity.getApplicationContext();
        Intent serviceIntent = new Intent(appContext, TermuxService.class);
        try {
            appContext.startService(serviceIntent);
        } catch (RuntimeException ignored) {
            cleanupAskPass(request);
            showSessionErrorDialog(activity);
            callback.onComplete(false);
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
                boolean launched = false;
                try {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        cleanupAskPass(request);
                    } else if (binder instanceof TermuxService.LocalBinder) {
                        TermuxService service = ((TermuxService.LocalBinder) binder).service;
                        launched = launchOnService(activity, service, request, sessionCreator);
                        if (!launched) {
                            cleanupAskPass(request);
                        }
                    } else {
                        cleanupAskPass(request);
                        showSessionErrorDialog(activity);
                    }
                } finally {
                    unbindQuietly(appContext, this);
                }
                callback.onComplete(launched);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }

            @Override
            public void onBindingDied(ComponentName name) {
                failBindingIfPending(this);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                failBindingIfPending(this);
            }

            private void failBindingIfPending(@NonNull ServiceConnection self) {
                if (handled) {
                    return;
                }
                handled = true;
                try {
                    cleanupAskPass(request);
                    showSessionErrorDialog(activity);
                } finally {
                    unbindQuietly(appContext, self);
                }
                callback.onComplete(false);
            }
        };

        if (!appContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)) {
            unbindQuietly(appContext, connection);
            cleanupAskPass(request);
            showSessionErrorDialog(activity);
            callback.onComplete(false);
            return false;
        }
        return true;
    }

    private static boolean launchOnService(
            @NonNull Activity activity,
            @NonNull TermuxService service,
            @NonNull SshSessionLaunchRequest request,
            @NonNull SessionCreator sessionCreator) {
        if (service.getTermuxSessionsSize() >= TermuxTerminalSessionActivityClient.MAX_SESSIONS) {
            showThemedAlertDialog(
                activity,
                R.string.title_max_terminals_reached,
                R.string.msg_max_terminals_reached
            );
            return false;
        }

        TermuxSession created = sessionCreator.createSession(service, request);
        if (created == null) {
            showSessionErrorDialog(activity);
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

    private static void showSessionErrorDialog(@NonNull Activity activity) {
        showThemedAlertDialog(
            activity,
            R.string.ssh_launch_error_title,
            R.string.ssh_launch_error_session
        );
    }

    private static void showThemedAlertDialog(
            @NonNull Activity activity,
            int titleResId,
            int messageResId) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(titleResId)
            .setMessage(messageResId)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        SshDialogStyler.showStyledDialog(activity, dialog, TermuxThemeManager.getActivePalette(activity));
    }

    private static void cleanupAskPass(@NonNull SshSessionLaunchRequest request) {
        SshAskPassManager.cleanupFilesForScriptPath(
            request.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS)
        );
    }

    private static void unbindQuietly(@NonNull Context appContext, @NonNull ServiceConnection conn) {
        try {
            appContext.unbindService(conn);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
