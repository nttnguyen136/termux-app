package com.termux.app.ssh;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.theme.TermuxAppearanceAware;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Coordinates the Left Navigation Drawer "SSH Hosts" quick-connect section in {@link TermuxActivity}.
 */
public final class SshDrawerCoordinator implements TermuxAppearanceAware {

    public static final int MAX_VISIBLE_HOSTS = 4;
    private static final String LOG_TAG = "SshDrawerCoordinator";

    @FunctionalInterface
    public interface DependenciesFactory {
        @NonNull
        Dependencies create(@NonNull TermuxActivity activity);
    }

    public static final class Dependencies {
        @NonNull public final SshProfileRepository repository;
        @NonNull public final SshConnectionLauncher connectionLauncher;

        public Dependencies(
                @NonNull SshProfileRepository repository,
                @NonNull SshConnectionLauncher connectionLauncher) {
            this.repository = repository;
            this.connectionLauncher = connectionLauncher;
        }

        public Dependencies(
                @NonNull TermuxActivity activity,
                @NonNull SshProfileRepository repository,
                @NonNull File prefixDir,
                @NonNull File homeDir,
                @NonNull File tmpDir,
                @NonNull SshSessionLaunchGateway launchGateway,
                @NonNull SshConnectionLauncher.Clock clock) {
            this(
                repository,
                new SshConnectionLauncher(
                    activity,
                    repository,
                    prefixDir,
                    homeDir,
                    tmpDir,
                    launchGateway,
                    clock
                )
            );
        }

        @NonNull
        public static Dependencies createDefault(@NonNull TermuxActivity activity) {
            SshProfileRepository repository = SshProfileRepository.createDefault(activity);
            return new Dependencies(
                repository,
                SshConnectionLauncher.createDefault(activity, repository)
            );
        }
    }

    @NonNull private final TermuxActivity activity;
    @NonNull private Dependencies dependencies;
    @Nullable private final View dividerView;
    @Nullable private final TextView sectionTitleView;
    @Nullable private final MaterialButton manageButton;
    @Nullable private final LinearLayout hostsListContainer;
    @Nullable private final TextView emptyStateView;
    @NonNull private TermuxColorPalette activePalette;
    @NonNull private Typeface activeTypeface;

    public SshDrawerCoordinator(
            @NonNull TermuxActivity activity,
            @NonNull Dependencies dependencies) {
        this.activity = activity;
        this.dependencies = dependencies;
        this.dividerView = activity.findViewById(R.id.drawer_ssh_divider);
        this.sectionTitleView = activity.findViewById(R.id.drawer_ssh_section_title);
        this.manageButton = activity.findViewById(R.id.drawer_ssh_manage_button);
        this.hostsListContainer = activity.findViewById(R.id.drawer_ssh_hosts_list);
        this.emptyStateView = activity.findViewById(R.id.drawer_ssh_empty_state);
        this.activePalette = TermuxThemeManager.getActivePalette(activity);
        this.activeTypeface = TermuxFontManager.getActiveTypeface(activity);

        if (manageButton != null) {
            manageButton.setOnClickListener(v -> {
                closeDrawerSafely(activity.getDrawer());
                ActivityUtils.startActivity(activity, SshHostsActivity.createIntent(activity));
            });
        }
    }

    static void closeDrawerSafely(@Nullable DrawerLayout drawer) {
        if (drawer == null) {
            return;
        }
        if (drawer.isLaidOut()) {
            drawer.closeDrawers();
        } else {
            drawer.closeDrawer(GravityCompat.START, false);
        }
    }

    public void setDependencies(@NonNull Dependencies dependencies) {
        this.dependencies = dependencies;
    }

    public void refreshHosts() {
        if (hostsListContainer == null || emptyStateView == null) {
            return;
        }
        List<SshProfile> sorted;
        try {
            sorted = dependencies.repository.getAllSorted();
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load SSH profiles for drawer", e);
            sorted = Collections.emptyList();
        }

        int count = Math.min(sorted.size(), MAX_VISIBLE_HOSTS);
        hostsListContainer.removeAllViews();

        if (count == 0) {
            hostsListContainer.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
            applyAppearance(activePalette, activeTypeface);
            return;
        }

        emptyStateView.setVisibility(View.GONE);
        hostsListContainer.setVisibility(View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < count; i++) {
            SshProfile profile = sorted.get(i);
            View row = inflater.inflate(R.layout.item_drawer_ssh_host, hostsListContainer, false);
            bindHostRow(row, profile);
            hostsListContainer.addView(row);
        }

        applyAppearance(activePalette, activeTypeface);
    }

