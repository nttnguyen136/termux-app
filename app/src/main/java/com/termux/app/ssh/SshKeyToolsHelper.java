package com.termux.app.ssh;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.theme.TermuxColorPalette;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Coordinates SSH Key Tools UI: listing {@code ~/.ssh} keys, copying {@code .pub} keys to the
 * clipboard, generating Ed25519 key pairs, and prompting to install {@code openssh} if missing.
 */
final class SshKeyToolsHelper {

    @FunctionalInterface
    interface KeyGeneratedCallback {
        void onKeyGenerated(@NonNull SshKeyGenerationResult result);
    }

    private SshKeyToolsHelper() {
    }

    static void showKeyToolsDialog(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette) {
        float density = activity.getResources().getDisplayMetrics().density;
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ssh_key_tools, null, false);

        TextView titleView = view.findViewById(R.id.ssh_key_tools_title);
        titleView.setTextColor(palette.onSurface);

        MaterialButton importBtn = view.findViewById(R.id.ssh_key_tools_import_button);
        importBtn.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        importBtn.setStrokeColor(ColorStateList.valueOf(palette.outline));
        importBtn.setTextColor(palette.onSurface);

        MaterialButton generateBtn = view.findViewById(R.id.ssh_key_tools_generate_button);
        generateBtn.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        generateBtn.setTextColor(palette.onAccent);

        TextView emptyView = view.findViewById(R.id.ssh_key_tools_empty);
        emptyView.setTextColor(palette.onSurfaceVariant);
        LinearLayout listContainer = view.findViewById(R.id.ssh_key_tools_list);

