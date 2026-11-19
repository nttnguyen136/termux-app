package com.termux.shared.termux.theme;

import com.termux.terminal.TerminalColorScheme;
import com.termux.terminal.TextStyle;

import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM unit tests for {@link TermuxColorPalette} and {@link TermuxThemePresets}.
 * Verifies all 7 canonical built-in developer color schemes, Material 3 UI tokens,
 * WCAG contrast ratios, {@link TerminalColorScheme} round-trip updates, and custom
 * {@code colors.properties} parsing/derivation/fallbacks.
 */
public class TermuxThemePresetsTest {

    @Test
    public void testBuiltInPalettesCountAndCanonicalOrder() {
        List<TermuxColorPalette> palettes = TermuxThemePresets.getBuiltInPalettes();
        assertEquals(7, palettes.size());

        assertEquals(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA, palettes.get(0).id);
        assertEquals("Catppuccin Mocha", palettes.get(0).displayName);
        assertFalse(palettes.get(0).isLight);

        assertEquals(TermuxColorPalette.THEME_TOKYO_NIGHT, palettes.get(1).id);
        assertEquals("Tokyo Night", palettes.get(1).displayName);
        assertFalse(palettes.get(1).isLight);

        assertEquals(TermuxColorPalette.THEME_DRACULA, palettes.get(2).id);
        assertEquals("Dracula", palettes.get(2).displayName);
        assertFalse(palettes.get(2).isLight);

        assertEquals(TermuxColorPalette.THEME_NORD, palettes.get(3).id);
        assertEquals("Nord", palettes.get(3).displayName);
        assertFalse(palettes.get(3).isLight);

        assertEquals(TermuxColorPalette.THEME_GRUVBOX_DARK, palettes.get(4).id);
        assertEquals("Gruvbox Dark", palettes.get(4).displayName);
        assertFalse(palettes.get(4).isLight);

        assertEquals(TermuxColorPalette.THEME_OLED_PITCH_BLACK, palettes.get(5).id);
        assertEquals("OLED Pitch Black", palettes.get(5).displayName);
        assertFalse(palettes.get(5).isLight);

        assertEquals(TermuxColorPalette.THEME_CATPPUCCIN_LATTE, palettes.get(6).id);
        assertEquals("Catppuccin Latte", palettes.get(6).displayName);
        assertTrue(palettes.get(6).isLight);
    }

    @Test
    public void testCanonicalHexTokensForAllSevenBuiltInPalettes() {
        // 1. Catppuccin Mocha (default)
        assertPaletteTokens(
            TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA),
            "catppuccin_mocha", "Catppuccin Mocha", false,
            0xFF1E1E2E, 0xFFCDD6F4, 0xFFF5E0DC,
            new int[]{
                0xFF45475A, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF,
                0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFBAC2DE,
                0xFF585B70, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF,
                0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFA6ADC8
            },
            0xFF181825, 0xFF1E1E2E, 0xFF313244, 0xFF181825, 0xFF313244,
            0xFF89B4FA, 0xFF11111B, 0xFFCDD6F4, 0xFFA6ADC8, 0xFF45475A, 0xFFF38BA8, 0xFF11111B
        );

