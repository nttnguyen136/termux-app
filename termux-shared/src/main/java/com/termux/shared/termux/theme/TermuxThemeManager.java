package com.termux.shared.termux.theme;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.termux.shared.R;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.theme.NightMode;
import com.termux.terminal.TerminalColors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Coordinates upgrade-safe color palette resolution, active theme persistence,
 * {@link TerminalColors#COLOR_SCHEME} updates, and Material 3 activity chrome / system bar synchronization.
 */
public final class TermuxThemeManager {

    private TermuxThemeManager() {
    }

    /**
     * Pure-Java upgrade-safe theme precedence resolver:
     * <ul>
     *   <li>If {@code persistedThemeId} is null/empty and {@code customColorsFile} exists with non-zero length,
     *       returns {@link TermuxColorPalette#THEME_CUSTOM} (preserving existing user customization on upgrade).</li>
     *   <li>If {@code persistedThemeId} is null/empty and no non-empty custom file exists,
     *       returns {@link TermuxColorPalette#THEME_CATPPUCCIN_MOCHA} (fresh install default).</li>
     *   <li>If {@code persistedThemeId} is explicitly set to {@link TermuxColorPalette#THEME_CUSTOM} or a valid built-in ID,
     *       returns that ID (explicit user selection overrides custom file without deleting it).</li>
     *   <li>Otherwise falls back to {@link TermuxColorPalette#THEME_CATPPUCCIN_MOCHA}.</li>
     * </ul>
     */
    @NonNull
    public static String resolveEffectiveThemeId(@Nullable String persistedThemeId, @Nullable File customColorsFile) {
        String normalized = persistedThemeId != null ? persistedThemeId.trim() : null;
        if (normalized == null || normalized.isEmpty()) {
            if (isValidCustomColorsFile(customColorsFile)) {
                return TermuxColorPalette.THEME_CUSTOM;
            }
            return TermuxColorPalette.THEME_CATPPUCCIN_MOCHA;
        }
        if (TermuxColorPalette.THEME_CUSTOM.equals(normalized)) {
            return TermuxColorPalette.THEME_CUSTOM;
        }
        if (TermuxThemePresets.isBuiltInThemeId(normalized)) {
            return normalized;
        }
        return TermuxColorPalette.THEME_CATPPUCCIN_MOCHA;
    }

    public static boolean isValidCustomColorsFile(@Nullable File customColorsFile) {
        return customColorsFile != null && customColorsFile.isFile() && customColorsFile.length() > 0L;
    }

    @NonNull
    public static String getEffectiveThemeId(@NonNull Context context) {
        return getEffectiveThemeId(context, TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE);
    }

    @NonNull
    public static String getEffectiveThemeId(@NonNull Context context, @Nullable File customColorsFile) {
        String persisted = TermuxAppearancePreferences.getSelectedThemeId(context);
        return resolveEffectiveThemeId(persisted, customColorsFile);
    }

    @NonNull
    public static TermuxColorPalette getActivePalette(@NonNull Context context) {
        return getActivePalette(context, TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE);
    }

    @NonNull
    public static TermuxColorPalette getActivePalette(@NonNull Context context, @Nullable File customColorsFile) {
        String persisted = TermuxAppearancePreferences.getSelectedThemeId(context);
        return resolvePalette(persisted, customColorsFile);
    }

    /**
     * Pure-Java palette resolver that never mutates or overwrites {@code customColorsFile}.
     */
    @NonNull
    public static TermuxColorPalette resolvePalette(@Nullable String persistedThemeId, @Nullable File customColorsFile) {
        String effectiveId = resolveEffectiveThemeId(persistedThemeId, customColorsFile);
        if (TermuxColorPalette.THEME_CUSTOM.equals(effectiveId)) {
            return loadCustomPalette(customColorsFile);
        }
        return TermuxThemePresets.getBuiltInById(effectiveId);
    }

    @NonNull
    public static TermuxColorPalette loadCustomPalette(@Nullable File customColorsFile) {
        if (!isValidCustomColorsFile(customColorsFile)) {
            return TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        }
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(customColorsFile)) {
            props.load(in);
        } catch (IOException | IllegalArgumentException ignored) {
            return TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        }
        return TermuxThemePresets.fromCustomProperties(props);
    }

    /**
     * Persists the selected theme ID, increments the monotonic appearance revision counter,
     * and synchronizes {@link NightMode} with the resolved palette.
     */
    @NonNull
    public static TermuxColorPalette setSelectedThemeId(@NonNull Context context, @Nullable String themeId) {
        return setSelectedThemeId(context, themeId, TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE);
    }

    @NonNull
    public static TermuxColorPalette setSelectedThemeId(
        @NonNull Context context,
        @Nullable String themeId,
        @Nullable File customColorsFile
    ) {
        TermuxAppearancePreferences.setSelectedThemeId(context, themeId);
        TermuxColorPalette palette = getActivePalette(context, customColorsFile);
        syncAppNightMode(palette);
        return palette;
    }

    /**
     * Synchronizes Termux's static {@link NightMode} with {@code palette.isLight}.
     * Does not call {@code AppCompatDelegate.setDefaultNightMode} so live theme switching
     * updates running activities in-place without triggering unwanted activity recreation.
     */
    public static void syncAppNightMode(@NonNull TermuxColorPalette palette) {
        NightMode target = palette.isLight ? NightMode.FALSE : NightMode.TRUE;
        NightMode.setAppNightMode(target.getName());
    }

    public static void syncNightMode(@NonNull TermuxColorPalette palette) {
        syncAppNightMode(palette);
    }

    /**
     * Updates {@link TerminalColors#COLOR_SCHEME} with the active palette's terminal colors.
     */
    public static void applyToTerminalColorScheme(@NonNull TermuxColorPalette palette) {
        TerminalColors.COLOR_SCHEME.updateWith(palette.toTerminalProperties());
    }

    /**
     * Synchronizes window background, status/navigation bar colors, status/navigation bar light/dark
     * icon appearance, and primary toolbar colors (if present) with the given {@link TermuxColorPalette}.
     */
    public static void applyActivityChrome(
        @NonNull AppCompatActivity activity,
        @NonNull TermuxColorPalette palette
    ) {
        applyActivityChrome(activity, palette, palette.surface);
    }

    public static void applyActivityChrome(
        @NonNull AppCompatActivity activity,
        @NonNull TermuxColorPalette palette,
        int windowBackgroundColor
    ) {
        Window window = activity.getWindow();
        if (window != null) {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            );
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(palette.statusBarColor);
            window.setNavigationBarColor(palette.navigationBarColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            window.setBackgroundDrawable(new ColorDrawable(windowBackgroundColor));
            View decorView = window.getDecorView();
            if (decorView != null) {
                decorView.setBackgroundColor(windowBackgroundColor);
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decorView);
                if (controller != null) {
                    controller.setAppearanceLightStatusBars(palette.isLight);
                    controller.setAppearanceLightNavigationBars(palette.isLight);
                }
            }
        }

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(palette.surface);
            toolbar.setTitleTextColor(palette.onSurface);
            toolbar.setSubtitleTextColor(palette.onSurfaceVariant);
            Drawable navIcon = toolbar.getNavigationIcon();
            if (navIcon != null) {
                Drawable wrapped = DrawableCompat.wrap(navIcon.mutate());
                DrawableCompat.setTint(wrapped, palette.onSurface);
                toolbar.setNavigationIcon(wrapped);
            }
            Drawable overflowIcon = toolbar.getOverflowIcon();
            if (overflowIcon != null) {
                Drawable wrapped = DrawableCompat.wrap(overflowIcon.mutate());
                DrawableCompat.setTint(wrapped, palette.onSurface);
                toolbar.setOverflowIcon(wrapped);
            }
        }
    }

}
