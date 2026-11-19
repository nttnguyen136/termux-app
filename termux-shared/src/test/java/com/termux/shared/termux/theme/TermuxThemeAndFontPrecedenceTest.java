package com.termux.shared.termux.theme;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM unit tests verifying upgrade-safe theme and Nerd Font precedence
 * when custom {@code ~/.termux/colors.properties} or {@code ~/.termux/font.ttf} files exist.
 */
public class TermuxThemeAndFontPrecedenceTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testFreshInstallDefaultsToCatppuccinMochaAndJetBrainsMono() {
        File missingColors = new File(tempFolder.getRoot(), "missing_colors.properties");
        File missingFont = new File(tempFolder.getRoot(), "missing_font.ttf");

        assertEquals(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
            TermuxThemeManager.resolveEffectiveThemeId(null, missingColors)
        );
        assertEquals(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
            TermuxThemeManager.resolveEffectiveThemeId("", missingColors)
        );
        assertEquals(
            TermuxFontManager.FONT_JETBRAINS_MONO,
            TermuxFontManager.resolveEffectiveFontId(null, missingFont)
        );
        assertEquals(
            TermuxFontManager.FONT_JETBRAINS_MONO,
            TermuxFontManager.resolveEffectiveFontId("", missingFont)
        );
    }

    @Test
    public void testEmptyCustomFilesDoNotOverrideFreshInstallDefaults() throws Exception {
        File emptyColors = tempFolder.newFile("colors.properties");
        File emptyFont = tempFolder.newFile("font.ttf");
        assertEquals(0L, emptyColors.length());
        assertEquals(0L, emptyFont.length());

        assertEquals(
            TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
            TermuxThemeManager.resolveEffectiveThemeId(null, emptyColors)
        );
        assertEquals(
            TermuxFontManager.FONT_JETBRAINS_MONO,
            TermuxFontManager.resolveEffectiveFontId(null, emptyFont)
        );
    }

    @Test
    public void testUpgradeWithExistingCustomFilesAutoSelectsCustomAndNeverOverwrites() throws Exception {
        File customColors = tempFolder.newFile("colors.properties");
        byte[] colorBytes = "background=#12131A\nforeground=#EAEAEA\ncursor=#00FFAA\n"
            .getBytes(StandardCharsets.UTF_8);
        Files.write(customColors.toPath(), colorBytes);

        File customFont = tempFolder.newFile("font.ttf");
        byte[] fontBytes = new byte[]{0x00, 0x01, 0x00, 0x00, 0x10, 0x20, 0x30};
        Files.write(customFont.toPath(), fontBytes);

        // When no preference has ever been persisted (null), existing non-empty custom files win automatically
        assertEquals(
            TermuxColorPalette.THEME_CUSTOM,
            TermuxThemeManager.resolveEffectiveThemeId(null, customColors)
        );
        assertEquals(
            TermuxFontManager.FONT_CUSTOM,
            TermuxFontManager.resolveEffectiveFontId(null, customFont)
        );

        // Resolving palette reads custom colors accurately without touching file contents
        TermuxColorPalette resolved = TermuxThemeManager.resolvePalette(null, customColors);
        assertEquals(TermuxColorPalette.THEME_CUSTOM, resolved.id);
        assertEquals(0xFF12131A, resolved.terminalBackground);
        assertEquals(0xFFEAEAEA, resolved.terminalForeground);
        assertEquals(0xFF00FFAA, resolved.terminalCursor);

        // Explicit user selection of a built-in preset overrides custom files without deleting or modifying them
        assertEquals(
            TermuxColorPalette.THEME_DRACULA,
            TermuxThemeManager.resolveEffectiveThemeId(TermuxColorPalette.THEME_DRACULA, customColors)
        );
        assertEquals(
            TermuxFontManager.FONT_FIRA_CODE,
            TermuxFontManager.resolveEffectiveFontId(TermuxFontManager.FONT_FIRA_CODE, customFont)
        );

        TermuxColorPalette dracula = TermuxThemeManager.resolvePalette(
            TermuxColorPalette.THEME_DRACULA, customColors);
        assertEquals(TermuxColorPalette.THEME_DRACULA, dracula.id);

        assertTrue(customColors.isFile());
        assertTrue(customFont.isFile());
        assertArrayEquals(colorBytes, Files.readAllBytes(customColors.toPath()));
        assertArrayEquals(fontBytes, Files.readAllBytes(customFont.toPath()));
    }

    @Test
    public void testExplicitCustomSelectionWithMissingFileFallsBackSafelyToMocha() {
        File missingColors = new File(tempFolder.getRoot(), "nonexistent_colors.properties");
        assertEquals(
            TermuxColorPalette.THEME_CUSTOM,
            TermuxThemeManager.resolveEffectiveThemeId(TermuxColorPalette.THEME_CUSTOM, missingColors)
        );
        TermuxColorPalette fallback = TermuxThemeManager.resolvePalette(
            TermuxColorPalette.THEME_CUSTOM, missingColors);
        assertEquals(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA, fallback.id);
        assertFalse(missingColors.exists());
    }

    @Test
    public void testBuiltInFontDescriptors() {
        List<TermuxFontManager.FontDescriptor> fonts = TermuxFontManager.getBuiltInFonts();
        assertEquals(3, fonts.size());

        assertEquals(TermuxFontManager.FONT_JETBRAINS_MONO, fonts.get(0).getId());
        assertEquals("JetBrainsMono Nerd Font Mono", fonts.get(0).getDisplayName());
        assertEquals("fonts/JetBrainsMonoNerdFontMono-Regular.ttf", fonts.get(0).getAssetPath());

        assertEquals(TermuxFontManager.FONT_MESLO_LGS, fonts.get(1).getId());
        assertEquals("MesloLGS Nerd Font Mono", fonts.get(1).getDisplayName());
        assertEquals("fonts/MesloLGSNerdFontMono-Regular.ttf", fonts.get(1).getAssetPath());

        assertEquals(TermuxFontManager.FONT_FIRA_CODE, fonts.get(2).getId());
        assertEquals("FiraCode Nerd Font Mono", fonts.get(2).getDisplayName());
        assertEquals("fonts/FiraCodeNerdFontMono-Regular.ttf", fonts.get(2).getAssetPath());
    }

}