        // 2. Tokyo Night
        assertPaletteTokens(
            TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_TOKYO_NIGHT),
            "tokyo_night", "Tokyo Night", false,
            0xFF1A1B26, 0xFFC0CAF5, 0xFFC0CAF5,
            new int[]{
                0xFF15161E, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68,
                0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFA9B1D6,
                0xFF414868, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68,
                0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFC0CAF5
            },
            0xFF16161E, 0xFF1F2335, 0xFF292E42, 0xFF16161E, 0xFF292E42,
            0xFF7AA2F7, 0xFF15161E, 0xFFC0CAF5, 0xFF9AA5CE, 0xFF414868, 0xFFF7768E, 0xFF15161E
        );

        // 3. Dracula
        assertPaletteTokens(
            TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_DRACULA),
            "dracula", "Dracula", false,
            0xFF282A36, 0xFFF8F8F2, 0xFFF8F8F2,
            new int[]{
                0xFF21222C, 0xFFFF5555, 0xFF50FA7B, 0xFFF1FA8C,
                0xFFBD93F9, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF8F8F2,
                0xFF6272A4, 0xFFFF6E6E, 0xFF69FF94, 0xFFFFFFA5,
                0xFFD6ACFF, 0xFFFF92DF, 0xFFA4FFFF, 0xFFFFFFFF
            },
            0xFF21222C, 0xFF282A36, 0xFF383A59, 0xFF21222C, 0xFF383A59,
            0xFFBD93F9, 0xFF21222C, 0xFFF8F8F2, 0xFFBFBFBF, 0xFF44475A, 0xFFFF5555, 0xFF21222C
        );

        // 4. Nord
        assertPaletteTokens(
            TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_NORD),
            "nord", "Nord", false,
            0xFF2E3440, 0xFFD8DEE9, 0xFFD8DEE9,
            new int[]{
                0xFF3B4252, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B,
                0xFF81A1C1, 0xFFB48EAD, 0xFF88C0D0, 0xFFE5E9F0,
                0xFF4C566A, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B,
                0xFF81A1C1, 0xFFB48EAD, 0xFF8FBCBB, 0xFFECEFF4
            },
            0xFF272C36, 0xFF3B4252, 0xFF434C5E, 0xFF272C36, 0xFF3B4252,
            0xFF88C0D0, 0xFF2E3440, 0xFFECEFF4, 0xFFD8DEE9, 0xFF4C566A, 0xFFBF616A, 0xFFECEFF4
        );

        // 5. Gruvbox Dark
        assertPaletteTokens(
            TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_GRUVBOX_DARK),
            "gruvbox_dark", "Gruvbox Dark", false,
            0xFF282828, 0xFFEBDBB2, 0xFFEBDBB2,
            new int[]{
                0xFF282828, 0xFFCC241D, 0xFF98971A, 0xFFD79921,
                0xFF458588, 0xFFB16286, 0xFF689D6A, 0xFFA89984,
                0xFF928374, 0xFFFB4934, 0xFFB8BB26, 0xFFFABD2F,
                0xFF83A598, 0xFFD3869B, 0xFF8EC07C, 0xFFEBDBB2
            },
            0xFF1D2021, 0xFF282828, 0xFF3C3836, 0xFF1D2021, 0xFF3C3836,
            0xFFFABD2F, 0xFF1D2021, 0xFFEBDBB2, 0xFFBDAE93, 0xFF504945, 0xFFFB4934, 0xFF1D2021
        );

        // 6. OLED Pitch Black
        assertPaletteTokens(
            TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_OLED_PITCH_BLACK),
            "oled_pitch_black", "OLED Pitch Black", false,
            0xFF000000, 0xFFEAEAEA, 0xFF00E5FF,
            new int[]{
                0xFF1C1C1E, 0xFFFF5F56, 0xFF27C93F, 0xFFFFBD2E,
                0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFE5E5E5,
                0xFF666666, 0xFFFF7A73, 0xFF5AF78E, 0xFFF4F99D,
                0xFF6CB6FF, 0xFFE891E8, 0xFF61D6D6, 0xFFFFFFFF
            },
            0xFF000000, 0xFF0E0E10, 0xFF1C1C1E, 0xFF08080A, 0xFF1A1A1E,
            0xFF00E5FF, 0xFF000000, 0xFFEAEAEA, 0xFFA1A1AA, 0xFF2C2C30, 0xFFFF5F56, 0xFF000000
        );

        // 7. Catppuccin Latte (Light)
        assertPaletteTokens(
            TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_LATTE),
            "catppuccin_latte", "Catppuccin Latte", true,
            0xFFEFF1F5, 0xFF4C4F69, 0xFFDC8A78,
            new int[]{
                0xFF5C5F77, 0xFFD20F39, 0xFF40A02B, 0xFFDF8E1D,
                0xFF1E66F5, 0xFFEA76CB, 0xFF179299, 0xFFACB0BE,
                0xFF6C6F85, 0xFFD20F39, 0xFF40A02B, 0xFFDF8E1D,
                0xFF1E66F5, 0xFFEA76CB, 0xFF179299, 0xFFBCC0CC
            },
            0xFFE6E9EF, 0xFFEFF1F5, 0xFFDCE0E8, 0xFFE6E9EF, 0xFFDCE0E8,
            0xFF1E66F5, 0xFFFFFFFF, 0xFF4C4F69, 0xFF6C6F85, 0xFFBCC0CC, 0xFFD20F39, 0xFFFFFFFF
        );
    }

    @Test
    public void testGetBuiltInByIdFallbackOnUnknownOrNull() {
        TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        assertEquals(mocha.id, TermuxThemePresets.getBuiltInById(null).id);
        assertEquals(mocha.id, TermuxThemePresets.getBuiltInById("").id);
        assertEquals(mocha.id, TermuxThemePresets.getBuiltInById("nonexistent_theme").id);
    }

    @Test
    public void testToTerminalPropertiesRoundTripIntoTerminalColorScheme() {
        for (TermuxColorPalette palette : TermuxThemePresets.getBuiltInPalettes()) {
            Properties props = palette.toTerminalProperties();
            assertEquals(19, props.size());

            TerminalColorScheme scheme = new TerminalColorScheme();
            scheme.updateWith(props);

            assertEquals(palette.terminalForeground, scheme.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND]);
            assertEquals(palette.terminalBackground, scheme.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND]);
            assertEquals(palette.terminalCursor, scheme.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR]);
            for (int i = 0; i < TermuxColorPalette.ANSI_COLOR_COUNT; i++) {
                assertEquals("ANSI color " + i + " mismatch for " + palette.id,
                    palette.ansiColors[i], scheme.mDefaultColors[i]);
            }
        }
    }

    @Test
    public void testBuiltInPalettesWcagContrastInvariants() {
        for (TermuxColorPalette palette : TermuxThemePresets.getBuiltInPalettes()) {
            double fgBgContrast = TermuxThemePresets.contrastRatio(
                palette.terminalForeground, palette.terminalBackground);
            assertTrue("Terminal fg/bg contrast < 4.5 for " + palette.id + ": " + fgBgContrast,
                fgBgContrast >= 4.5);

            double cursorBgContrast = TermuxThemePresets.contrastRatio(
                palette.terminalCursor, palette.terminalBackground);
            assertTrue("Terminal cursor/bg contrast < 2.2 for " + palette.id + ": " + cursorBgContrast,
                cursorBgContrast >= 2.2);

            double onSurfaceContrast = TermuxThemePresets.contrastRatio(
                palette.onSurface, palette.surfaceContainer);
            assertTrue("onSurface/surfaceContainer contrast < 4.5 for " + palette.id,
                onSurfaceContrast >= 4.5);

            double onAccentContrast = TermuxThemePresets.contrastRatio(
                palette.onAccent, palette.accent);
            assertTrue("onAccent/accent contrast < 4.5 for " + palette.id,
                onAccentContrast >= 4.5);
        }
    }

    @Test
    public void testFromCustomPropertiesDarkAndLightDerivation() {
        // Dark custom properties with #RRGGBB, #RGB, and rgb:r/g/b syntax
        Properties darkProps = new Properties();
        darkProps.setProperty("background", "#10141C");
        darkProps.setProperty("foreground", "#E2E8F0");
        darkProps.setProperty("cursor", "rgb:ff/88/00");
        darkProps.setProperty("color4", "#38BDF8");
        darkProps.setProperty("color1", "#F43F5E");

        TermuxColorPalette darkCustom = TermuxThemePresets.fromCustomProperties(darkProps);
        assertEquals(TermuxColorPalette.THEME_CUSTOM, darkCustom.id);
        assertFalse(darkCustom.isLight);
        assertEquals(0xFF10141C, darkCustom.terminalBackground);
        assertEquals(0xFFE2E8F0, darkCustom.terminalForeground);
        assertEquals(0xFFFF8800, darkCustom.terminalCursor);
        assertEquals(0xFF38BDF8, darkCustom.ansiColors[4]);
        assertEquals(0xFFF43F5E, darkCustom.ansiColors[1]);
        assertTrue(TermuxThemePresets.contrastRatio(darkCustom.onSurface, darkCustom.surfaceContainer) >= 4.5);
        assertTrue(TermuxThemePresets.contrastRatio(darkCustom.onAccent, darkCustom.accent) >= 4.5);

        // Light custom properties
        Properties lightProps = new Properties();
        lightProps.setProperty("background", "#FAFAFA");
        lightProps.setProperty("foreground", "#1E293B");
        lightProps.setProperty("color4", "#2563EB");

        TermuxColorPalette lightCustom = TermuxThemePresets.fromCustomProperties(lightProps);
        assertEquals(TermuxColorPalette.THEME_CUSTOM, lightCustom.id);
        assertTrue(lightCustom.isLight);
        assertEquals(0xFFFAFAFA, lightCustom.terminalBackground);
        assertEquals(0xFF1E293B, lightCustom.terminalForeground);
        assertTrue(TermuxThemePresets.contrastRatio(lightCustom.onSurface, lightCustom.surfaceContainer) >= 4.5);
        assertTrue(TermuxThemePresets.contrastRatio(lightCustom.onAccent, lightCustom.accent) >= 4.5);
    }

    @Test
    public void testFromCustomPropertiesEmptyOrMalformedFallsBackToMocha() {
        assertEquals(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
            TermuxThemePresets.fromCustomProperties(null).id
        );
        assertEquals(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
            TermuxThemePresets.fromCustomProperties(new Properties()).id
        );

        Properties invalid = new Properties();
        invalid.setProperty("background", "not-a-color");
        invalid.setProperty("unknown_key", "#112233");
        invalid.setProperty("color999", "#445566");
        assertEquals(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
            TermuxThemePresets.fromCustomProperties(invalid).id
        );
    }

    @Test
    public void testParseTerminalColorFormats() {
        assertEquals(0xFF1E1E2E, TermuxThemePresets.parseTerminalColor("#1E1E2E"));
        assertEquals(0xFFFFFFFF, TermuxThemePresets.parseTerminalColor("#FFF"));
        assertEquals(0xFF000000, TermuxThemePresets.parseTerminalColor("rgb:00/00/00"));
        assertEquals(0xFF89B4FA, TermuxThemePresets.parseTerminalColor("  rgb:89/b4/fa "));
        assertEquals(0, TermuxThemePresets.parseTerminalColor(null));
        assertEquals(0, TermuxThemePresets.parseTerminalColor(""));
        assertEquals(0, TermuxThemePresets.parseTerminalColor("#12345"));
        assertEquals(0, TermuxThemePresets.parseTerminalColor("rgb:zz/00/00"));
    }

    private static void assertPaletteTokens(
        TermuxColorPalette p,
        String expectedId,
        String expectedName,
        boolean expectedLight,
        int expectedBg,
        int expectedFg,
        int expectedCursor,
        int[] expectedAnsi,
        int expectedSurface,
        int expectedSurfaceContainer,
        int expectedSurfaceContainerHigh,
        int expectedToolbarSurface,
        int expectedKeycapBg,
        int expectedAccent,
        int expectedOnAccent,
        int expectedOnSurface,
        int expectedOnSurfaceVariant,
        int expectedOutline,
        int expectedError,
        int expectedOnError
    ) {
        assertNotNull(p);
        assertEquals(expectedId, p.getId());
        assertEquals(expectedName, p.getDisplayName());
        assertEquals(expectedLight, p.isLight());
        assertEquals(expectedBg, p.getTerminalBackground());
        assertEquals(expectedFg, p.getTerminalForeground());
        assertEquals(expectedCursor, p.getTerminalCursor());
        assertArrayEquals(expectedAnsi, p.getAnsiColors());
        assertEquals(expectedSurface, p.getSurface());
        assertEquals(expectedSurfaceContainer, p.getSurfaceContainer());
        assertEquals(expectedSurfaceContainerHigh, p.getSurfaceContainerHigh());
        assertEquals(expectedToolbarSurface, p.getToolbarSurface());
        assertEquals(expectedKeycapBg, p.getKeycapBackground());
        assertEquals(expectedOnSurface, p.getKeycapText());
        assertEquals(expectedAccent, p.getKeycapActiveBackground());
        assertEquals(expectedOnAccent, p.getKeycapActiveText());
        assertEquals(expectedAccent, p.getAccent());
        assertEquals(expectedOnAccent, p.getOnAccent());
        assertEquals(expectedOnSurface, p.getOnSurface());
        assertEquals(expectedOnSurfaceVariant, p.getOnSurfaceVariant());
        assertEquals(expectedOutline, p.getOutline());
        assertEquals(expectedError, p.getError());
        assertEquals(expectedOnError, p.getOnError());
        assertEquals(expectedSurface, p.getStatusBarColor());
        assertEquals(expectedSurface, p.getNavigationBarColor());
    }

}
