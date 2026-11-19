package com.termux.shared.interact;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.shared.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemeManager;

public class MessageDialogUtils {

    /**
     * Show a message in a dialog
     *
     * @param context The {@link Context} to use to start the dialog. An {@link Activity} {@link Context}
     *                must be passed, otherwise exceptions will be thrown.
     * @param titleText The title text of the dialog.
     * @param messageText The message text of the dialog.
     * @param onDismiss The {@link DialogInterface.OnDismissListener} to run when dialog is dismissed.
     */
    public static void showMessage(Context context, String titleText, String messageText, final DialogInterface.OnDismissListener onDismiss) {
        showMessage(context, titleText, messageText, null, null, null, null, onDismiss);
    }

    /**
     * Show a message in a dialog
     *
     * @param context The {@link Context} to use to start the dialog. An {@link Activity} {@link Context}
     *                must be passed, otherwise exceptions will be thrown.
     * @param titleText The title text of the dialog.
     * @param messageText The message text of the dialog.
     * @param positiveText The positive button text of the dialog.
     * @param onPositiveButton The {@link DialogInterface.OnClickListener} to run when positive button
     *                         is pressed.
     * @param negativeText The negative button text of the dialog. If this is {@code null}, then
     *                         negative button will not be shown.
     * @param onNegativeButton The {@link DialogInterface.OnClickListener} to run when negative button
     *                         is pressed.
     * @param onDismiss The {@link DialogInterface.OnDismissListener} to run when dialog is dismissed.
     */
    public static void showMessage(Context context, String titleText, String messageText,
                                   String positiveText,
                                   final DialogInterface.OnClickListener onPositiveButton,
                                   String negativeText,
                                   final DialogInterface.OnClickListener onNegativeButton,
                                   final DialogInterface.OnDismissListener onDismiss) {

        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(context);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater != null ? inflater.inflate(R.layout.dialog_show_message, null) : null;
        if (view != null) {
            view.setBackgroundColor(palette.surfaceContainerHigh);
            builder.setView(view);

            TextView titleView = view.findViewById(R.id.dialog_title);
            if (titleView != null) {
                titleView.setText(titleText);
                titleView.setTextColor(palette.onSurface);
            }

            TextView messageView = view.findViewById(R.id.dialog_message);
            if (messageView != null) {
                messageView.setText(messageText);
                messageView.setTextColor(palette.onSurfaceVariant);
                messageView.setLinkTextColor(palette.accent);
            }
        }

        if (positiveText == null)
            positiveText = context.getString(android.R.string.ok);
        builder.setPositiveButton(positiveText, onPositiveButton);

        if (negativeText != null)
            builder.setNegativeButton(negativeText, onNegativeButton);

        if (onDismiss != null)
            builder.setOnDismissListener(onDismiss);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(dialogInterface -> {
            Logger.logVerbose("MessageDialogUtils", "Showing Material 3 message dialog");
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null)
                positiveButton.setTextColor(palette.accent);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null)
                negativeButton.setTextColor(palette.onSurfaceVariant);
        });

        dialog.show();
    }

    public static void exitAppWithErrorMessage(Context context, String titleText, String messageText) {
        showMessage(context, titleText, messageText, dialog -> System.exit(0));
    }

}
