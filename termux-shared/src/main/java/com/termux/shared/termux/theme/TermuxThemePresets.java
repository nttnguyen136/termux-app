package com.termux.shared.termux.theme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Canonical definitions of the 7 built-in developer color palettes and pure-Java derivation
 * of Material 3 tokens from custom {@code ~/.termux/colors.properties} files.
 * <p>
 * Pure Java (no Android framework dependencies) so all palette, contrast, and parsing logic
 * is directly testable on the JVM.
 */
public final class TermuxThemePresets {

    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int DARK_TEXT_CANDIDATE = 0xFF11111B;
    private static final int LIGHT_TEXT_CANDIDATE = 0xFFF8F8F2;
    private static final double LIGHT_BG_LUMINANCE_THRESHOLD = 0.25;
    private static final double WCAG_AA_TEXT_CONTRAST = 4.5;
    private static final double WCAG_UI_COMPONENT_CONTRAST = 3.0;

    private static final TermuxColorPalette CATPPUCCIN_MOCHA = buildBuiltIn(
        TermuxColorPalette.THEME_CATPPUCCIN_MOCHA,
        "Catppuccin Mocha",
        false,
        0xFF1E1E2E, 0xFFCDD6F4, 0xFFF5E0DC,
        new int[]{
            0xFF45475A, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF,
            0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFBAC2DE,
            0xFF585B70, 0xFFF38BA8, 0xFFA6E3A1, 0xFFF9E2AF,
            0xFF89B4FA, 0xFFF5C2E7, 0xFF94E2D5, 0xFFA6ADC8
        },
        0xFF181825, 0xFF1E1E2E, 0xFF313244, 0xFF181825, 0xFF313244,
        0xFF89B4FA, 0xFF11111B, 0xFFCDD6F4, 0xFFA6ADC8, 0xFF45475A,
        0xFFF38BA8, 0xFF11111B
    );

    private static final TermuxColorPalette TOKYO_NIGHT = buildBuiltIn(
        TermuxColorPalette.THEME_TOKYO_NIGHT,
        "Tokyo Night",
        false,
        0xFF1A1B26, 0xFFC0CAF5, 0xFFC0CAF5,
        new int[]{
            0xFF15161E, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68,
            0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFA9B1D6,
            0xFF414868, 0xFFF7768E, 0xFF9ECE6A, 0xFFE0AF68,
            0xFF7AA2F7, 0xFFBB9AF7, 0xFF7DCFFF, 0xFFC0CAF5
        },
        0xFF16161E, 0xFF1F2335, 0xFF292E42, 0xFF16161E, 0xFF292E42,
        0xFF7AA2F7, 0xFF15161E, 0xFFC0CAF5, 0xFF9AA5CE, 0xFF414868,
        0xFFF7768E, 0xFF15161E
    );

    private static final TermuxColorPalette DRACULA = buildBuiltIn(
        TermuxColorPalette.THEME_DRACULA,
        "Dracula",
        false,
        0xFF282A36, 0xFFF8F8F2, 0xFFF8F8F2,
        new int[]{
            0xFF21222C, 0xFFFF5555, 0xFF50FA7B, 0xFFF1FA8C,
            0xFFBD93F9, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF8F8F2,
            0xFF6272A4, 0xFFFF6E6E, 0xFF69FF94, 0xFFFFFFA5,
            0xFFD6ACFF, 0xFFFF92DF, 0xFFA4FFFF, 0xFFFFFFFF
        },
        0xFF21222C, 0xFF282A36, 0xFF383A59, 0xFF21222C, 0xFF383A59,
        0xFFBD93F9, 0xFF21222C, 0xFFF8F8F2, 0xFFBFBFBF, 0xFF44475A,
        0xFFFF5555, 0xFF21222C
    );

    private static final TermuxColorPalette NORD = buildBuiltIn(
        TermuxColorPalette.THEME_NORD,
        "Nord",
        false,
        0xFF2E3440, 0xFFD8DEE9, 0xFFD8DEE9,
        new int[]{
            0xFF3B4252, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B,
            0xFF81A1C1, 0xFFB48EAD, 0xFF88C0D0, 0xFFE5E9F0,
            0xFF4C566A, 0xFFBF616A, 0xFFA3BE8C, 0xFFEBCB8B,
            0xFF81A1C1, 0xFFB48EAD, 0xFF8FBCBB, 0xFFECEFF4
        },
        0xFF272C36, 0xFF3B4252, 0xFF434C5E, 0xFF272C36, 0xFF3B4252,
        0xFF88C0D0, 0xFF2E3440, 0xFFECEFF4, 0xFFD8DEE9, 0xFF4C566A,
        0xFFBF616A, 0xFFECEFF4
    );

