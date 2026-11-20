package com.termux.app.terminal;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.ssh.SshHostsActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

/**
 * Material 3 Bottom Sheet replacing the legacy Android context menu for terminal actions.
 * Dynamically styled with the active {@link TermuxColorPalette}.
 */
public final class TerminalActionsBottomSheet {

    private TerminalActionsBottomSheet() {
    }

    public static final class ActionIds {
        public static final String PASTE = "paste";
        public static final String NEW_SESSION = "new_session";
        public static final String SSH_HOSTS = "ssh_hosts";
        public static final String APPEARANCE = "appearance";
        public static final String TOGGLE_EXTRA_KEYS = "toggle_extra_keys";
        public static final String SELECT_URL = "select_url";
        public static final String SHARE_TRANSCRIPT = "share_transcript";
        public static final String SHARE_SELECTED_TEXT = "share_selected_text";
        public static final String AUTOFILL_USERNAME = "autofill_username";
        public static final String AUTOFILL_PASSWORD = "autofill_password";
        public static final String RESET_TERMINAL = "reset_terminal";
        public static final String KILL_PROCESS = "kill_process";
        public static final String KEEP_SCREEN_ON = "keep_screen_on";
        public static final String STYLING = "styling";
        public static final String HELP = "help";
        public static final String SETTINGS = "settings";
        public static final String REPORT = "report";

        private ActionIds() {
        }
    }

    @NonNull
    public static BottomSheetDialog create(@NonNull final TermuxActivity activity) {
        final BottomSheetDialog dialog = new BottomSheetDialog(activity);
        final View root = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_terminal_actions, null, false);
        dialog.setContentView(root);

        final TermuxColorPalette palette = TermuxThemeManager.getActivePalette(activity);
        final float density = activity.getResources().getDisplayMetrics().density;

        // Style rounded sheet container
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setShape(GradientDrawable.RECTANGLE);
        float topRadius = 24f * density;
        sheetBg.setCornerRadii(new float[]{topRadius, topRadius, topRadius, topRadius, 0f, 0f, 0f, 0f});
        sheetBg.setColor(palette.surfaceContainer);
        root.setBackground(sheetBg);

