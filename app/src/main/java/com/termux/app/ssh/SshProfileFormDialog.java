package com.termux.app.ssh;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.termux.theme.TermuxColorPalette;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * Material 3 Add/Edit SSH Profile dialog controller.
 * Enforces {@link SshProfileValidator} inline errors, defaults new profiles to {@code SSH_KEY}
 * and port {@code 22}, keeps saved secrets masked and preserved unless edited or cleared,
 * and reveals the collapsible Advanced section automatically when advanced fields are populated.
 */
final class SshProfileFormDialog {

    interface HostCallbacks {
        void onProfileSaved();
    }

    private final SshHostsActivity activity;
    private final SshHostsEnvironment environment;
    private final TermuxColorPalette palette;
    private final float density;
    @Nullable private final SshProfile existingProfile;
    private final HostCallbacks callbacks;

    private final List<String> spinnerKeyPaths = new ArrayList<>();
    private final List<String> spinnerKeyLabels = new ArrayList<>();

    private SshAuthType selectedAuthType;
    private String selectedKeyPath = "";
    private boolean advancedExpanded;
    private boolean secretVisible = false;
    private boolean hadExistingSecret = false;
    private boolean secretUnreadable = false;
    @Nullable private String initialLoadedSecret = null;
    private boolean secretDirty = false;
    private boolean secretCleared = false;
    private boolean secretUserModified = false;
    private boolean suppressSecretWatcher = false;

    private EditText displayNameInput;
    private EditText hostInput;
    private EditText portInput;
    private EditText usernameInput;
    private TextView hostErrorView;
    private TextView portErrorView;
    private TextView usernameErrorView;

    private MaterialButton authKeyButton;
    private MaterialButton authPasswordButton;
    private LinearLayout keySection;
    private Spinner keySpinner;
    private TextView keyPathText;
    private TextView keyErrorView;
    private MaterialButton importKeyButton;
    private MaterialButton generateKeyButton;
    private MaterialButton copyPubKeyButton;

    private TextView secretLabelView;
    private View secretRow;
    private EditText secretInput;
    private MaterialButton secretToggleButton;
    private MaterialButton secretClearButton;
    private TextView secretStatusView;

    private MaterialButton advancedToggleButton;
    private LinearLayout advancedContainer;
    private EditText startupCommandInput;
    private EditText proxyJumpInput;
    private EditText keepAliveInput;
    private EditText extraArgsInput;
    private TextView proxyJumpErrorView;
    private TextView keepAliveErrorView;
    private TextView extraArgsErrorView;

    private AlertDialog dialog;

    private SshProfileFormDialog(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette,
            @Nullable SshProfile existingProfile,
            @NonNull HostCallbacks callbacks) {
        this.activity = activity;
        this.environment = environment;
        this.palette = palette;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.existingProfile = existingProfile;
        this.callbacks = callbacks;
        this.selectedAuthType = existingProfile != null
            ? existingProfile.authType
            : SshProfile.DEFAULT_AUTH_TYPE;
        this.selectedKeyPath = existingProfile != null ? existingProfile.privateKeyPath : "";
        this.advancedExpanded = existingProfile != null && existingProfile.hasAdvancedValues();
    }

    @NonNull
    static SshProfileFormDialog show(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette,
            @Nullable SshProfile existingProfile,
            @NonNull HostCallbacks callbacks) {
        SshProfileFormDialog form = new SshProfileFormDialog(
            activity, environment, palette, existingProfile, callbacks);
        form.buildAndShow();
        return form;
    }