    private static final TermuxColorPalette GRUVBOX_DARK = buildBuiltIn(
        TermuxColorPalette.THEME_GRUVBOX_DARK,
        "Gruvbox Dark",
        false,
        0xFF282828, 0xFFEBDBB2, 0xFFEBDBB2,
        new int[]{
            0xFF282828, 0xFFCC241D, 0xFF98971A, 0xFFD79921,
            0xFF458588, 0xFFB16286, 0xFF689D6A, 0xFFA89984,
            0xFF928374, 0xFFFB4934, 0xFFB8BB26, 0xFFFABD2F,
            0xFF83A598, 0xFFD3869B, 0xFF8EC07C, 0xFFEBDBB2
        },
        0xFF1D2021, 0xFF282828, 0xFF3C3836, 0xFF1D2021, 0xFF3C3836,
        0xFFFABD2F, 0xFF1D2021, 0xFFEBDBB2, 0xFFBDAE93, 0xFF504945,
        0xFFFB4934, 0xFF1D2021
    );

    private static final TermuxColorPalette OLED_PITCH_BLACK = buildBuiltIn(
        TermuxColorPalette.THEME_OLED_PITCH_BLACK,
        "OLED Pitch Black",
        false,
        0xFF000000, 0xFFEAEAEA, 0xFF00E5FF,
        new int[]{
            0xFF1C1C1E, 0xFFFF5F56, 0xFF27C93F, 0xFFFFBD2E,
            0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFE5E5E5,
            0xFF666666, 0xFFFF7A73, 0xFF5AF78E, 0xFFF4F99D,
            0xFF6CB6FF, 0xFFE891E8, 0xFF61D6D6, 0xFFFFFFFF
        },
        0xFF000000, 0xFF0E0E10, 0xFF1C1C1E, 0xFF08080A, 0xFF1A1A1E,
        0xFF00E5FF, 0xFF000000, 0xFFEAEAEA, 0xFFA1A1AA, 0xFF2C2C30,
        0xFFFF5F56, 0xFF000000
    );

    private static final TermuxColorPalette CATPPUCCIN_LATTE = buildBuiltIn(
        TermuxColorPalette.THEME_CATPPUCCIN_LATTE,
        "Catppuccin Latte",
        true,
        0xFFEFF1F5, 0xFF4C4F69, 0xFFDC8A78,
        new int[]{
            0xFF5C5F77, 0xFFD20F39, 0xFF40A02B, 0xFFDF8E1D,
            0xFF1E66F5, 0xFFEA76CB, 0xFF179299, 0xFFACB0BE,
            0xFF6C6F85, 0xFFD20F39, 0xFF40A02B, 0xFFDF8E1D,
            0xFF1E66F5, 0xFFEA76CB, 0xFF179299, 0xFFBCC0CC
        },
        0xFFE6E9EF, 0xFFEFF1F5, 0xFFDCE0E8, 0xFFE6E9EF, 0xFFDCE0E8,
        0xFF1E66F5, 0xFFFFFFFF, 0xFF4C4F69, 0xFF6C6F85, 0xFFBCC0CC,
        0xFFD20F39, 0xFFFFFFFF
    );

    private static final Map<String, TermuxColorPalette> BUILT_IN_BY_ID;
    private static final List<TermuxColorPalette> BUILT_IN_LIST;

