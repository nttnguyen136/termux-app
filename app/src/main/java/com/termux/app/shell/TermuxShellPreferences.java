package com.termux.app.shell;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.TermuxConstants;

/**
 * SharedPreferences helper for shell & welcome banner configuration, backed by the canonical
 * Termux default preferences file ({@link TermuxConstants#TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION}).
 */
public final class TermuxShellPreferences {

    public static final String KEY_WELCOME_BANNER_ENABLED = "shell_welcome_banner_enabled";
    public static final boolean DEFAULT_WELCOME_BANNER_ENABLED = true;

    public static final String KEY_SHELL_BUNDLE_VERSION = "shell_bundle_installed_version";

    private TermuxShellPreferences() {
    }

    @Nullable
    private static SharedPreferences getPrefs(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Context target = appContext != null ? appContext : context;
        return target.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
    }

    public static boolean hasWelcomeBannerPreference(@NonNull Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.contains(KEY_WELCOME_BANNER_ENABLED);
    }

    public static boolean isWelcomeBannerEnabled(@NonNull Context context) {
        return SharedPreferenceUtils.getBoolean(
            getPrefs(context), KEY_WELCOME_BANNER_ENABLED, DEFAULT_WELCOME_BANNER_ENABLED);
    }

    public static void setWelcomeBannerEnabled(@NonNull Context context, boolean enabled) {
        SharedPreferenceUtils.setBoolean(
            getPrefs(context), KEY_WELCOME_BANNER_ENABLED, enabled, false);
    }

    @Nullable
    public static String getInstalledShellBundleVersion(@NonNull Context context) {
        return SharedPreferenceUtils.getString(
            getPrefs(context), KEY_SHELL_BUNDLE_VERSION, null, true);
    }

    public static void setInstalledShellBundleVersion(@NonNull Context context, @Nullable String version) {
        SharedPreferenceUtils.setString(
            getPrefs(context), KEY_SHELL_BUNDLE_VERSION, version, false);
    }
}
