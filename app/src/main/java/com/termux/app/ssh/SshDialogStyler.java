package com.termux.app.ssh;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.termux.shared.termux.theme.TermuxColorPalette;

/**
 * Applies active {@link TermuxColorPalette} Material 3 colors (rounded {@code surfaceContainerHigh}
 * background, title/message typography, and action button colors) to SSH {@link AlertDialog}s.
 */
public final class SshDialogStyler {

    private SshDialogStyler() {
    }

    public static void showStyledDialog(
            @NonNull Context context,
            @NonNull AlertDialog dialog,
            @NonNull TermuxColorPalette palette) {
        showStyledDialog(context, dialog, palette, palette.accent);
    }

    public static void showStyledDialog(
            @NonNull Context context,
            @NonNull AlertDialog dialog,
            @NonNull TermuxColorPalette palette,
            int positiveButtonColor) {
        dialog.setOnShowListener(d -> styleAlertDialog(context, dialog, palette, positiveButtonColor));
        dialog.show();
        styleAlertDialog(context, dialog, palette, positiveButtonColor);
    }

    public static void styleAlertDialog(
            @NonNull Context context,
            @NonNull AlertDialog dialog,
            @NonNull TermuxColorPalette palette) {
        styleAlertDialog(context, dialog, palette, palette.accent);
    }

    public static void styleAlertDialog(
            @NonNull Context context,
            @NonNull AlertDialog dialog,
            @NonNull TermuxColorPalette palette,
            int positiveButtonColor) {
        float density = context.getResources().getDisplayMetrics().density;
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable windowBg = new GradientDrawable();
            windowBg.setShape(GradientDrawable.RECTANGLE);
            windowBg.setCornerRadius(24f * density);
            windowBg.setColor(palette.surfaceContainerHigh);
            window.setBackgroundDrawable(windowBg);
        }

        TextView titleView = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (titleView != null) {
            titleView.setTextColor(palette.onSurface);
        }

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(palette.onSurfaceVariant);
        }

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive != null) {
            positive.setTextColor(positiveButtonColor);
        }

        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (negative != null) {
            negative.setTextColor(palette.onSurfaceVariant);
        }

        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neutral != null) {
            neutral.setTextColor(palette.accent);
        }
    }
}
