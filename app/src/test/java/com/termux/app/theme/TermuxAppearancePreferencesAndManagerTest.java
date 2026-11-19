package com.termux.app.theme;

import android.content.Context;

import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.shared.theme.NightMode;
import com.termux.terminal.TerminalEmulator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric unit tests for {@link TermuxAppearancePreferences} and {@link TermuxThemeManager}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TermuxAppearancePreferencesAndManagerTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        if (TermuxAppearancePreferences.getPreferences(context) != null) {
            TermuxAppearancePreferences.getPreferences(context).edit().clear().commit();
        }
    }

    @Test
    public void testFreshInstallDefaultsAndMonotonicRevisionIncrement() {
        assertNull(TermuxAppearancePreferences.getSelectedThemeId(context));
        assertNull(TermuxAppearancePreferences.getSelectedFontId(context));
        assertEquals(
            TermuxAppearancePreferences.CURSOR_STYLE_UNSET,
            TermuxAppearancePreferences.getCursorStyle(context)
        );
        assertFalse(TermuxAppearancePreferences.shouldShowTopTabBar(context));
        assertEquals(0L, TermuxAppearancePreferences.getAppearanceRevision(context));

        File missingColors = new File(tempFolder.getRoot(), "missing_colors.properties");
        File missingFont = new File(tempFolder.getRoot(), "missing_font.ttf");

        assertEquals(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
            TermuxThemeManager.getActivePalette(context, missingColors).id
        );
        assertEquals(
            TermuxFontManager.FONT_JETBRAINS_MONO,
            TermuxFontManager.getEffectiveFontId(context, missingFont)
        );

        long rev1 = TermuxAppearancePreferences.setSelectedThemeId(
            context, TermuxColorPalette.THEME_TOKYO_NIGHT);
        assertEquals(1L, rev1);
        assertEquals(
            TermuxColorPalette.THEME_TOKYO_NIGHT,
            TermuxAppearancePreferences.getSelectedThemeId(context)
        );

        long rev2 = TermuxAppearancePreferences.setSelectedFontId(
            context, TermuxFontManager.FONT_MESLO_LGS);
        assertEquals(2L, rev2);
        assertEquals(
            TermuxFontManager.FONT_MESLO_LGS,
            TermuxAppearancePreferences.getSelectedFontId(context)
        );

        long rev3 = TermuxAppearancePreferences.setCursorStyle(
            context, TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR);
        assertEquals(3L, rev3);
        assertEquals(
            TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR,
            TermuxAppearancePreferences.getCursorStyle(context)
        );

        long rev4 = TermuxAppearancePreferences.setShowTopTabBar(context, true);
        assertEquals(4L, rev4);
        assertTrue(TermuxAppearancePreferences.shouldShowTopTabBar(context));
    }

    @Test
    public void testSetSelectedThemeIdSyncsNightModeForDarkAndLightThemes() {
        File missingColors = new File(tempFolder.getRoot(), "missing_colors.properties");

        TermuxColorPalette latte = TermuxThemeManager.setSelectedThemeId(
            context, TermuxColorPalette.THEME_CATPPUCCIN_LATTE, missingColors);
        assertTrue(latte.isLight);
        assertEquals(NightMode.FALSE, NightMode.getAppNightMode());

        TermuxColorPalette dracula = TermuxThemeManager.setSelectedThemeId(
            context, TermuxColorPalette.THEME_DRACULA, missingColors);
        assertFalse(dracula.isLight);
        assertEquals(NightMode.TRUE, NightMode.getAppNightMode());
    }

    @Test
    public void testGetActivePalettePreservesCustomColorsFileUntouched() throws Exception {
        File customColors = tempFolder.newFile("colors.properties");
        byte[] raw = "background=#0B0E14\nforeground=#BFBDB6\ncursor=#E6B450\n"
            .getBytes(StandardCharsets.UTF_8);
        Files.write(customColors.toPath(), raw);

        // Fresh upgrade state (no theme ID set in preferences) -> loads custom file
        TermuxColorPalette active = TermuxThemeManager.getActivePalette(context, customColors);
        assertEquals(TermuxColorPalette.THEME_CUSTOM, active.id);
        assertEquals(0xFF0B0E14, active.terminalBackground);
        assertEquals(0xFFBFBDB6, active.terminalForeground);
        assertEquals(0xFFE6B450, active.terminalCursor);

        // Switching to a built-in preset leaves custom colors.properties intact on disk
        TermuxColorPalette nord = TermuxThemeManager.setSelectedThemeId(
            context, TermuxColorPalette.THEME_NORD, customColors);
        assertEquals(TermuxColorPalette.THEME_NORD, nord.id);
        assertTrue(customColors.isFile());
        assertArrayEquals(raw, Files.readAllBytes(customColors.toPath()));
    }

}
