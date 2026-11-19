package com.termux.shared.termux.theme;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.theme.NightMode;

public class TermuxThemeUtils {

    /**
     * Resolve the active {@link TermuxColorPalette} via {@link TermuxThemeManager}
     * and synchronize the app-wide {@link NightMode} with {@code palette.isLight}.
     */
    public static void setAppNightMode(@NonNull Context context) {
        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(context);
        TermuxThemeManager.syncAppNightMode(palette);
    }

    /** Set name as app wide night mode value. */
    public static void setAppNightMode(@Nullable String name) {
        NightMode.setAppNightMode(name);
    }
}