    static {
        Map<String, TermuxColorPalette> map = new LinkedHashMap<>();
        map.put(CATPPUCCIN_MOCHA.id, CATPPUCCIN_MOCHA);
        map.put(TOKYO_NIGHT.id, TOKYO_NIGHT);
        map.put(DRACULA.id, DRACULA);
        map.put(NORD.id, NORD);
        map.put(GRUVBOX_DARK.id, GRUVBOX_DARK);
        map.put(OLED_PITCH_BLACK.id, OLED_PITCH_BLACK);
        map.put(CATPPUCCIN_LATTE.id, CATPPUCCIN_LATTE);
        BUILT_IN_BY_ID = Collections.unmodifiableMap(map);
        BUILT_IN_LIST = Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    private TermuxThemePresets() {
    }

    private static TermuxColorPalette buildBuiltIn(
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
        int accent,
        int onAccent,
        int onSurface,
        int onSurfaceVariant,
        int outline,
        int error,
        int onError
    ) {
        int keycapLockedBg = blend(accent, isLight ? COLOR_BLACK : COLOR_WHITE, 0.18f);
        int keycapLockedText = pickOnColor(keycapLockedBg, DARK_TEXT_CANDIDATE, COLOR_WHITE);
        return new TermuxColorPalette(
            id,
            displayName,
            isLight,
            terminalBackground,
            terminalForeground,
            terminalCursor,
            ansiColors,
            surface,
            surfaceContainer,
            surfaceContainerHigh,
            toolbarSurface,
            keycapBackground,
            onSurface,
            accent,
            onAccent,
            keycapLockedBg,
            keycapLockedText,
            accent,
            onAccent,
            onSurface,
            onSurfaceVariant,
            outline,
            error,
            onError,
            surface,
            surface
        );
    }

    /**
     * Returns all 7 built-in developer color palettes in canonical presentation order.
     */
    @NonNull
    public static List<TermuxColorPalette> getBuiltInPalettes() {
        return BUILT_IN_LIST;
    }

    /**
     * Returns the built-in palette matching {@code id}, or {@link TermuxColorPalette#THEME_CATPPUCCIN_MOCHA}
     * if {@code id} is null or unrecognized.
     */
    @NonNull
    public static TermuxColorPalette getBuiltInById(@Nullable String id) {
        if (id == null) return CATPPUCCIN_MOCHA;
        TermuxColorPalette found = BUILT_IN_BY_ID.get(id);
        return found != null ? found : CATPPUCCIN_MOCHA;
    }

    public static boolean isBuiltInThemeId(@Nullable String id) {
        return id != null && BUILT_IN_BY_ID.containsKey(id);
    }

    /**
     * Builds a {@link TermuxColorPalette} from custom {@code colors.properties} entries,
     * deriving contrast-safe Material 3 surface, keycap, accent, error, and system bar tokens.
     * Falls back to Catppuccin Mocha when {@code props} is null, empty, or contains no valid color entries.
     */
    @NonNull
    public static TermuxColorPalette fromCustomProperties(@Nullable Properties props) {
        if (props == null || props.isEmpty()) {
            return CATPPUCCIN_MOCHA;
        }

        int parsedBg = 0;
        int parsedFg = 0;
        int parsedCursor = 0;
        int[] parsedAnsi = new int[TermuxColorPalette.ANSI_COLOR_COUNT];
        boolean[] hasAnsi = new boolean[TermuxColorPalette.ANSI_COLOR_COUNT];
        int validCount = 0;

        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                continue;
            }
            String key = ((String) entry.getKey()).trim();
            String value = ((String) entry.getValue()).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }

            int color = parseTerminalColor(value);
            if (color == 0) {
                continue;
            }

            if ("background".equals(key)) {
                parsedBg = color;
                validCount++;
            } else if ("foreground".equals(key)) {
                parsedFg = color;
                validCount++;
            } else if ("cursor".equals(key)) {
                parsedCursor = color;
                validCount++;
            } else if (key.startsWith("color")) {
                try {
                    int idx = Integer.parseInt(key.substring(5));
                    if (idx >= 0 && idx < TermuxColorPalette.ANSI_COLOR_COUNT) {
                        parsedAnsi[idx] = color;
                        hasAnsi[idx] = true;
                        validCount++;
                    } else if (idx >= TermuxColorPalette.ANSI_COLOR_COUNT && idx <= 255) {
                        // Valid extended xterm color property in user's colors.properties
                        validCount++;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed color index keys
                }
            }
        }

        if (validCount == 0) {
            return CATPPUCCIN_MOCHA;
        }

        int bg;
        if (parsedBg != 0) {
            bg = parsedBg;
        } else if (parsedFg != 0 && relativeLuminance(parsedFg) < 0.35) {
            bg = CATPPUCCIN_LATTE.terminalBackground;
        } else {
            bg = CATPPUCCIN_MOCHA.terminalBackground;
        }

        boolean isLight = relativeLuminance(bg) > LIGHT_BG_LUMINANCE_THRESHOLD;
        TermuxColorPalette baseDefaults = isLight ? CATPPUCCIN_LATTE : CATPPUCCIN_MOCHA;

        int fg = parsedFg != 0 ? parsedFg : baseDefaults.terminalForeground;

        int cursor;
        if (parsedCursor != 0) {
            cursor = parsedCursor;
        } else if (contrastRatio(fg, bg) >= WCAG_AA_TEXT_CONTRAST) {
            cursor = fg;
        } else {
            cursor = isLight ? COLOR_BLACK : COLOR_WHITE;
        }

        int[] ansi = new int[TermuxColorPalette.ANSI_COLOR_COUNT];
        for (int i = 0; i < TermuxColorPalette.ANSI_COLOR_COUNT; i++) {
            ansi[i] = hasAnsi[i] ? parsedAnsi[i] : baseDefaults.ansiColors[i];
        }

