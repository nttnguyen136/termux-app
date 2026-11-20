package com.termux.app.ssh;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemeManager;

import java.io.File;

/**
 * Material 3 confirmation dialog shown when {@code $PREFIX/bin/ssh} is missing or not executable,
 * offering a one-tap action to install {@code openssh} and immediately connect.
 */
public final class SshOpenSshInstallDialog {

    private SshOpenSshInstallDialog() {
    }

    @NonNull
    public static AlertDialog show(
            @NonNull Activity activity,
            @NonNull SshProfile profile,
            @NonNull File sshBinary,
            @NonNull Runnable onConfirmInstallAndConnect) {
        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(activity);
        float density = activity.getResources().getDisplayMetrics().density;

        View view = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_ssh_install_openssh, null, false);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setShape(GradientDrawable.RECTANGLE);
        rootBg.setCornerRadius(24f * density);
        rootBg.setColor(palette.surfaceContainerHigh);
        view.setBackground(rootBg);

        TextView titleView = view.findViewById(R.id.ssh_install_dialog_title);
        TextView messageView = view.findViewById(R.id.ssh_install_dialog_message);
        TextView commandPreviewView = view.findViewById(R.id.ssh_install_dialog_command_preview);

        if (titleView != null) {
            titleView.setText(R.string.ssh_missing_openssh_title);
            titleView.setTextColor(palette.onSurface);
        }

        if (messageView != null) {
            messageView.setText(activity.getString(
                R.string.ssh_missing_openssh_message,
                sshBinary.getAbsolutePath(),
                profile.getEffectiveDisplayName()
            ));
            messageView.setTextColor(palette.onSurfaceVariant);
        }

        if (commandPreviewView != null) {
            GradientDrawable previewBg = new GradientDrawable();
            previewBg.setShape(GradientDrawable.RECTANGLE);
            previewBg.setCornerRadius(12f * density);
            previewBg.setColor(palette.surfaceContainer);
            previewBg.setStroke(Math.max(1, Math.round(density)), palette.outline);
            commandPreviewView.setBackground(previewBg);
            commandPreviewView.setTextColor(palette.onSurface);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(R.string.ssh_missing_openssh_install_and_connect, (d, which) ->
                onConfirmInstallAndConnect.run())
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        SshDialogStyler.showStyledDialog(activity, dialog, palette);
        return dialog;
    }
}
