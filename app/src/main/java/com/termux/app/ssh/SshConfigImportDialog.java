package com.termux.app.ssh;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.theme.TermuxColorPalette;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Material 3 preview and selective import dialog for one-way, read-only {@code ~/.ssh/config}
 * ingestion via {@link SshConfigImporter}.
 */
final class SshConfigImportDialog {

    private static final String LOG_TAG = "SshConfigImportDialog";

    private static final class CandidateRow {
        final SshConfigImporter.ImportCandidate candidate;
        final CheckBox checkBox;
        final EditText usernameInput;

        CandidateRow(
                @NonNull SshConfigImporter.ImportCandidate candidate,
                @NonNull CheckBox checkBox,
                @NonNull EditText usernameInput) {
            this.candidate = candidate;
            this.checkBox = checkBox;
            this.usernameInput = usernameInput;
        }
    }

    private SshConfigImportDialog() {
    }

    static void show(
            @NonNull SshHostsActivity activity,
            @NonNull SshHostsEnvironment environment,
            @NonNull TermuxColorPalette palette,
            @NonNull List<SshProfile> existingProfiles,
            @NonNull Runnable onImported) {
        List<SshConfigImporter.ImportCandidate> candidates;
        try {
            candidates = SshConfigImporter.parse(
                environment.getSshConfigFile(),
                environment.homeDir,
                existingProfiles
            );
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read ~/.ssh/config", e);
            Toast.makeText(
                activity,
                activity.getString(R.string.ssh_action_failed, e.getMessage()),
                Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (candidates.isEmpty()) {
            AlertDialog emptyDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ssh_config_import_title)
                .setMessage(R.string.ssh_config_import_empty)
                .setPositiveButton(android.R.string.ok, null)
                .create();
            SshDialogStyler.showStyledDialog(activity, emptyDialog, palette);
            return;
        }

        float density = activity.getResources().getDisplayMetrics().density;
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_ssh_config_import, null, false);
        ((TextView) view.findViewById(R.id.ssh_config_import_title)).setTextColor(palette.onSurface);
        ((TextView) view.findViewById(R.id.ssh_config_import_subtitle)).setTextColor(palette.onSurfaceVariant);

        LinearLayout listContainer = view.findViewById(R.id.ssh_config_import_list);
        LayoutInflater inflater = LayoutInflater.from(activity);
        List<CandidateRow> rows = new ArrayList<>(candidates.size());

        for (SshConfigImporter.ImportCandidate candidate : candidates) {
            View rowView = inflater.inflate(R.layout.item_ssh_config_import, listContainer, false);
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setShape(GradientDrawable.RECTANGLE);
            rowBg.setCornerRadius(12f * density);
            rowBg.setColor(palette.surfaceContainer);
            rowBg.setStroke(Math.max(1, Math.round(density)), palette.outline);
            rowView.setBackground(rowBg);

            CheckBox checkBox = rowView.findViewById(R.id.ssh_import_candidate_checkbox);
            checkBox.setButtonTintList(ColorStateList.valueOf(palette.accent));
            checkBox.setChecked(!candidate.isDuplicate() && candidate.hasUsername());

            TextView aliasView = rowView.findViewById(R.id.ssh_import_candidate_alias);
            aliasView.setText(candidate.getAlias());
            aliasView.setTextColor(palette.onSurface);

            TextView endpointView = rowView.findViewById(R.id.ssh_import_candidate_endpoint);
            endpointView.setText(candidate.getProfile().formatEndpoint());
            endpointView.setTextColor(palette.onSurfaceVariant);

            TextView dupBadge = rowView.findViewById(R.id.ssh_import_candidate_duplicate_badge);
            if (candidate.isDuplicate()) {
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setShape(GradientDrawable.RECTANGLE);
                badgeBg.setCornerRadius(10f * density);
                badgeBg.setColor(palette.surfaceContainerHigh);
                badgeBg.setStroke(Math.max(1, Math.round(0.75f * density)), palette.outline);
                dupBadge.setBackground(badgeBg);
                dupBadge.setTextColor(palette.onSurfaceVariant);
                dupBadge.setVisibility(View.VISIBLE);
            } else {
                dupBadge.setVisibility(View.GONE);
            }

            TextView missingUserHint = rowView.findViewById(R.id.ssh_import_candidate_missing_user_hint);
            EditText usernameInput = rowView.findViewById(R.id.ssh_import_candidate_username_input);
            if (!candidate.hasUsername()) {
                missingUserHint.setTextColor(palette.error);
                missingUserHint.setVisibility(View.VISIBLE);

                GradientDrawable inputBg = new GradientDrawable();
                inputBg.setShape(GradientDrawable.RECTANGLE);
                inputBg.setCornerRadius(10f * density);
                inputBg.setColor(palette.surfaceContainerHigh);
                inputBg.setStroke(Math.max(1, Math.round(density)), palette.outline);
                usernameInput.setBackground(inputBg);
                usernameInput.setTextColor(palette.onSurface);
                usernameInput.setHintTextColor(palette.onSurfaceVariant);
                usernameInput.setVisibility(View.VISIBLE);
                usernameInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (!candidate.isDuplicate() && s != null && !s.toString().trim().isEmpty()) {
                            checkBox.setChecked(true);
                        }
                    }
                });
            }

            rows.add(new CandidateRow(candidate, checkBox, usernameInput));
            listContainer.addView(rowView);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(R.string.ssh_config_import_confirm, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        SshDialogStyler.showStyledDialog(activity, dialog, palette);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<SshProfile> toImport = new ArrayList<>();
            for (CandidateRow row : rows) {
                if (!row.checkBox.isChecked()) {
                    continue;
                }
                SshProfile profile = row.candidate.getProfile();
                if (!row.candidate.hasUsername()) {
                    String enteredUser = row.usernameInput.getText() != null
                        ? row.usernameInput.getText().toString().trim()
                        : "";
                    if (enteredUser.isEmpty()) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.ssh_config_import_missing_username_error, row.candidate.getAlias()),
                            Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    profile = profile.toBuilder().username(enteredUser).build();
                }
                SshValidationResult validation = SshProfileValidator.validate(profile);
                if (!validation.isValid()) {
                    Toast.makeText(activity, validation.getJoinedErrorMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                toImport.add(profile);
            }

            if (toImport.isEmpty()) {
                Toast.makeText(activity, R.string.ssh_config_import_none_selected, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }

            try {
                for (SshProfile profile : toImport) {
                    environment.repository.saveProfile(profile);
                }
                dialog.dismiss();
                Toast.makeText(
                    activity,
                    activity.getString(R.string.ssh_config_import_success, toImport.size()),
                    Toast.LENGTH_SHORT
                ).show();
                onImported.run();
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to import SSH profiles", e);
                Toast.makeText(
                    activity,
                    activity.getString(R.string.ssh_action_failed, e.getMessage()),
                    Toast.LENGTH_LONG
                ).show();
            }
        });
    }
}