        int surface;
        int surfaceContainer;
        int surfaceContainerHigh;
        int toolbarSurface;
        int keycapBackground;

        if (isLight) {
            surface = blend(bg, COLOR_BLACK, 0.04f);
            surfaceContainer = bg;
            surfaceContainerHigh = blend(bg, COLOR_BLACK, 0.08f);
            toolbarSurface = surface;
            keycapBackground = surfaceContainerHigh;
        } else {
            surface = blend(bg, COLOR_BLACK, 0.20f);
            surfaceContainer = blend(bg, COLOR_WHITE, 0.05f);
            surfaceContainerHigh = blend(bg, COLOR_WHITE, 0.12f);
            toolbarSurface = blend(bg, COLOR_WHITE, 0.03f);
            keycapBackground = surfaceContainerHigh;
        }

        int onSurface;
        if (contrastRatio(fg, surfaceContainer) >= WCAG_AA_TEXT_CONTRAST
            && contrastRatio(fg, surface) >= WCAG_AA_TEXT_CONTRAST) {
            onSurface = fg;
        } else {
            onSurface = pickOnColor(surfaceContainer, DARK_TEXT_CANDIDATE, LIGHT_TEXT_CANDIDATE);
        }

        int onSurfaceVariant;
        int variant25 = blend(onSurface, surfaceContainer, 0.25f);
        int variant10 = blend(onSurface, surfaceContainer, 0.10f);
        if (contrastRatio(variant25, surfaceContainer) >= WCAG_AA_TEXT_CONTRAST
            && contrastRatio(variant25, surface) >= WCAG_AA_TEXT_CONTRAST) {
            onSurfaceVariant = variant25;
        } else if (contrastRatio(variant10, surfaceContainer) >= WCAG_AA_TEXT_CONTRAST
            && contrastRatio(variant10, surface) >= WCAG_AA_TEXT_CONTRAST) {
            onSurfaceVariant = variant10;
        } else {
            onSurfaceVariant = onSurface;
        }

        int outline = blend(bg, fg, 0.25f);

        int accent = pickFirstContrasting(
            new int[]{ansi[4], ansi[12], ansi[6], ansi[5], baseDefaults.accent},
            surfaceContainer,
            surface,
            WCAG_UI_COMPONENT_CONTRAST,
            baseDefaults.accent
        );
        int onAccent = pickOnColor(accent, DARK_TEXT_CANDIDATE, COLOR_WHITE);

        int error = pickFirstContrasting(
            new int[]{ansi[1], ansi[9], baseDefaults.error},
            surfaceContainer,
            surface,
            WCAG_UI_COMPONENT_CONTRAST,
            baseDefaults.error
        );
        int onError = pickOnColor(error, DARK_TEXT_CANDIDATE, COLOR_WHITE);

        int keycapText = contrastRatio(onSurface, keycapBackground) >= WCAG_AA_TEXT_CONTRAST
            ? onSurface
            : pickOnColor(keycapBackground, DARK_TEXT_CANDIDATE, COLOR_WHITE);
        int keycapLockedBg = blend(accent, isLight ? COLOR_BLACK : COLOR_WHITE, 0.18f);
        int keycapLockedText = pickOnColor(keycapLockedBg, DARK_TEXT_CANDIDATE, COLOR_WHITE);