        // Style bottom sheet window container transparent so rounded corners show cleanly
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(Color.TRANSPARENT);
            }
        });

        View dragHandle = root.findViewById(R.id.terminal_actions_drag_handle);
        if (dragHandle != null) {
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setShape(GradientDrawable.RECTANGLE);
            handleBg.setCornerRadius(2f * density);
            handleBg.setColor(palette.outline);
            dragHandle.setBackground(handleBg);
        }

        TextView titleView = root.findViewById(R.id.terminal_actions_title);
        if (titleView != null) {
            titleView.setTextColor(palette.onSurface);
        }

        final TerminalSession currentSession = activity.getCurrentSession();
        TextView subtitleView = root.findViewById(R.id.terminal_actions_subtitle);
        if (subtitleView != null) {
            String sessionTitle = currentSession != null ? currentSession.getTitle() : null;
            if (!DataUtils.isNullOrEmpty(sessionTitle)) {
                subtitleView.setText(sessionTitle);
                subtitleView.setTextColor(palette.onSurfaceVariant);
                subtitleView.setVisibility(View.VISIBLE);
            } else {
                subtitleView.setVisibility(View.GONE);
            }
        }

        View divider = root.findViewById(R.id.terminal_actions_divider);
        if (divider != null) {
            divider.setBackgroundColor(palette.outline);
            divider.setAlpha(0.35f);
        }

        LinearLayout listContainer = root.findViewById(R.id.terminal_actions_list);
        final TerminalView terminalView = activity.getTerminalView();
        final TermuxTerminalViewClient viewClient = activity.getTermuxTerminalViewClient();
        final TermuxTerminalSessionActivityClient sessionClient = activity.getTermuxTerminalSessionClient();

        // Quick actions
        addActionRow(activity, listContainer, palette, density, ActionIds.PASTE,
            R.drawable.ic_content_paste, activity.getString(R.string.action_paste_clipboard),
            null, true, false, () -> {
                dialog.dismiss();
                if (viewClient != null) viewClient.doPaste();
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.NEW_SESSION,
            R.drawable.ic_add, activity.getString(R.string.action_new_session),
            null, true, false, () -> {
                dialog.dismiss();
                if (sessionClient != null) sessionClient.addNewSession(false, null);
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.SSH_HOSTS,
            R.drawable.ic_ssh_host, activity.getString(R.string.action_open_ssh_hosts),
            null, true, false, () -> {
                dialog.dismiss();
                ActivityUtils.startActivity(activity, SshHostsActivity.createIntent(activity));
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.APPEARANCE,
            R.drawable.ic_palette, activity.getString(R.string.action_open_appearance),
            null, true, false, () -> {
                dialog.dismiss();
                ActivityUtils.startActivity(activity, SettingsActivity.createAppearanceStudioIntent(activity));
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.TOGGLE_EXTRA_KEYS,
            R.drawable.ic_keyboard, activity.getString(R.string.action_toggle_extra_keys),
            null, true, false, () -> {
                dialog.dismiss();
                activity.toggleTerminalToolbar();
            });

        addSectionDivider(activity, listContainer, palette, density);

        // Session & transcript actions
        addActionRow(activity, listContainer, palette, density, ActionIds.SELECT_URL,
            R.drawable.ic_link, activity.getString(R.string.action_select_url),
            null, currentSession != null, false, () -> {
                dialog.dismiss();
                if (viewClient != null) viewClient.showUrlSelection();
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.SHARE_TRANSCRIPT,
            R.drawable.ic_share, activity.getString(R.string.action_share_transcript),
            null, currentSession != null, false, () -> {
                dialog.dismiss();
                if (viewClient != null) viewClient.shareSessionTranscript();
            });

        if (terminalView != null && !DataUtils.isNullOrEmpty(terminalView.getStoredSelectedText())) {
            addActionRow(activity, listContainer, palette, density, ActionIds.SHARE_SELECTED_TEXT,
                R.drawable.ic_share, activity.getString(R.string.action_share_selected_text),
                null, true, false, () -> {
                    dialog.dismiss();
                    if (viewClient != null) viewClient.shareSelectedText();
                });
        }

        if (terminalView != null && terminalView.isAutoFillEnabled()) {
            addActionRow(activity, listContainer, palette, density, ActionIds.AUTOFILL_USERNAME,
                R.drawable.ic_content_paste, activity.getString(R.string.action_autofill_username),
                null, true, false, () -> {
                    dialog.dismiss();
                    terminalView.requestAutoFillUsername();
                });
            addActionRow(activity, listContainer, palette, density, ActionIds.AUTOFILL_PASSWORD,
                R.drawable.ic_content_paste, activity.getString(R.string.action_autofill_password),
                null, true, false, () -> {
                    dialog.dismiss();
                    terminalView.requestAutoFillPassword();
                });
        }

        addActionRow(activity, listContainer, palette, density, ActionIds.RESET_TERMINAL,
            R.drawable.ic_refresh, activity.getString(R.string.action_reset_terminal),
            null, currentSession != null, false, () -> {
                dialog.dismiss();
                activity.resetTerminalSessionFromSheet(currentSession);
            });

        if (currentSession != null) {
            boolean isRunning = currentSession.isRunning();
            addActionRow(activity, listContainer, palette, density, ActionIds.KILL_PROCESS,
                R.drawable.ic_close, activity.getString(R.string.action_kill_process, currentSession.getPid()),
                null, isRunning, true, () -> {
                    dialog.dismiss();
                    activity.showKillSessionDialogFromSheet(currentSession);
                });
        }

        boolean keepScreenOn = activity.getPreferences() != null && activity.getPreferences().shouldKeepScreenOn();
        addActionRow(activity, listContainer, palette, density, ActionIds.KEEP_SCREEN_ON,
            R.drawable.ic_settings, activity.getString(R.string.action_toggle_keep_screen_on),
            activity.getString(keepScreenOn ? R.string.status_on : R.string.status_off),
            true, false, () -> {
                dialog.dismiss();
                activity.toggleKeepScreenOnFromSheet();
            });

        addSectionDivider(activity, listContainer, palette, density);

        // App-level actions
        addActionRow(activity, listContainer, palette, density, ActionIds.STYLING,
            R.drawable.ic_palette, activity.getString(R.string.action_style_terminal),
            null, true, false, () -> {
                dialog.dismiss();
                activity.showStylingDialogFromSheet();
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.HELP,
            R.drawable.ic_help, activity.getString(R.string.action_open_help),
            null, true, false, () -> {
                dialog.dismiss();
                ActivityUtils.startActivity(activity, new Intent(activity, HelpActivity.class));
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.SETTINGS,
            R.drawable.ic_settings, activity.getString(R.string.action_open_settings),
            null, true, false, () -> {
                dialog.dismiss();
                ActivityUtils.startActivity(activity, new Intent(activity, SettingsActivity.class));
            });

        addActionRow(activity, listContainer, palette, density, ActionIds.REPORT,
            R.drawable.ic_help, activity.getString(R.string.action_report_issue),
            null, currentSession != null, false, () -> {
                dialog.dismiss();
                if (viewClient != null) viewClient.reportIssueFromTranscript();
            });

        dialog.setOnDismissListener(d -> {
            if (terminalView != null) {
                terminalView.onContextMenuClosed(null);
            }
        });

        return dialog;
    }

    private static void addSectionDivider(@NonNull TermuxActivity activity,
                                          @NonNull ViewGroup parent,
                                          @NonNull TermuxColorPalette palette,
                                          float density) {
        View divider = new View(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density)));
        int marginV = Math.round(6f * density);
        int marginH = Math.round(8f * density);
        params.setMargins(marginH, marginV, marginH, marginV);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(palette.outline);
        divider.setAlpha(0.25f);
        parent.addView(divider);
    }

    private static void addActionRow(@NonNull TermuxActivity activity,
                                     @NonNull ViewGroup parent,
                                     @NonNull TermuxColorPalette palette,
                                     float density,
                                     @NonNull String actionTag,
                                     @DrawableRes int iconRes,
                                     @NonNull String label,
                                     @Nullable String badgeText,
                                     boolean enabled,
                                     boolean destructive,
                                     @NonNull Runnable onClick) {
        View row = LayoutInflater.from(activity).inflate(R.layout.item_bottom_sheet_action, parent, false);
        row.setTag(actionTag);

        ImageView icon = row.findViewById(R.id.action_icon);
        TextView labelView = row.findViewById(R.id.action_label);
        TextView badgeView = row.findViewById(R.id.action_badge);

        int tintColor = destructive ? palette.error : palette.onSurface;
        int iconTint = destructive ? palette.error : palette.onSurfaceVariant;

        if (icon != null) {
            icon.setImageResource(iconRes);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(iconTint));
        }

        if (labelView != null) {
            labelView.setText(label);
            labelView.setTextColor(tintColor);
        }

        if (badgeView != null) {
            if (badgeText != null && !badgeText.isEmpty()) {
                boolean isOn = badgeText.equalsIgnoreCase(activity.getString(R.string.status_on));
                badgeView.setText(badgeText);
                badgeView.setTextColor(isOn ? palette.onAccent : palette.onSurfaceVariant);
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setShape(GradientDrawable.RECTANGLE);
                badgeBg.setCornerRadius(10f * density);
                badgeBg.setColor(isOn ? palette.accent : palette.surfaceContainerHigh);
                badgeView.setBackground(badgeBg);
                badgeView.setVisibility(View.VISIBLE);
            } else {
                badgeView.setVisibility(View.GONE);
            }
        }

        row.setEnabled(enabled);
        row.setClickable(enabled);
        row.setAlpha(enabled ? 1.0f : 0.4f);
        if (enabled) {
            row.setOnClickListener(v -> onClick.run());
        }

        parent.addView(row);
    }
}
