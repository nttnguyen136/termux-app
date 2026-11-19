package com.termux.shared.termux.theme;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

/**
 * Immutable definition of a Termux terminal color scheme paired with synchronized
 * Material 3 UI chrome tokens (surfaces, accents, keycaps, system bars, and contrast-safe on-colors).
 * <p>
 * Pure Java (no Android framework color calls) so palette logic can be unit tested directly on the JVM.
 */
public final class TermuxColorPalette {

    public static final String THEME_CATPPUCCIN_MOCHA = "catppuccin_mocha";
    public static final String THEME_TOKYO_NIGHT = "tokyo_night";
    public static final String THEME_DRACULA = "dracula";
    public static final String THEME_NORD = "nord";
    public static final String THEME_GRUVBOX_DARK = "gruvbox_dark";
    public static final String THEME_OLED_PITCH_BLACK = "oled_pitch_black";
    public static final String THEME_CATPPUCCIN_LATTE = "catppuccin_latte";
    public static final String THEME_CUSTOM = "custom";

    public static final int ANSI_COLOR_COUNT = 16;

    @NonNull public final String id;
    @NonNull public final String displayName;
    public final boolean isLight;

    // Terminal core colors
    public final int terminalBackground;
    public final int terminalForeground;
    public final int terminalCursor;
    @NonNull public final int[] ansiColors;

    // Material 3 UI surface & container tokens
    public final int surface;
    public final int surfaceContainer;
    public final int surfaceContainerHigh;
    public final int toolbarSurface;

    // Extra keys keycap tokens
    public final int keycapBackground;
    public final int keycapText;
    public final int keycapActiveBackground;
    public final int keycapActiveText;
    public final int keycapLockedBackground;
    public final int keycapLockedText;

    // Semantic accent, text, outline, error, and system bar tokens
    public final int accent;
    public final int onAccent;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int outline;
    public final int error;
    public final int onError;
    public final int statusBarColor;
    public final int navigationBarColor;

    public TermuxColorPalette(
        @NonNull String id,
        @NonNull String displayName,
        boolean isLight,
        int terminalBackground,
        int terminalForeground,
        int terminalCursor,
        @NonNull int[] ansiColors,
        int surface,
        int surfaceContainer,
        int surfaceContainerHigh,
        int toolbarSurface,
        int keycapBackground,
        int keycapText,
        int keycapActiveBackground,
        int keycapActiveText,
        int keycapLockedBackground,
        int keycapLockedText,
        int accent,
        int onAccent,
        int onSurface,
        int onSurfaceVariant,
        int outline,
        int error,
        int onError,
        int statusBarColor,
        int navigationBarColor
    ) {
        if (ansiColors.length != ANSI_COLOR_COUNT) {
            throw new IllegalArgumentException("ansiColors must contain exactly " + ANSI_COLOR_COUNT + " colors");
        }
        this.id = id;
        this.displayName = displayName;
        this.isLight = isLight;
        this.terminalBackground = terminalBackground;
        this.terminalForeground = terminalForeground;
        this.terminalCursor = terminalCursor;
        this.ansiColors = Arrays.copyOf(ansiColors, ANSI_COLOR_COUNT);
        this.surface = surface;
        this.surfaceContainer = surfaceContainer;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.toolbarSurface = toolbarSurface;
        this.keycapBackground = keycapBackground;
        this.keycapText = keycapText;
        this.keycapActiveBackground = keycapActiveBackground;
        this.keycapActiveText = keycapActiveText;
        this.keycapLockedBackground = keycapLockedBackground;
        this.keycapLockedText = keycapLockedText;
        this.accent = accent;
        this.onAccent = onAccent;
        this.onSurface = onSurface;
        this.onSurfaceVariant = onSurfaceVariant;
        this.outline = outline;
        this.error = error;
        this.onError = onError;
        this.statusBarColor = statusBarColor;
        this.navigationBarColor = navigationBarColor;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    public boolean isLight() {
        return isLight;
    }

    public int getTerminalBackground() {
        return terminalBackground;
    }

    public int getTerminalForeground() {
        return terminalForeground;
    }

    public int getTerminalCursor() {
        return terminalCursor;
    }

    @NonNull
    public int[] getAnsiColors() {
        return Arrays.copyOf(ansiColors, ansiColors.length);
    }

    public int getAnsiColor(int index) {
        if (index < 0 || index >= ansiColors.length) {
            throw new IndexOutOfBoundsException("ANSI color index out of range: " + index);
        }
        return ansiColors[index];
    }

    public int getSurface() {
        return surface;
    }

    public int getSurfaceContainer() {
        return surfaceContainer;
    }

    public int getSurfaceContainerHigh() {
        return surfaceContainerHigh;
    }

    public int getToolbarSurface() {
        return toolbarSurface;
    }

    public int getKeycapBackground() {
        return keycapBackground;
    }

    public int getKeycapText() {
        return keycapText;
    }

    public int getKeycapActiveBackground() {
        return keycapActiveBackground;
    }

    public int getKeycapActiveText() {
        return keycapActiveText;
    }

    public int getKeycapLockedBackground() {
        return keycapLockedBackground;
    }

    public int getKeycapLockedText() {
        return keycapLockedText;
    }

    public int getAccent() {
        return accent;
    }

    public int getOnAccent() {
        return onAccent;
    }

    public int getOnSurface() {
        return onSurface;
    }

    public int getOnSurfaceVariant() {
        return onSurfaceVariant;
    }

    public int getOutline() {
        return outline;
    }

    public int getError() {
        return error;
    }

    public int getOnError() {
        return onError;
    }

    public int getStatusBarColor() {
        return statusBarColor;
    }

    public int getNavigationBarColor() {
        return navigationBarColor;
    }

    /**
     * Converts this palette's terminal colors into a {@link Properties} map compatible with
     * {@link com.termux.terminal.TerminalColorScheme#updateWith(Properties)}.
     */
    @NonNull
    public Properties toTerminalProperties() {
        Properties props = new Properties();
        props.setProperty("foreground", toHexRgb(terminalForeground));
        props.setProperty("background", toHexRgb(terminalBackground));
        props.setProperty("cursor", toHexRgb(terminalCursor));
        for (int i = 0; i < ansiColors.length; i++) {
            props.setProperty("color" + i, toHexRgb(ansiColors[i]));
        }
        return props;
    }

    @NonNull
    public static String toHexRgb(int argb) {
        return String.format(Locale.US, "#%06X", argb & 0x00FFFFFF);
    }

}