        Runnable refreshList = () -> populateKeyToolsList(
            activity, environment, palette, density, listContainer, emptyView);
        refreshList.run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setNegativeButton(R.string.ssh_key_tools_close, null)
            .create();
        importBtn.setOnClickListener(v -> activity.launchSafKeyImport(imported -> {
            if (!activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing()) {
                refreshList.run();
            }
        }));
        generateBtn.setOnClickListener(v ->
            startEd25519KeyGeneration(activity, environment, palette, result -> {
                if (!activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing()) {
                    refreshList.run();
                }
            }));
        SshDialogStyler.showStyledDialog(activity, dialog, palette);
    }

    private static void populateKeyToolsList(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette,
            float density,
            @NonNull LinearLayout listContainer,
            @NonNull TextView emptyView) {
        listContainer.removeAllViews();
        List<File> keys = SshKeyManager.scanPrivateKeys(environment.getSshDir());
        if (keys.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            listContainer.setVisibility(View.GONE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        listContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (File keyFile : keys) {
            View row = inflater.inflate(R.layout.item_ssh_key_tool, listContainer, false);
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setShape(GradientDrawable.RECTANGLE);
            rowBg.setCornerRadius(12f * density);
            rowBg.setColor(palette.surfaceContainer);
            rowBg.setStroke(Math.max(1, Math.round(density)), palette.outline);
            row.setBackground(rowBg);

            TextView nameView = row.findViewById(R.id.ssh_key_item_name);
            nameView.setText(keyFile.getName());
            nameView.setTextColor(palette.onSurface);

            TextView pathView = row.findViewById(R.id.ssh_key_item_path);
            pathView.setText(keyFile.getAbsolutePath());
            pathView.setTextColor(palette.onSurfaceVariant);

            MaterialButton copyPubBtn = row.findViewById(R.id.ssh_key_item_copy_pub);
            String pubContent = readPublicKeyQuietly(keyFile);
            if (pubContent != null && !pubContent.isEmpty()) {
                copyPubBtn.setVisibility(View.VISIBLE);
                copyPubBtn.setTextColor(palette.accent);
                copyPubBtn.setOnClickListener(v -> copyPublicKeyToClipboard(activity, pubContent));
            } else {
                copyPubBtn.setVisibility(View.GONE);
            }

            listContainer.addView(row);
        }
    }

    static void startEd25519KeyGeneration(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette,
            @NonNull KeyGeneratedCallback onSuccess) {
        if (!SshKeyManager.isSshKeygenAvailable(environment.prefixDir)) {
            showMissingOpenSshForKeygenDialog(activity, environment, palette);
            return;
        }
        showGenerateEd25519Dialog(activity, environment, palette, onSuccess);
    }

    static void showMissingOpenSshForKeygenDialog(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ssh_keys_generate_missing_openssh_title)
            .setMessage(R.string.ssh_keys_generate_missing_openssh_message)
            .setPositiveButton(R.string.ssh_keys_install_openssh_confirm, (d, which) -> {
                SshSessionLaunchRequest request = new SshSessionLaunchRequest(
                    SshCommandBuilder.getShBinary(environment.prefixDir).getAbsolutePath(),
                    Arrays.asList("-c", "pkg install -y openssh"),
                    environment.homeDir.getAbsolutePath(),
                    activity.getString(R.string.ssh_keys_install_openssh_session_title),
                    Collections.emptyMap()
                );
                environment.launchGateway.launchSession(request);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        SshDialogStyler.showStyledDialog(activity, dialog, palette);
    }

    private static void showGenerateEd25519Dialog(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette,
            @NonNull KeyGeneratedCallback onSuccess) {
        float density = activity.getResources().getDisplayMetrics().density;
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ssh_generate_key, null, false);

        ((TextView) view.findViewById(R.id.ssh_keygen_dialog_title)).setTextColor(palette.onSurface);
        ((TextView) view.findViewById(R.id.ssh_keygen_name_label)).setTextColor(palette.onSurfaceVariant);
        ((TextView) view.findViewById(R.id.ssh_keygen_passphrase_label)).setTextColor(palette.onSurfaceVariant);
        ((TextView) view.findViewById(R.id.ssh_keygen_comment_label)).setTextColor(palette.onSurfaceVariant);

        EditText nameInput = view.findViewById(R.id.ssh_keygen_name_input);
        EditText passphraseInput = view.findViewById(R.id.ssh_keygen_passphrase_input);
        EditText commentInput = view.findViewById(R.id.ssh_keygen_comment_input);

        styleEditText(nameInput, palette, density);
        styleEditText(passphraseInput, palette, density);
        styleEditText(commentInput, palette, density);
        nameInput.setText("id_ed25519");

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(R.string.ssh_keygen_generate_cta, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        SshDialogStyler.showStyledDialog(activity, dialog, palette);

        Button generateButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        generateButton.setOnClickListener(v -> {
            String keyName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
            String passphrase = passphraseInput.getText() != null ? passphraseInput.getText().toString() : "";
            String comment = commentInput.getText() != null ? commentInput.getText().toString().trim() : "";

            setKeygenDialogEnabled(dialog, generateButton, cancelButton, nameInput, passphraseInput, commentInput, false);

            environment.keygenExecutor.execute(() -> {
                SshKeyGenerationResult result = SshKeyManager.generateEd25519Key(
                    environment.prefixDir,
                    environment.homeDir,
                    keyName,
                    passphrase,
                    comment,
                    environment.keygenRunner
                );
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    setKeygenDialogEnabled(dialog, generateButton, cancelButton, nameInput, passphraseInput, commentInput, true);
                    if (result.isMissingOpenssh()) {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        showMissingOpenSshForKeygenDialog(activity, environment, palette);
                        return;
                    }
                    if (!result.isSuccess()) {
                        String error = result.getErrorMessage() != null
                            ? result.getErrorMessage()
                            : activity.getString(R.string.ssh_keygen_unknown_error);
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.ssh_keys_generate_failed, error),
                            Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    if (result.getPrivateKeyFile() != null) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.ssh_keys_generate_success, result.getPrivateKeyFile().getName()),
                            Toast.LENGTH_SHORT
                        ).show();
                    }
                    onSuccess.onKeyGenerated(result);
                });
            });
        });
    }

    private static void setKeygenDialogEnabled(
            @NonNull AlertDialog dialog,
            @Nullable Button generateButton,
            @Nullable Button cancelButton,
            @NonNull EditText nameInput,
            @NonNull EditText passphraseInput,
            @NonNull EditText commentInput,
            boolean enabled) {
        dialog.setCancelable(enabled);
        if (generateButton != null) {
            generateButton.setEnabled(enabled);
        }
        if (cancelButton != null) {
            cancelButton.setEnabled(enabled);
        }
        nameInput.setEnabled(enabled);
        passphraseInput.setEnabled(enabled);
        commentInput.setEnabled(enabled);
    }

    @Nullable
    static String readPublicKeyQuietly(@NonNull File privateKeyFile) {
        try {
            return SshKeyManager.readPublicKey(privateKeyFile);
        } catch (IOException ignored) {
            return null;
        }
    }

    static void copyPublicKeyToClipboard(@NonNull SshHostsActivity activity, @NonNull String publicKey) {
        ShareUtils.copyTextToClipboard(
            activity,
            activity.getString(R.string.ssh_keys_public_key_clip_label),
            publicKey,
            activity.getString(R.string.ssh_keys_public_key_copied)
        );
    }

    private static void styleEditText(
            @NonNull EditText editText,
            @NonNull TermuxColorPalette palette,
            float density) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(12f * density);
        bg.setColor(palette.surfaceContainer);
        bg.setStroke(Math.max(1, Math.round(density)), palette.outline);
        editText.setBackground(bg);
        editText.setTextColor(palette.onSurface);
        editText.setHintTextColor(palette.onSurfaceVariant);
    }
}
