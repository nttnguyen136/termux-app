package com.termux.app.theme;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.shared.termux.theme.TermuxThemePresets;
import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric lifecycle & integration tests for {@link TermuxActivity}, {@link SettingsActivity},
 * and {@link TermuxTerminalSessionActivityClient} live appearance updates.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TermuxAppearanceLifecycleTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        if (TermuxAppearancePreferences.getPreferences(context) != null) {
            TermuxAppearancePreferences.getPreferences(context).edit().clear().commit();
        }
    }

    @Test
    public void testCursorStylePrecedenceWithPropertiesFallback() {
        assertEquals(
            TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE,
            TermuxTerminalSessionActivityClient.resolveTerminalCursorStyleValue(
                TermuxAppearancePreferences.CURSOR_STYLE_UNSET,
                TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
            )
        );
        assertEquals(
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR,
            TermuxTerminalSessionActivityClient.resolveTerminalCursorStyleValue(
                TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR,
                TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
            )
        );
        assertEquals(
            TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE,
            TermuxTerminalSessionActivityClient.resolveTerminalCursorStyleValue(999, 888)
        );
    }

    @Test
    public void testMultiEmulatorColorAndCursorLiveReset() {
        final int[] activeCursorStyle = new int[]{TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK};
        TerminalSessionClient client = new StubTerminalSessionClient() {
            @Override
            public Integer getTerminalCursorStyle() {
                return activeCursorStyle[0];
            }
        };
        TerminalOutput output = new StubTerminalOutput();

        TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        TermuxThemeManager.applyToTerminalColorScheme(mocha);

        TerminalEmulator emulator1 = new TerminalEmulator(output, 80, 24, 12, 24, 100, client);
        TerminalEmulator emulator2 = new TerminalEmulator(output, 80, 24, 12, 24, 100, client);

        assertEquals(mocha.terminalBackground, emulator1.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals(mocha.terminalBackground, emulator2.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK, emulator1.getCursorStyle());
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK, emulator2.getCursorStyle());

        // Switch active theme to Catppuccin Latte and cursor style to BAR
        TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(
            TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        TermuxThemeManager.applyToTerminalColorScheme(latte);
        activeCursorStyle[0] = TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR;

        emulator1.mColors.reset();
        emulator1.setCursorStyle();
        emulator2.mColors.reset();
        emulator2.setCursorStyle();

        assertEquals(latte.terminalBackground, emulator1.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals(latte.terminalForeground, emulator1.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND]);
        assertEquals(latte.terminalCursor, emulator1.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR]);
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, emulator1.getCursorStyle());

        assertEquals(latte.terminalBackground, emulator2.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals(latte.terminalForeground, emulator2.mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND]);
        assertEquals(latte.terminalCursor, emulator2.mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR]);
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, emulator2.getCursorStyle());
    }

    @Test
    public void testSettingsActivityAppearanceStudioSeamAndSystemBarSync() {
        Intent studioIntent = SettingsActivity.createAppearanceStudioIntent(context);
        assertNotNull(studioIntent);
        assertEquals(
            SettingsActivity.FRAGMENT_APPEARANCE_STUDIO,
            studioIntent.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT)
        );

        // Dark theme (Catppuccin Mocha default)
        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, studioIntent)) {
            SettingsActivity activity = controller.create().start().resume().get();
            Fragment mounted = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
            assertTrue(mounted instanceof com.termux.app.fragments.settings.AppearanceStudioFragment);

            TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
            Window window = activity.getWindow();
            assertEquals(mocha.statusBarColor, window.getStatusBarColor());
            assertEquals(mocha.navigationBarColor, window.getNavigationBarColor());
            WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());
            assertFalse(insetsController.isAppearanceLightStatusBars());
        }

        // Light theme (Catppuccin Latte) with unknown fragment extra -> safe fallback + light bars
        TermuxThemeManager.setSelectedThemeId(context, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        Intent unknownIntent = new Intent(context, SettingsActivity.class);
        unknownIntent.putExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT, "unknown_future_fragment");

        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class, unknownIntent)) {
            SettingsActivity activity = controller.create().start().resume().get();
            Fragment mounted = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
            assertTrue(mounted instanceof SettingsActivity.RootPreferencesFragment);

            TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
            Window window = activity.getWindow();
            assertEquals(latte.statusBarColor, window.getStatusBarColor());
            assertEquals(latte.navigationBarColor, window.getNavigationBarColor());
            WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());
            assertTrue(insetsController.isAppearanceLightStatusBars());
        }
    }

    @Test
    public void testTermuxActivityLiveReloadOnResumeAndBroadcastWithoutRestart() {
        try (ActivityController<TermuxActivity> controller =
                 Robolectric.buildActivity(TermuxActivity.class)) {
            TermuxActivity activity = controller.create().start().resume().get();
            assertNotNull(activity.getTerminalView());
            assertNotNull(activity.getTerminalView().mRenderer);

            TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
            assertEquals(
                mocha.terminalBackground,
                TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]
            );
            assertEquals(mocha.statusBarColor, activity.getWindow().getStatusBarColor());
            assertEquals(mocha.navigationBarColor, activity.getWindow().getNavigationBarColor());
            assertEquals(
                mocha.terminalBackground,
                ((ColorDrawable) activity.getWindow().getDecorView().getBackground()).getColor()
            );

            // Simulate user opening SettingsActivity (TermuxActivity stopped -> broadcast receiver unregistered)
            controller.pause().stop();

            // Change theme to Catppuccin Latte (Light) and font to FiraCode while TermuxActivity is stopped
            TermuxThemeManager.setSelectedThemeId(context, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
            TermuxFontManager.setSelectedFontId(context, TermuxFontManager.FONT_FIRA_CODE);
            long expectedRev = TermuxAppearancePreferences.getAppearanceRevision(context);

            // Resume TermuxActivity without recreating it
            controller.start().resume();

            TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
            assertEquals(expectedRev, activity.getLastAppliedAppearanceRevision());
            assertEquals(
                latte.terminalBackground,
                TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]
            );
            assertEquals(latte.statusBarColor, activity.getWindow().getStatusBarColor());
            assertEquals(latte.navigationBarColor, activity.getWindow().getNavigationBarColor());
            assertEquals(
                latte.terminalBackground,
                ((ColorDrawable) activity.getWindow().getDecorView().getBackground()).getColor()
            );
            WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
            assertTrue(insetsController.isAppearanceLightStatusBars());

            // Also verify in-place ACTION_RELOAD_STYLE broadcast with EXTRA_RECREATE_ACTIVITY = false
            TermuxThemeManager.setSelectedThemeId(context, TermuxColorPalette.THEME_OLED_PITCH_BLACK);
            Intent reloadIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
            reloadIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, false);
            activity.sendBroadcast(reloadIntent);
            activity.applyCurrentAppearance();

            TermuxColorPalette oled = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_OLED_PITCH_BLACK);
            assertEquals(
                oled.terminalBackground,
                TerminalColors.COLOR_SCHEME.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]
            );
            assertEquals(oled.statusBarColor, activity.getWindow().getStatusBarColor());
            assertFalse(insetsController.isAppearanceLightStatusBars());
        }
    }

    private static class StubTerminalOutput extends TerminalOutput {
        @Override
        public void write(byte[] data, int offset, int count) {
        }

        @Override
        public void titleChanged(String oldTitle, String newTitle) {
        }

        @Override
        public void onCopyTextToClipboard(String text) {
        }

        @Override
        public void onPasteTextFromClipboard() {
        }

        @Override
        public void onBell() {
        }

        @Override
        public void onColorsChanged() {
        }
    }

    private static class StubTerminalSessionClient implements TerminalSessionClient {
        @Override
        public void onTextChanged(com.termux.terminal.TerminalSession changedSession) {
        }

        @Override
        public void onTitleChanged(com.termux.terminal.TerminalSession changedSession) {
        }

        @Override
        public void onSessionFinished(com.termux.terminal.TerminalSession finishedSession) {
        }

        @Override
        public void onCopyTextToClipboard(com.termux.terminal.TerminalSession session, String text) {
        }

        @Override
        public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession session) {
        }

        @Override
        public void onBell(com.termux.terminal.TerminalSession session) {
        }

        @Override
        public void onColorsChanged(com.termux.terminal.TerminalSession session) {
        }

        @Override
        public void onTerminalCursorStateChange(boolean state) {
        }

        @Override
        public void setTerminalShellPid(com.termux.terminal.TerminalSession session, int pid) {
        }

        @Override
        public Integer getTerminalCursorStyle() {
            return TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;
        }

        @Override
        public void logError(String tag, String message) {
        }

        @Override
        public void logWarn(String tag, String message) {
        }

        @Override
        public void logInfo(String tag, String message) {
        }

        @Override
        public void logDebug(String tag, String message) {
        }

        @Override
        public void logVerbose(String tag, String message) {
        }

        @Override
        public void logStackTraceWithMessage(String tag, String message, Exception e) {
        }

        @Override
        public void logStackTrace(String tag, Exception e) {
        }
    }

}
