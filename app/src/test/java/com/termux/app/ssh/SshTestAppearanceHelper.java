package com.termux.app.ssh;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.theme.NightMode;
import com.termux.terminal.TerminalColors;

import java.util.Properties;

/**
 * Shared test hygiene helper ensuring modified appearance preferences, terminal color scheme,
 * and global night mode state are cleanly reset between unit/Robolectric tests.
 */
final class SshTestAppearanceHelper {

    private SshTestAppearanceHelper() {
    }

    static void resetGlobalAppearanceState(@NonNull Context context) {
        SharedPreferences prefs = TermuxAppearancePreferences.getPreferences(context);
        if (prefs != null) {
            prefs.edit().clear().commit();
        }
        TerminalColors.COLOR_SCHEME.updateWith(new Properties());
        NightMode.setAppNightMode(NightMode.TRUE.getName());
    }
}