    @Override
    public void applyAppearance(@NonNull TermuxColorPalette palette, @NonNull Typeface typeface) {
        activePalette = palette;
        activeTypeface = typeface;
        float density = activity.getResources().getDisplayMetrics().density;

        if (dividerView != null) {
            dividerView.setBackgroundColor(ColorUtils.setAlphaComponent(palette.outline, 80));
        }
        if (sectionTitleView != null) {
            sectionTitleView.setTextColor(palette.onSurfaceVariant);
            sectionTitleView.setTypeface(typeface, Typeface.BOLD);
        }
        if (manageButton != null) {
            manageButton.setTextColor(palette.accent);
            manageButton.setTypeface(typeface, Typeface.BOLD);
        }
        if (emptyStateView != null) {
            GradientDrawable emptyBg = getOrCreateRoundedDrawable(emptyStateView, 10f * density);
            emptyBg.setColor(ColorUtils.setAlphaComponent(palette.surfaceContainer, 180));
            emptyBg.setStroke(
                Math.max(1, Math.round(density)),
                ColorUtils.setAlphaComponent(palette.outline, 80)
            );
            emptyStateView.setBackground(emptyBg);
            emptyStateView.setTextColor(palette.onSurfaceVariant);
            emptyStateView.setTypeface(typeface);
        }

        if (hostsListContainer != null) {
            for (int i = 0; i < hostsListContainer.getChildCount(); i++) {
                View row = hostsListContainer.getChildAt(i);
                Object tag = row.getTag();
                SshProfile profile = tag instanceof SshProfile ? (SshProfile) tag : null;
                styleHostRow(row, profile, palette, typeface, density);
            }
        }
    }

    private void bindHostRow(@NonNull View row, @NonNull SshProfile profile) {
        row.setTag(profile);
        TextView titleView = row.findViewById(R.id.drawer_ssh_host_title);
        TextView subtitleView = row.findViewById(R.id.drawer_ssh_host_subtitle);
        TextView pinBadgeView = row.findViewById(R.id.drawer_ssh_host_pin_badge);
        TextView authBadgeView = row.findViewById(R.id.drawer_ssh_host_auth_badge);

        if (titleView != null) {
            titleView.setText(profile.getEffectiveDisplayName());
        }
        if (subtitleView != null) {
            subtitleView.setText(formatEndpoint(profile));
        }
        if (pinBadgeView != null) {
            pinBadgeView.setVisibility(profile.pinned ? View.VISIBLE : View.GONE);
        }
        if (authBadgeView != null) {
            authBadgeView.setText(
                profile.authType == SshAuthType.PASSWORD
                    ? R.string.ssh_auth_type_password
                    : R.string.ssh_auth_type_key
            );
        }

        row.setOnClickListener(v -> connectToProfile(profile));
    }

    private static void styleHostRow(
            @NonNull View row,
            @Nullable SshProfile profile,
            @NonNull TermuxColorPalette palette,
            @NonNull Typeface typeface,
            float density) {
        boolean pinned = profile != null && profile.pinned;

        GradientDrawable cardBg = getOrCreateRoundedDrawable(row, 10f * density);
        cardBg.setColor(pinned ? palette.surfaceContainerHigh : palette.surfaceContainer);
        cardBg.setStroke(
            Math.max(1, Math.round(density)),
            pinned
                ? ColorUtils.setAlphaComponent(palette.accent, 160)
                : ColorUtils.setAlphaComponent(palette.outline, 80)
        );
        row.setBackground(cardBg);

        TextView titleView = row.findViewById(R.id.drawer_ssh_host_title);
        if (titleView != null) {
            titleView.setTextColor(palette.onSurface);
            titleView.setTypeface(typeface, Typeface.BOLD);
        }

        TextView subtitleView = row.findViewById(R.id.drawer_ssh_host_subtitle);
        if (subtitleView != null) {
            subtitleView.setTextColor(palette.onSurfaceVariant);
            subtitleView.setTypeface(typeface);
        }

        TextView pinBadgeView = row.findViewById(R.id.drawer_ssh_host_pin_badge);
        if (pinBadgeView != null) {
            GradientDrawable pinBg = getOrCreateRoundedDrawable(pinBadgeView, 8f * density);
            pinBg.setColor(palette.accent);
            pinBadgeView.setBackground(pinBg);
            pinBadgeView.setTextColor(palette.onAccent);
            pinBadgeView.setTypeface(typeface, Typeface.BOLD);
        }

        TextView authBadgeView = row.findViewById(R.id.drawer_ssh_host_auth_badge);
        if (authBadgeView != null) {
            GradientDrawable authBg = getOrCreateRoundedDrawable(authBadgeView, 8f * density);
            authBg.setColor(palette.surfaceContainerHigh);
            authBg.setStroke(Math.max(1, Math.round(0.75f * density)), palette.outline);
            authBadgeView.setBackground(authBg);
            authBadgeView.setTextColor(palette.onSurfaceVariant);
            authBadgeView.setTypeface(typeface, Typeface.BOLD);
        }
    }

    @NonNull
    private static GradientDrawable getOrCreateRoundedDrawable(
            @NonNull View view,
            float cornerRadiusPx) {
        Drawable existing = view.getBackground();
        GradientDrawable drawable;
        if (existing instanceof GradientDrawable) {
            drawable = (GradientDrawable) existing.mutate();
        } else {
            drawable = new GradientDrawable();
        }
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(cornerRadiusPx);
        return drawable;
    }

    private void connectToProfile(@NonNull SshProfile profile) {
        closeDrawerSafely(activity.getDrawer());
        dependencies.connectionLauncher.connect(profile, outcome -> {
            if (outcome.status == SshConnectionLauncher.LaunchStatus.LAUNCHED) {
                refreshHosts();
            } else if (outcome.status == SshConnectionLauncher.LaunchStatus.LAUNCH_FAILED) {
                Toast.makeText(activity, R.string.ssh_launch_error_session, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @NonNull
    private static String formatEndpoint(@NonNull SshProfile profile) {
        String base = profile.username + "@" + profile.host;
        if (profile.port != SshProfile.DEFAULT_PORT) {
            return base + ":" + profile.port;
        }
        return base;
    }
}