    private void buildAndShow() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ssh_profile_form, null, false);
        bindViews(view);
        applyTheme(view);
        populateInitialValues();
        wireControls();

        dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(R.string.ssh_form_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        SshDialogStyler.showStyledDialog(activity, dialog, palette);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> attemptSave());
    }

    private void bindViews(@NonNull View view) {
        TextView titleView = view.findViewById(R.id.ssh_form_title);
        titleView.setText(existingProfile == null ? R.string.ssh_form_add_title : R.string.ssh_form_edit_title);

        displayNameInput = view.findViewById(R.id.ssh_form_display_name);
        hostInput = view.findViewById(R.id.ssh_form_host);
        portInput = view.findViewById(R.id.ssh_form_port);
        usernameInput = view.findViewById(R.id.ssh_form_username);
        hostErrorView = view.findViewById(R.id.ssh_form_host_error);
        portErrorView = view.findViewById(R.id.ssh_form_port_error);
        usernameErrorView = view.findViewById(R.id.ssh_form_username_error);

        authKeyButton = view.findViewById(R.id.ssh_form_auth_key_button);
        authPasswordButton = view.findViewById(R.id.ssh_form_auth_password_button);
        keySection = view.findViewById(R.id.ssh_form_key_section);
        keySpinner = view.findViewById(R.id.ssh_form_key_spinner);
        keyPathText = view.findViewById(R.id.ssh_form_key_path_text);
        keyErrorView = view.findViewById(R.id.ssh_form_key_error);
        importKeyButton = view.findViewById(R.id.ssh_form_import_key_button);
        generateKeyButton = view.findViewById(R.id.ssh_form_generate_key_button);
        copyPubKeyButton = view.findViewById(R.id.ssh_form_copy_pub_key_button);

        secretLabelView = view.findViewById(R.id.ssh_form_secret_label);
        secretRow = view.findViewById(R.id.ssh_form_secret_row);
        secretInput = view.findViewById(R.id.ssh_form_secret_input);
        secretToggleButton = view.findViewById(R.id.ssh_form_secret_toggle_button);
        secretClearButton = view.findViewById(R.id.ssh_form_secret_clear_button);
        secretStatusView = view.findViewById(R.id.ssh_form_secret_status);

        advancedToggleButton = view.findViewById(R.id.ssh_form_advanced_toggle);
        advancedContainer = view.findViewById(R.id.ssh_form_advanced_container);
        startupCommandInput = view.findViewById(R.id.ssh_form_startup_command);
        proxyJumpInput = view.findViewById(R.id.ssh_form_proxy_jump);
        keepAliveInput = view.findViewById(R.id.ssh_form_keep_alive);
        extraArgsInput = view.findViewById(R.id.ssh_form_extra_args);
        proxyJumpErrorView = view.findViewById(R.id.ssh_form_proxy_jump_error);
        keepAliveErrorView = view.findViewById(R.id.ssh_form_keep_alive_error);
        extraArgsErrorView = view.findViewById(R.id.ssh_form_extra_args_error);
    }

    private void applyTheme(@NonNull View view) {
        TextView titleView = view.findViewById(R.id.ssh_form_title);
        titleView.setTextColor(palette.onSurface);

        int[] labelIds = new int[]{
            R.id.ssh_form_display_name_label,
            R.id.ssh_form_host_label,
            R.id.ssh_form_port_label,
            R.id.ssh_form_username_label,
            R.id.ssh_form_auth_label,
            R.id.ssh_form_key_label,
            R.id.ssh_form_secret_label,
            R.id.ssh_form_startup_command_label,
            R.id.ssh_form_proxy_jump_label,
            R.id.ssh_form_keep_alive_label,
            R.id.ssh_form_extra_args_label
        };
        for (int id : labelIds) {
            TextView tv = view.findViewById(id);
            if (tv != null) {
                tv.setTextColor(palette.onSurfaceVariant);
            }
        }

        styleInputBox(displayNameInput);
        styleInputBox(hostInput);
        styleInputBox(portInput);
        styleInputBox(usernameInput);
        styleInputBox(startupCommandInput);
        styleInputBox(proxyJumpInput);
        styleInputBox(keepAliveInput);
        styleInputBox(extraArgsInput);

        GradientDrawable secretBg = createInputBoxDrawable();
        secretRow.setBackground(secretBg);
        secretInput.setTextColor(palette.onSurface);
        secretInput.setHintTextColor(palette.onSurfaceVariant);

        GradientDrawable spinnerBg = createInputBoxDrawable();
        keySpinner.setBackground(spinnerBg);

        keyPathText.setTextColor(palette.onSurfaceVariant);
        secretStatusView.setTextColor(palette.onSurfaceVariant);

        TextView[] errorViews = new TextView[]{
            hostErrorView, portErrorView, usernameErrorView, keyErrorView,
            proxyJumpErrorView, keepAliveErrorView, extraArgsErrorView
        };
        for (TextView ev : errorViews) {
            ev.setTextColor(palette.error);
        }

        styleOutlinedSecondaryButton(importKeyButton);
        styleOutlinedSecondaryButton(generateKeyButton);
        copyPubKeyButton.setTextColor(palette.accent);
        secretToggleButton.setTextColor(palette.accent);
        secretClearButton.setTextColor(palette.error);
        advancedToggleButton.setTextColor(palette.accent);
    }

    private void styleInputBox(@NonNull EditText editText) {
        editText.setBackground(createInputBoxDrawable());
        editText.setTextColor(palette.onSurface);
        editText.setHintTextColor(palette.onSurfaceVariant);
    }

    @NonNull
    private GradientDrawable createInputBoxDrawable() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(12f * density);
        bg.setColor(palette.surfaceContainer);
        bg.setStroke(Math.max(1, Math.round(density)), palette.outline);
        return bg;
    }

    private void styleOutlinedSecondaryButton(@NonNull MaterialButton button) {
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeColor(ColorStateList.valueOf(palette.outline));
        button.setTextColor(palette.onSurface);
    }

    private void populateInitialValues() {
        if (existingProfile != null) {
            displayNameInput.setText(existingProfile.displayName);
            hostInput.setText(existingProfile.host);
            portInput.setText(String.valueOf(existingProfile.port));
            usernameInput.setText(existingProfile.username);
            startupCommandInput.setText(existingProfile.startupCommand);
            proxyJumpInput.setText(existingProfile.proxyJump);
            keepAliveInput.setText(
                existingProfile.keepAliveIntervalSeconds > 0
                    ? String.valueOf(existingProfile.keepAliveIntervalSeconds)
                    : ""
            );
            extraArgsInput.setText(existingProfile.extraArgs);

            hadExistingSecret = environment.repository.hasSecret(existingProfile.id);
            if (hadExistingSecret) {
                try {
                    String loadedSecret = environment.repository.getSecret(existingProfile.id);
                    if (loadedSecret != null && !loadedSecret.isEmpty()) {
                        initialLoadedSecret = loadedSecret;
                        suppressSecretWatcher = true;
                        secretInput.setText(loadedSecret);
                        suppressSecretWatcher = false;
                        secretStatusView.setText(R.string.ssh_form_secret_kept_status);
                        secretStatusView.setVisibility(View.VISIBLE);
                    } else {
                        hadExistingSecret = false;
                    }
                } catch (GeneralSecurityException | IOException e) {
                    secretUnreadable = true;
                    secretStatusView.setText(R.string.ssh_form_secret_unreadable_status);
                    secretStatusView.setVisibility(View.VISIBLE);
                }
            }
        } else {
            portInput.setText(String.valueOf(SshProfile.DEFAULT_PORT));
        }

        applySecretMaskState();
        updateSecretClearVisibility();
        refreshKeyPicker(selectedKeyPath);
        updateAuthUi();
        updateAdvancedVisibility();
    }

    private void wireControls() {
        authKeyButton.setOnClickListener(v -> switchAuthType(SshAuthType.SSH_KEY));
        authPasswordButton.setOnClickListener(v -> switchAuthType(SshAuthType.PASSWORD));

        secretToggleButton.setOnClickListener(v -> {
            secretVisible = !secretVisible;
            applySecretMaskState();
        });

        secretClearButton.setOnClickListener(v -> {
            secretCleared = true;
            secretDirty = true;
            secretUserModified = true;
            suppressSecretWatcher = true;
            secretInput.setText("");
            suppressSecretWatcher = false;
            if (hadExistingSecret) {
                secretStatusView.setText(R.string.ssh_form_secret_cleared_status);
                secretStatusView.setVisibility(View.VISIBLE);
            } else {
                secretStatusView.setVisibility(View.GONE);
            }
            updateSecretClearVisibility();
        });

        secretInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSecretWatcher) {
                    return;
                }
                secretDirty = true;
                secretCleared = false;
                secretUserModified = true;
                if (hadExistingSecret && (s == null || s.length() == 0)) {
                    secretStatusView.setText(R.string.ssh_form_secret_cleared_status);
                    secretStatusView.setVisibility(View.VISIBLE);
                } else {
                    secretStatusView.setVisibility(View.GONE);
                }
                updateSecretClearVisibility();
            }
        });

        advancedToggleButton.setOnClickListener(v -> {
            advancedExpanded = !advancedExpanded;
            updateAdvancedVisibility();
        });

        keySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < spinnerKeyPaths.size()) {
                    selectedKeyPath = spinnerKeyPaths.get(position);
                    updateKeyMetadataUi();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        importKeyButton.setOnClickListener(v ->
            activity.launchSafKeyImport(importedFile -> {
                if (activity.isFinishing() || activity.isDestroyed() || dialog == null || !dialog.isShowing()) {
                    return;
                }
                refreshKeyPicker(importedFile.getAbsolutePath());
            }));

        generateKeyButton.setOnClickListener(v ->
            SshKeyToolsHelper.startEd25519KeyGeneration(
                activity,
                environment,
                palette,
                result -> {
                    if (activity.isFinishing() || activity.isDestroyed() || dialog == null || !dialog.isShowing()) {
                        return;
                    }
                    String path = result.getPrivateKeyPath();
                    if (path != null) {
                        refreshKeyPicker(path);
                    }
                }
            ));
    }

    private void switchAuthType(@NonNull SshAuthType target) {
        if (selectedAuthType == target) {
            return;
        }
        selectedAuthType = target;
        if (existingProfile != null && hadExistingSecret && !secretUserModified) {
            if (target == existingProfile.authType) {
                suppressSecretWatcher = true;
                secretInput.setText(initialLoadedSecret != null ? initialLoadedSecret : "");
                suppressSecretWatcher = false;
                secretDirty = false;
                secretCleared = false;
                secretVisible = false;
                applySecretMaskState();
                secretStatusView.setText(
                    secretUnreadable
                        ? R.string.ssh_form_secret_unreadable_status
                        : R.string.ssh_form_secret_kept_status
                );
                secretStatusView.setVisibility(View.VISIBLE);
            } else {
                suppressSecretWatcher = true;
                secretInput.setText("");
                suppressSecretWatcher = false;
                secretDirty = true;
                secretCleared = false;
                secretStatusView.setVisibility(View.GONE);
            }
        }
        updateAuthUi();
        updateSecretClearVisibility();
    }

    private void updateAuthUi() {
        boolean isKey = selectedAuthType == SshAuthType.SSH_KEY;
        styleAuthToggleButton(authKeyButton, isKey);
        styleAuthToggleButton(authPasswordButton, !isKey);
        keySection.setVisibility(isKey ? View.VISIBLE : View.GONE);
        secretLabelView.setText(isKey ? R.string.ssh_form_passphrase_label : R.string.ssh_form_password_label);
        secretInput.setHint(isKey ? R.string.ssh_form_passphrase_hint : R.string.ssh_form_password_hint);
    }

    private void styleAuthToggleButton(@NonNull MaterialButton button, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            button.setStrokeColor(ColorStateList.valueOf(palette.accent));
            button.setTextColor(palette.onAccent);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            button.setStrokeColor(ColorStateList.valueOf(palette.outline));
            button.setTextColor(palette.onSurface);
        }
    }

    private void applySecretMaskState() {
        int cursor = secretInput.getSelectionEnd();
        if (secretVisible) {
            secretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            secretInput.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            secretToggleButton.setText(R.string.ssh_form_secret_hide);
        } else {
            secretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            secretInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            secretToggleButton.setText(R.string.ssh_form_secret_show);
        }
        int len = secretInput.getText() != null ? secretInput.getText().length() : 0;
        if (cursor >= 0 && cursor <= len) {
            secretInput.setSelection(cursor);
        }
    }

    private void updateSecretClearVisibility() {
        boolean hasText = secretInput.getText() != null && secretInput.getText().length() > 0;
        boolean hasOriginalSecretForActiveAuth = existingProfile != null
            && hadExistingSecret
            && !secretCleared
            && selectedAuthType == existingProfile.authType;
        secretClearButton.setVisibility((hasText || hasOriginalSecretForActiveAuth) ? View.VISIBLE : View.GONE);
    }

    private void updateAdvancedVisibility() {
        advancedContainer.setVisibility(advancedExpanded ? View.VISIBLE : View.GONE);
        advancedToggleButton.setText(
            advancedExpanded ? R.string.ssh_form_advanced_hide : R.string.ssh_form_advanced_show
        );
    }

    void refreshKeyPicker(@Nullable String preferredKeyPath) {
        spinnerKeyPaths.clear();
        spinnerKeyLabels.clear();

        spinnerKeyPaths.add("");
        spinnerKeyLabels.add(activity.getString(R.string.ssh_form_key_default_option));

        List<File> keys = SshKeyManager.scanPrivateKeys(environment.getSshDir());
        for (File key : keys) {
            spinnerKeyPaths.add(key.getAbsolutePath());
            spinnerKeyLabels.add(key.getName());
        }

        String targetPath = preferredKeyPath != null ? preferredKeyPath.trim() : "";
        if (!targetPath.isEmpty() && !spinnerKeyPaths.contains(targetPath)) {
            spinnerKeyPaths.add(targetPath);
            spinnerKeyLabels.add(new File(targetPath).getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                activity, android.R.layout.simple_spinner_item, spinnerKeyLabels) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(palette.onSurface);
                }
                return v;
            }

            @NonNull
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(palette.onSurface);
                    v.setBackgroundColor(palette.surfaceContainerHigh);
                }
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keySpinner.setAdapter(adapter);

        int idx = Math.max(0, spinnerKeyPaths.indexOf(targetPath));
        selectedKeyPath = spinnerKeyPaths.get(idx);
        keySpinner.setSelection(idx);
        updateKeyMetadataUi();
    }

    private void updateKeyMetadataUi() {
        if (selectedKeyPath == null || selectedKeyPath.isEmpty()) {
            keyPathText.setVisibility(View.GONE);
            copyPubKeyButton.setVisibility(View.GONE);
            copyPubKeyButton.setOnClickListener(null);
            return;
        }
        keyPathText.setText(selectedKeyPath);
        keyPathText.setVisibility(View.VISIBLE);

        String pubContent = SshKeyToolsHelper.readPublicKeyQuietly(new File(selectedKeyPath));
        if (pubContent != null && !pubContent.isEmpty()) {
            copyPubKeyButton.setVisibility(View.VISIBLE);
            copyPubKeyButton.setOnClickListener(v ->
                SshKeyToolsHelper.copyPublicKeyToClipboard(activity, pubContent));
        } else {
            copyPubKeyButton.setVisibility(View.GONE);
            copyPubKeyButton.setOnClickListener(null);
        }
    }

    private void attemptSave() {
        clearAllErrors();

        String rawPort = portInput.getText() != null ? portInput.getText().toString().trim() : "";
        int parsedPort;
        if (rawPort.isEmpty()) {
            parsedPort = 0;
        } else {
            try {
                parsedPort = Integer.parseInt(rawPort);
            } catch (NumberFormatException e) {
                parsedPort = -1;
            }
        }

        String rawKeepAlive = keepAliveInput.getText() != null ? keepAliveInput.getText().toString().trim() : "";
        int parsedKeepAlive = 0;
        boolean invalidKeepAliveFormat = false;
        if (!rawKeepAlive.isEmpty()) {
            try {
                parsedKeepAlive = Integer.parseInt(rawKeepAlive);
            } catch (NumberFormatException e) {
                parsedKeepAlive = -1;
                invalidKeepAliveFormat = true;
            }
        }

        String extraArgs = extraArgsInput.getText() != null ? extraArgsInput.getText().toString().trim() : "";
        String extraArgsError = null;
        try {
            SshCommandBuilder.parseExtraArgs(extraArgs);
        } catch (IllegalArgumentException e) {
            extraArgsError = e.getMessage();
        }

        SshProfile.Builder builder = existingProfile != null ? existingProfile.toBuilder() : SshProfile.builder();
        SshProfile candidate = builder
            .displayName(displayNameInput.getText() != null ? displayNameInput.getText().toString() : "")
            .host(hostInput.getText() != null ? hostInput.getText().toString() : "")
            .port(parsedPort)
            .username(usernameInput.getText() != null ? usernameInput.getText().toString() : "")
            .authType(selectedAuthType)
            .privateKeyPath(selectedAuthType == SshAuthType.SSH_KEY ? selectedKeyPath : "")
            .startupCommand(startupCommandInput.getText() != null ? startupCommandInput.getText().toString() : "")
            .proxyJump(proxyJumpInput.getText() != null ? proxyJumpInput.getText().toString() : "")
            .keepAliveIntervalSeconds(parsedKeepAlive)
            .extraArgs(extraArgs)
            .build();

        SshValidationResult validation = SshProfileValidator.validate(candidate);
        if (!validation.isValid() || extraArgsError != null) {
            showValidationErrors(validation, invalidKeepAliveFormat, extraArgsError);
            return;
        }

        String enteredSecret = secretInput.getText() != null ? secretInput.getText().toString() : "";
        try {
            if (enteredSecret.isEmpty()) {
                boolean authTypeChanged = existingProfile != null && selectedAuthType != existingProfile.authType;
                if (existingProfile != null && hadExistingSecret && (secretDirty || secretCleared || authTypeChanged)) {
                    environment.repository.saveProfileWithSecret(candidate, null);
                } else {
                    environment.repository.saveProfile(candidate);
                }
            } else {
                if (existingProfile != null && hadExistingSecret && !secretDirty && !secretCleared) {
                    environment.repository.saveProfile(candidate);
                } else {
                    environment.repository.saveProfileWithSecret(candidate, enteredSecret);
                }
            }
            dialog.dismiss();
            callbacks.onProfileSaved();
        } catch (GeneralSecurityException | IOException e) {
            Toast.makeText(
                activity,
                activity.getString(R.string.ssh_action_failed, e.getMessage()),
                Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void clearAllErrors() {
        TextView[] errorViews = new TextView[]{
            hostErrorView, portErrorView, usernameErrorView, keyErrorView,
            proxyJumpErrorView, keepAliveErrorView, extraArgsErrorView
        };
        for (TextView ev : errorViews) {
            ev.setText("");
            ev.setVisibility(View.GONE);
        }
    }

    private void showValidationErrors(
            @NonNull SshValidationResult validation,
            boolean invalidKeepAliveFormat,
            @Nullable String extraArgsError) {
        bindFieldError(hostErrorView, validation.getFieldError(SshValidationResult.Field.HOST));
        bindFieldError(usernameErrorView, validation.getFieldError(SshValidationResult.Field.USERNAME));
        bindFieldError(portErrorView, validation.getFieldError(SshValidationResult.Field.PORT));
        bindFieldError(keyErrorView, validation.getFieldError(SshValidationResult.Field.PRIVATE_KEY_PATH));

        String proxyErr = validation.getFieldError(SshValidationResult.Field.PROXY_JUMP);
        String keepAliveErr = invalidKeepAliveFormat
            ? activity.getString(R.string.ssh_form_error_keep_alive_invalid)
            : validation.getFieldError(SshValidationResult.Field.KEEP_ALIVE_INTERVAL);
        bindFieldError(proxyJumpErrorView, proxyErr);
        bindFieldError(keepAliveErrorView, keepAliveErr);
        bindFieldError(extraArgsErrorView, extraArgsError);

        if ((proxyErr != null || keepAliveErr != null || extraArgsError != null) && !advancedExpanded) {
            advancedExpanded = true;
            updateAdvancedVisibility();
        }
    }

    private static void bindFieldError(@NonNull TextView errorView, @Nullable String message) {
        if (message != null && !message.trim().isEmpty()) {
            errorView.setText(message);
            errorView.setVisibility(View.VISIBLE);
        } else {
            errorView.setVisibility(View.GONE);
        }
    }
}
