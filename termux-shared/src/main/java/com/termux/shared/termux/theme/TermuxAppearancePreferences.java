package com.termux.shared.termux.theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.TermuxConstants;

/**
 * SharedPreferences store for Termux Material 3 appearance, theme, Nerd Font, cursor style,
 * top tab bar visibility, and monotonic appearance revision tracking.
 */
public final class TermuxAppearancePreferences {

    public static final String KEY_THEME_ID = "appearance_theme_id";
    public static final String KEY_FONT_ID = "appearance_font_id";
    public static final String KEY_CURSOR_STYLE = "appearance_cursor_style";
    public static final String KEY_SHOW_TOP_TAB_BAR = "appearance_show_top_tab_bar";
    public static final String KEY_APPEARANCE_REVISION = "appearance_revision";

    public static final int CURSOR_STYLE_UNSET = -1;
    public static final boolean DEFAULT_SHOW_TOP_TAB_BAR = false;

    private TermuxAppearancePreferences() {
    }

    @Nullable
    public static SharedPreferences getPreferences(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        return SharedPreferenceUtils.getPrivateSharedPreferences(
            appContext != null ? appContext : context,
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION
        );
    }

    /**
     * Returns the explicitly persisted theme ID, or {@code null} if the user has never explicitly
     * chosen a theme in-app (enabling upgrade-safe precedence when {@code ~/.termux/colors.properties} exists).
     */
    @Nullable
    public static String getSelectedThemeId(@NonNull Context context) {
        SharedPreferences prefs = getPreferences(context);
        return SharedPreferenceUtils.getString(prefs, KEY_THEME_ID, null, true);
    }

    @Nullable
    public static String getPersistedThemeId(@NonNull Context context) {
        return getSelectedThemeId(context);
    }

    public static long setSelectedThemeId(@NonNull Context context, @Nullable String themeId) {
        SharedPreferences prefs = getPreferences(context);
        if (prefs != null) {
            String normalized = (themeId != null && !themeId.trim().isEmpty()) ? themeId.trim() : null;
            if (normalized == null) {
                prefs.edit().remove(KEY_THEME_ID).commit();
            } else {
                SharedPreferenceUtils.setString(prefs, KEY_THEME_ID, normalized, true);
            }
        }
        return bumpAppearanceRevision(context);
    }

    public static long setPersistedThemeId(@NonNull Context context, @Nullable String themeId) {
        return setSelectedThemeId(context, themeId);
    }

    /**
     * Returns the explicitly persisted font ID, or {@code null} if the user has never explicitly
     * chosen a font in-app (enabling upgrade-safe precedence when {@code ~/.termux/font.ttf} exists).
     */
    @Nullable
    public static String getSelectedFontId(@NonNull Context context) {
        SharedPreferences prefs = getPreferences(context);
        return SharedPreferenceUtils.getString(prefs, KEY_FONT_ID, null, true);
    }

    @Nullable
    public static String getPersistedFontId(@NonNull Context context) {
        return getSelectedFontId(context);
    }

    public static long setSelectedFontId(@NonNull Context context, @Nullable String fontId) {
        SharedPreferences prefs = getPreferences(context);
        if (prefs != null) {
            String normalized = (fontId != null && !fontId.trim().isEmpty()) ? fontId.trim() : null;
            if (normalized == null) {
                prefs.edit().remove(KEY_FONT_ID).commit();
            } else {
                SharedPreferenceUtils.setString(prefs, KEY_FONT_ID, normalized, true);
            }
        }
        return bumpAppearanceRevision(context);
    }

    public static long setPersistedFontId(@NonNull Context context, @Nullable String fontId) {
        return setSelectedFontId(context, fontId);
    }

    /**
     * Returns the persisted in-app cursor style ({@link com.termux.terminal.TerminalEmulator#TERMINAL_CURSOR_STYLE_BLOCK},
     * {@code UNDERLINE}, {@code BAR}), or {@link #CURSOR_STYLE_UNSET} ({@code -1}) when unset.
     */
    public static int getCursorStyle(@NonNull Context context) {
        SharedPreferences prefs = getPreferences(context);
        return SharedPreferenceUtils.getInt(prefs, KEY_CURSOR_STYLE, CURSOR_STYLE_UNSET);
    }

    public static long setCursorStyle(@NonNull Context context, int cursorStyle) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferenceUtils.setInt(prefs, KEY_CURSOR_STYLE, cursorStyle, true);
        return bumpAppearanceRevision(context);
    }

    public static boolean shouldShowTopTabBar(@NonNull Context context) {
        SharedPreferences prefs = getPreferences(context);
        return SharedPreferenceUtils.getBoolean(prefs, KEY_SHOW_TOP_TAB_BAR, DEFAULT_SHOW_TOP_TAB_BAR);
    }

    public static boolean isShowTopTabBar(@NonNull Context context) {
        return shouldShowTopTabBar(context);
    }

    public static long setShowTopTabBar(@NonNull Context context, boolean show) {
        SharedPreferences prefs = getPreferences(context);
        SharedPreferenceUtils.setBoolean(prefs, KEY_SHOW_TOP_TAB_BAR, show, true);
        return bumpAppearanceRevision(context);
    }

    public static synchronized long getAppearanceRevision(@NonNull Context context) {
        SharedPreferences prefs = getPreferences(context);
        return SharedPreferenceUtils.getLong(prefs, KEY_APPEARANCE_REVISION, 0L);
    }

    public static synchronized long bumpAppearanceRevision(@NonNull Context context) {
        SharedPreferences prefs = getPreferences(context);
        long next = SharedPreferenceUtils.getLong(prefs, KEY_APPEARANCE_REVISION, 0L) + 1L;
        SharedPreferenceUtils.setLong(prefs, KEY_APPEARANCE_REVISION, next, true);
        return next;
    }

}
