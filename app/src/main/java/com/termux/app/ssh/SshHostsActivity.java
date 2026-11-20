package com.termux.app.ssh;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.shared.theme.NightMode;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Dedicated Material 3 screen for managing, searching, creating, editing, duplicating,
 * deleting, importing, and launching saved SSH accounts.
 */
public class SshHostsActivity extends AppCompatActivity {

    private static final String LOG_TAG = "SshHostsActivity";

    @FunctionalInterface
    interface EnvironmentFactory {
        @NonNull
        SshHostsEnvironment create(@NonNull SshHostsActivity activity);
    }

    @Nullable
    private static volatile EnvironmentFactory sTestEnvironmentFactory;

    @FunctionalInterface
    interface SafImportCallback {
        void onKeyImported(@NonNull File importedKeyFile);
    }

    private SshHostsEnvironment environment;
    private SshConnectionLauncher connectionLauncher;
    private TermuxColorPalette activePalette;
    @NonNull private List<SshProfile> allProfiles = Collections.emptyList();
    @NonNull private String currentQuery = "";
    private ActivityResultLauncher<String[]> safOpenDocumentLauncher;
    @Nullable private SafImportCallback pendingSafImportCallback;

    private View rootView;
    private View searchContainer;
    private EditText searchInput;
    private ImageButton searchClearButton;
    private MaterialButton importConfigButton;
    private MaterialButton keyToolsButton;
    private MaterialButton addHostButton;
    private LinearLayout hostsListContainer;
    private LinearLayout emptyStateContainer;
    private TextView emptyTitleView;
    private TextView emptyMessageView;
    private MaterialButton emptyPrimaryCta;
    private MaterialButton emptySecondaryCta;

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, SshHostsActivity.class);
    }

    @VisibleForTesting
    static void setEnvironmentFactoryForTests(@Nullable EnvironmentFactory factory) {
        sTestEnvironmentFactory = factory;
    }

    @NonNull
    protected SshHostsEnvironment createEnvironment() {
        EnvironmentFactory factory = sTestEnvironmentFactory;
        if (factory != null) {
            return factory.create(this);
        }
        return SshHostsEnvironment.createDefault(this);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(this);
        TermuxThemeManager.syncAppNightMode(palette);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh_hosts);

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        AppCompatActivityUtils.setToolbarTitle(
            this,
            com.termux.shared.R.id.toolbar,
            getString(R.string.title_activity_ssh_hosts),
            0
        );

        environment = createEnvironment();
        connectionLauncher = new SshConnectionLauncher(
            this,
            environment.repository,
            environment.prefixDir,
            environment.homeDir,
            environment.tmpDir,
            environment.launchGateway,
            environment.clock
        );
        safOpenDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::handleSafKeyPicked
        );
        bindViews();
        wireControls();
        applyPalette(palette);
        reloadProfiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(this);
        TermuxThemeManager.syncAppNightMode(palette);
        applyPalette(palette);
        reloadProfiles();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void bindViews() {
        rootView = findViewById(R.id.ssh_hosts_root);
        searchContainer = findViewById(R.id.ssh_search_container);
        searchInput = findViewById(R.id.ssh_search_input);
        searchClearButton = findViewById(R.id.ssh_search_clear_button);
        importConfigButton = findViewById(R.id.ssh_action_import_config);
        keyToolsButton = findViewById(R.id.ssh_action_key_tools);
        addHostButton = findViewById(R.id.ssh_add_host_button);
        hostsListContainer = findViewById(R.id.ssh_hosts_list_container);
        emptyStateContainer = findViewById(R.id.ssh_empty_state_container);
        emptyTitleView = findViewById(R.id.ssh_empty_title);
        emptyMessageView = findViewById(R.id.ssh_empty_message);
        emptyPrimaryCta = findViewById(R.id.ssh_empty_primary_cta);
        emptySecondaryCta = findViewById(R.id.ssh_empty_secondary_cta);
    }

    private void wireControls() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s != null ? s.toString() : "";
                updateSearchClearButtonVisibility();
                renderProfiles();
            }
        });

        searchClearButton.setOnClickListener(v -> searchInput.setText(""));
        addHostButton.setOnClickListener(v -> openProfileForm(null));
        importConfigButton.setOnClickListener(v -> openConfigImportDialog());
        keyToolsButton.setOnClickListener(v ->
            SshKeyToolsHelper.showKeyToolsDialog(this, environment, getCurrentPalette()));
    }

    private void openConfigImportDialog() {
        SshConfigImportDialog.show(
            this,
            environment,
            getCurrentPalette(),
            allProfiles,
            this::reloadProfiles
        );
    }

    void launchSafKeyImport(@NonNull SafImportCallback callback) {
        pendingSafImportCallback = callback;
        safOpenDocumentLauncher.launch(new String[]{"*/*"});
    }

    private void handleSafKeyPicked(@Nullable Uri uri) {
        SafImportCallback callback = pendingSafImportCallback;
        pendingSafImportCallback = null;
        if (uri == null) {
            return;
        }
        try {
            File imported = SshKeyManager.importPrivateKey(this, uri, environment.getSshDir());
            Toast.makeText(
                this,
                getString(R.string.ssh_keys_import_success, imported.getName()),
                Toast.LENGTH_SHORT
            ).show();
            if (callback != null) {
                callback.onKeyImported(imported);
            }
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to import SSH private key via SAF", e);
            String message = e.getMessage() != null && e.getMessage().contains("empty")
                ? getString(R.string.ssh_keys_import_error_empty)
                : getString(R.string.ssh_keys_import_error_unreadable);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void openProfileForm(@Nullable SshProfile profileToEdit) {
        SshProfileFormDialog.show(
            this,
            environment,
            getCurrentPalette(),
            profileToEdit,
            this::reloadProfiles
        );
    }

    private void updateSearchClearButtonVisibility() {
        if (searchClearButton != null) {
            searchClearButton.setVisibility(currentQuery.trim().isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void reloadProfiles() {
        try {
            allProfiles = environment.repository.getAllSorted();
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load SSH profiles", e);
            allProfiles = Collections.emptyList();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        renderProfiles();
    }

    private void renderProfiles() {
        if (hostsListContainer == null || emptyStateContainer == null) {
            return;
        }
        TermuxColorPalette palette = activePalette != null
            ? activePalette
            : TermuxThemeManager.getActivePalette(this);
        float density = getResources().getDisplayMetrics().density;

        List<SshProfile> filtered = SshProfileRepository.filterProfiles(allProfiles, currentQuery);
        hostsListContainer.removeAllViews();

        if (filtered.isEmpty()) {
            hostsListContainer.setVisibility(View.GONE);
            emptyStateContainer.setVisibility(View.VISIBLE);
            boolean hasQuery = !currentQuery.trim().isEmpty();
            if (hasQuery && !allProfiles.isEmpty()) {
                emptyTitleView.setText(R.string.ssh_empty_search_title);
                emptyMessageView.setText(R.string.ssh_empty_search_message);
                emptyPrimaryCta.setText(R.string.ssh_empty_clear_search_cta);
                emptyPrimaryCta.setOnClickListener(v -> searchInput.setText(""));
                emptySecondaryCta.setVisibility(View.GONE);
            } else {
                emptyTitleView.setText(R.string.ssh_empty_title);
                emptyMessageView.setText(R.string.ssh_empty_message);
                emptyPrimaryCta.setText(R.string.ssh_empty_add_cta);
                emptyPrimaryCta.setOnClickListener(v -> openProfileForm(null));
                emptySecondaryCta.setVisibility(View.VISIBLE);
                emptySecondaryCta.setOnClickListener(v -> openConfigImportDialog());
            }
            return;
        }

        emptyStateContainer.setVisibility(View.GONE);
        hostsListContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SshProfile profile : filtered) {
            View card = inflater.inflate(R.layout.item_ssh_host_card, hostsListContainer, false);
            bindHostCard(card, profile, palette, density);
            hostsListContainer.addView(card);
        }
    }

    private void bindHostCard(
            @NonNull View card,
            @NonNull SshProfile profile,
            @NonNull TermuxColorPalette palette,
            float density) {
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(14f * density);
        cardBg.setColor(palette.surfaceContainer);
        cardBg.setStroke(
            Math.max(1, Math.round((profile.pinned ? 1.5f : 1f) * density)),
            profile.pinned ? palette.accent : palette.outline
        );
        card.setBackground(cardBg);

        TextView displayNameView = card.findViewById(R.id.ssh_host_display_name);
        TextView endpointView = card.findViewById(R.id.ssh_host_endpoint);
        TextView pinnedBadgeView = card.findViewById(R.id.ssh_host_pinned_badge);
        TextView authBadgeView = card.findViewById(R.id.ssh_host_auth_badge);
        TextView metaSummaryView = card.findViewById(R.id.ssh_host_meta_summary);
        View divider = card.findViewById(R.id.ssh_host_card_divider);

        MaterialButton pinButton = card.findViewById(R.id.ssh_host_pin_button);
        MaterialButton editButton = card.findViewById(R.id.ssh_host_edit_button);
        MaterialButton duplicateButton = card.findViewById(R.id.ssh_host_duplicate_button);
        MaterialButton deleteButton = card.findViewById(R.id.ssh_host_delete_button);

        displayNameView.setText(profile.getEffectiveDisplayName());
        displayNameView.setTextColor(palette.onSurface);

        endpointView.setText(formatEndpoint(profile));
        endpointView.setTextColor(palette.onSurfaceVariant);

        if (profile.pinned) {
            GradientDrawable pinnedBg = new GradientDrawable();
            pinnedBg.setShape(GradientDrawable.RECTANGLE);
            pinnedBg.setCornerRadius(10f * density);
            pinnedBg.setColor(palette.accent);
            pinnedBadgeView.setBackground(pinnedBg);
            pinnedBadgeView.setTextColor(palette.onAccent);
            pinnedBadgeView.setVisibility(View.VISIBLE);
        } else {
            pinnedBadgeView.setVisibility(View.GONE);
        }

        GradientDrawable authBg = new GradientDrawable();
        authBg.setShape(GradientDrawable.RECTANGLE);
        authBg.setCornerRadius(10f * density);
        authBg.setColor(palette.surfaceContainerHigh);
        authBg.setStroke(Math.max(1, Math.round(0.75f * density)), palette.outline);
        authBadgeView.setBackground(authBg);
        authBadgeView.setTextColor(palette.onSurfaceVariant);
        authBadgeView.setText(
            profile.authType == SshAuthType.PASSWORD
                ? R.string.ssh_auth_type_password
                : R.string.ssh_auth_type_key
        );

        String metaSummary = buildMetaSummary(profile);
        if (metaSummary.isEmpty()) {
            metaSummaryView.setVisibility(View.GONE);
        } else {
            metaSummaryView.setText(metaSummary);
            metaSummaryView.setTextColor(palette.onSurfaceVariant);
            metaSummaryView.setVisibility(View.VISIBLE);
        }

        divider.setBackgroundColor(palette.outline);
        divider.setAlpha(0.25f);

        pinButton.setText(profile.pinned ? R.string.ssh_action_unpin : R.string.ssh_action_pin);
        pinButton.setTextColor(profile.pinned ? palette.accent : palette.onSurfaceVariant);
        pinButton.setOnClickListener(v -> togglePin(profile));

        editButton.setTextColor(palette.onSurface);
        editButton.setOnClickListener(v -> openProfileForm(profile));
        duplicateButton.setTextColor(palette.onSurfaceVariant);
        duplicateButton.setOnClickListener(v -> duplicateProfile(profile));

        deleteButton.setTextColor(palette.error);
        deleteButton.setOnClickListener(v -> confirmDeleteProfile(profile));

        card.setOnClickListener(v -> connectToProfile(profile));
    }

    private void connectToProfile(@NonNull SshProfile profile) {
        connectionLauncher.connect(profile, outcome -> {
            if (outcome.status == SshConnectionLauncher.LaunchStatus.LAUNCHED) {
                finish();
            } else if (outcome.status == SshConnectionLauncher.LaunchStatus.LAUNCH_FAILED) {
                Toast.makeText(this, R.string.ssh_launch_error_session, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void togglePin(@NonNull SshProfile profile) {
        try {
            environment.repository.setPinned(profile.id, !profile.pinned);
            reloadProfiles();
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to update pin state", e);
            Toast.makeText(this, getString(R.string.ssh_action_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void duplicateProfile(@NonNull SshProfile profile) {
        try {
            environment.repository.duplicateProfile(profile.id);
            reloadProfiles();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to duplicate SSH profile", e);
            Toast.makeText(this, getString(R.string.ssh_action_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteProfile(@NonNull SshProfile profile) {
        TermuxColorPalette palette = getCurrentPalette();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ssh_delete_confirm_title)
            .setMessage(getString(R.string.ssh_delete_confirm_message, profile.getEffectiveDisplayName()))
            .setPositiveButton(R.string.ssh_action_delete, (d, which) -> {
                try {
                    environment.repository.deleteProfile(profile.id);
                    reloadProfiles();
                } catch (IOException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to delete SSH profile", e);
                    Toast.makeText(this, getString(R.string.ssh_action_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.setOnShowListener(d -> styleAlertDialog(dialog, palette, palette.error));
        dialog.show();
        styleAlertDialog(dialog, palette, palette.error);
    }

    @NonNull
    TermuxColorPalette getCurrentPalette() {
        return activePalette != null ? activePalette : TermuxThemeManager.getActivePalette(this);
    }

    static void styleAlertDialog(
            @NonNull AlertDialog dialog,
            @NonNull TermuxColorPalette palette,
            int positiveButtonColor) {
        float density = dialog.getContext().getResources().getDisplayMetrics().density;
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable windowBg = new GradientDrawable();
            windowBg.setShape(GradientDrawable.RECTANGLE);
            windowBg.setCornerRadius(24f * density);
            windowBg.setColor(palette.surfaceContainerHigh);
            window.setBackgroundDrawable(windowBg);
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

    @NonNull
    private static String formatEndpoint(@NonNull SshProfile profile) {
        String base = profile.username + "@" + profile.host;
        if (profile.port != SshProfile.DEFAULT_PORT) {
            return base + ":" + profile.port;
        }
        return base;
    }

    @NonNull
    private String buildMetaSummary(@NonNull SshProfile profile) {
        StringBuilder sb = new StringBuilder();
        if (profile.authType == SshAuthType.SSH_KEY && !profile.privateKeyPath.isEmpty()) {
            sb.append(getString(R.string.ssh_host_key_summary, new File(profile.privateKeyPath).getName()));
        }
        if (!profile.proxyJump.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  \u00b7  ");
            }
            sb.append(getString(R.string.ssh_host_jump_summary, profile.proxyJump));
        }
        return sb.toString();
    }

    private void applyPalette(@NonNull TermuxColorPalette palette) {
        activePalette = palette;
        float density = getResources().getDisplayMetrics().density;
        TermuxThemeManager.applyActivityChrome(this, palette);

        if (rootView != null) {
            rootView.setBackgroundColor(palette.surface);
        }

        if (searchContainer != null) {
            GradientDrawable searchBg = new GradientDrawable();
            searchBg.setShape(GradientDrawable.RECTANGLE);
            searchBg.setCornerRadius(14f * density);
            searchBg.setColor(palette.surfaceContainer);
            searchBg.setStroke(Math.max(1, Math.round(density)), palette.outline);
            searchContainer.setBackground(searchBg);
        }

        if (searchInput != null) {
            searchInput.setTextColor(palette.onSurface);
            searchInput.setHintTextColor(palette.onSurfaceVariant);
        }

        if (searchClearButton != null) {
            ImageViewCompat.setImageTintList(
                searchClearButton, ColorStateList.valueOf(palette.onSurfaceVariant));
        }

        styleOutlinedActionButton(importConfigButton, palette);
        styleOutlinedActionButton(keyToolsButton, palette);
        stylePrimaryActionButton(addHostButton, palette);

        if (emptyStateContainer != null) {
            GradientDrawable emptyBg = new GradientDrawable();
            emptyBg.setShape(GradientDrawable.RECTANGLE);
            emptyBg.setCornerRadius(16f * density);
            emptyBg.setColor(palette.surfaceContainer);
            emptyBg.setStroke(Math.max(1, Math.round(density)), palette.outline);
            emptyStateContainer.setBackground(emptyBg);
        }
        if (emptyTitleView != null) {
            emptyTitleView.setTextColor(palette.onSurface);
        }
        if (emptyMessageView != null) {
            emptyMessageView.setTextColor(palette.onSurfaceVariant);
        }
        stylePrimaryActionButton(emptyPrimaryCta, palette);
        styleOutlinedActionButton(emptySecondaryCta, palette);
    }

    private static void stylePrimaryActionButton(
            @Nullable MaterialButton button,
            @NonNull TermuxColorPalette palette) {
        if (button == null) {
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        button.setTextColor(palette.onAccent);
        button.setIconTint(ColorStateList.valueOf(palette.onAccent));
    }

    private static void styleOutlinedActionButton(
            @Nullable MaterialButton button,
            @NonNull TermuxColorPalette palette) {
        if (button == null) {
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeColor(ColorStateList.valueOf(palette.outline));
        button.setTextColor(palette.onSurface);
        button.setIconTint(ColorStateList.valueOf(palette.onSurface));
    }
}