        return new TermuxColorPalette(
            TermuxColorPalette.THEME_CUSTOM,
            "Custom",
            isLight,
            bg,
            fg,
            cursor,
            ansi,
            surface,
            surfaceContainer,
            surfaceContainerHigh,
            toolbarSurface,
            keycapBackground,
            keycapText,
            accent,
            onAccent,
            keycapLockedBg,
            keycapLockedText,
            accent,
            onAccent,
            onSurface,
            onSurfaceVariant,
            outline,
            error,
            onError,
            surface,
            surface
        );
    }

    /**
     * Pure-Java parser matching {@code TerminalColors.parse(String)} semantics:
     * supports {@code #RGB}, {@code #RRGGBB}, {@code #RRRGGGBBB}, {@code #RRRRGGGGBBBB},
     * and {@code rgb:<r>/<g>/<b>} with 1-4 hex digits per channel.
     *
     * @return {@code 0xFFRRGGBB} on success, or {@code 0} if invalid.
     */
    public static int parseTerminalColor(@Nullable String raw) {
        if (raw == null) return 0;
        String c = raw.trim();
        if (c.isEmpty()) return 0;

        try {
            if (c.charAt(0) == '#') {
                String hex = c.substring(1);
                int len = hex.length();
                if (len != 3 && len != 6 && len != 9 && len != 12) {
                    return 0;
                }
                int componentLen = len / 3;
                double mult = 255.0 / (Math.pow(2.0, componentLen * 4) - 1.0);
                int r = parseHexComponent(hex.substring(0, componentLen), mult);
                int g = parseHexComponent(hex.substring(componentLen, componentLen * 2), mult);
                int b = parseHexComponent(hex.substring(componentLen * 2), mult);
                if (r < 0 || g < 0 || b < 0) return 0;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            } else if (c.startsWith("rgb:")) {
                String[] parts = c.substring(4).split("/", -1);
                if (parts.length != 3) return 0;
                int r = parseScaledRgbPart(parts[0]);
                int g = parseScaledRgbPart(parts[1]);
                int b = parseScaledRgbPart(parts[2]);
                if (r < 0 || g < 0 || b < 0) return 0;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
            return 0;
        }
        return 0;
    }

    private static int parseHexComponent(@NonNull String hex, double mult) {
        if (!isAllHexDigits(hex)) return -1;
        int parsed = Integer.parseInt(hex, 16);
        return clampByte((int) Math.round(parsed * mult));
    }

    private static int parseScaledRgbPart(@NonNull String part) {
        int len = part.length();
        if (len < 1 || len > 4 || !isAllHexDigits(part)) return -1;
        double mult = 255.0 / (Math.pow(2.0, len * 4) - 1.0);
        return clampByte((int) Math.round(Integer.parseInt(part, 16) * mult));
    }

    private static boolean isAllHexDigits(@NonNull String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean hex = (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    private static int clampByte(int value) {
        if (value < 0) return 0;
        if (value > 255) return 255;
        return value;
    }

    /**
     * Pure-Java WCAG 2.1 relative luminance in {@code [0.0, 1.0]}.
     */
    public static double relativeLuminance(int argb) {
        double r = linearize(((argb >>> 16) & 0xFF) / 255.0);
        double g = linearize(((argb >>> 8) & 0xFF) / 255.0);
        double b = linearize((argb & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    /**
     * Pure-Java WCAG 2.1 contrast ratio in {@code [1.0, 21.0]}.
     */
    public static double contrastRatio(int argb1, int argb2) {
        double l1 = relativeLuminance(argb1);
        double l2 = relativeLuminance(argb2);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    /**
     * Linearly blends {@code fromArgb} toward {@code toArgb} by {@code ratio} in {@code [0, 1]}.
     */
    public static int blend(int fromArgb, int toArgb, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        float inv = 1f - clamped;
        int r = clampByte(Math.round(((fromArgb >>> 16) & 0xFF) * inv + ((toArgb >>> 16) & 0xFF) * clamped));
        int g = clampByte(Math.round(((fromArgb >>> 8) & 0xFF) * inv + ((toArgb >>> 8) & 0xFF) * clamped));
        int b = clampByte(Math.round((fromArgb & 0xFF) * inv + (toArgb & 0xFF) * clamped));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Picks the higher-contrast candidate between {@code darkCandidate} and {@code lightCandidate}
     * against {@code background}, falling back to pure black/white if needed to guarantee WCAG AA
     * ({@code >= 4.5:1}) legibility.
     */
    public static int pickOnColor(int background, int darkCandidate, int lightCandidate) {
        double darkContrast = contrastRatio(background, darkCandidate);
        double lightContrast = contrastRatio(background, lightCandidate);
        int preferred = darkContrast >= lightContrast ? darkCandidate : lightCandidate;
        if (Math.max(darkContrast, lightContrast) >= WCAG_AA_TEXT_CONTRAST) {
            return preferred;
        }
        double blackContrast = contrastRatio(background, COLOR_BLACK);
        double whiteContrast = contrastRatio(background, COLOR_WHITE);
        return blackContrast >= whiteContrast ? COLOR_BLACK : COLOR_WHITE;
    }

    private static int pickFirstContrasting(
        @NonNull int[] candidates,
        int surfaceA,
        int surfaceB,
        double minContrast,
        int fallback
    ) {
        for (int candidate : candidates) {
            if (contrastRatio(candidate, surfaceA) >= minContrast
                && contrastRatio(candidate, surfaceB) >= minContrast) {
                return candidate;
            }
        }
        if (contrastRatio(fallback, surfaceA) >= minContrast
            && contrastRatio(fallback, surfaceB) >= minContrast) {
            return fallback;
        }
        return pickOnColor(surfaceA, DARK_TEXT_CANDIDATE, COLOR_WHITE);
    }

}
