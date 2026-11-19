package com.termux.shared.termux.theme;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the 3 bundled Mono Nerd Fonts and upgrade-safe custom {@code ~/.termux/font.ttf} loading.
 */
public final class TermuxFontManager {

    public static final String FONT_JETBRAINS_MONO = "jetbrains_mono";
    public static final String FONT_MESLO_LGS = "meslo_lgs";
    public static final String FONT_FIRA_CODE = "fira_code";
    public static final String FONT_CUSTOM = "custom";

    public static final String ASSET_JETBRAINS_MONO = "fonts/JetBrainsMonoNerdFontMono-Regular.ttf";
    public static final String ASSET_MESLO_LGS = "fonts/MesloLGSNerdFontMono-Regular.ttf";
    public static final String ASSET_FIRA_CODE = "fonts/FiraCodeNerdFontMono-Regular.ttf";

    /**
     * Immutable descriptor for a bundled Mono Nerd Font.
     */
    public static final class FontDescriptor {
        @NonNull public final String id;
        @NonNull public final String displayName;
        @NonNull public final String assetPath;

        public FontDescriptor(@NonNull String id, @NonNull String displayName, @NonNull String assetPath) {
            this.id = id;
            this.displayName = displayName;
            this.assetPath = assetPath;
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public String getDisplayName() {
            return displayName;
        }

        @NonNull
        public String getAssetPath() {
            return assetPath;
        }
    }

    private static final FontDescriptor DESC_JETBRAINS_MONO = new FontDescriptor(
        FONT_JETBRAINS_MONO,
        "JetBrainsMono Nerd Font Mono",
        ASSET_JETBRAINS_MONO
    );

    private static final FontDescriptor DESC_MESLO_LGS = new FontDescriptor(
        FONT_MESLO_LGS,
        "MesloLGS Nerd Font Mono",
        ASSET_MESLO_LGS
    );

    private static final FontDescriptor DESC_FIRA_CODE = new FontDescriptor(
        FONT_FIRA_CODE,
        "FiraCode Nerd Font Mono",
        ASSET_FIRA_CODE
    );

    private static final Map<String, FontDescriptor> BUILT_IN_BY_ID;
    private static final List<FontDescriptor> BUILT_IN_LIST;
    private static final Map<String, Typeface> ASSET_TYPEFACE_CACHE = new ConcurrentHashMap<>();

    static {
        Map<String, FontDescriptor> map = new LinkedHashMap<>();
        map.put(DESC_JETBRAINS_MONO.id, DESC_JETBRAINS_MONO);
        map.put(DESC_MESLO_LGS.id, DESC_MESLO_LGS);
        map.put(DESC_FIRA_CODE.id, DESC_FIRA_CODE);
        BUILT_IN_BY_ID = Collections.unmodifiableMap(map);
        BUILT_IN_LIST = Collections.unmodifiableList(new ArrayList<>(map.values()));
    }

    private TermuxFontManager() {
    }

    @NonNull
    public static List<FontDescriptor> getBuiltInFonts() {
        return BUILT_IN_LIST;
    }

    @NonNull
    public static FontDescriptor getBuiltInById(@Nullable String id) {
        if (id == null) return DESC_JETBRAINS_MONO;
        FontDescriptor found = BUILT_IN_BY_ID.get(id);
        return found != null ? found : DESC_JETBRAINS_MONO;
    }

    public static boolean isBuiltInFontId(@Nullable String id) {
        return id != null && BUILT_IN_BY_ID.containsKey(id);
    }

    /**
     * Pure-Java upgrade-safe font precedence resolver:
     * <ul>
     *   <li>If {@code persistedFontId} is null/empty and {@code customFontFile} exists with non-zero length,
     *       returns {@link #FONT_CUSTOM} (preserving existing user {@code ~/.termux/font.ttf} on upgrade).</li>
     *   <li>If {@code persistedFontId} is null/empty and no non-empty custom font file exists,
     *       returns {@link #FONT_JETBRAINS_MONO} (fresh install default).</li>
     *   <li>If {@code persistedFontId} is {@link #FONT_CUSTOM} or a valid built-in font ID, returns that ID.</li>
     *   <li>Otherwise falls back to {@link #FONT_JETBRAINS_MONO}.</li>
     * </ul>
     */
    @NonNull
    public static String resolveEffectiveFontId(@Nullable String persistedFontId, @Nullable File customFontFile) {
        String normalized = persistedFontId != null ? persistedFontId.trim() : null;
        if (normalized == null || normalized.isEmpty()) {
            if (isValidCustomFontFile(customFontFile)) {
                return FONT_CUSTOM;
            }
            return FONT_JETBRAINS_MONO;
        }
        if (FONT_CUSTOM.equals(normalized)) {
            return FONT_CUSTOM;
        }
        if (isBuiltInFontId(normalized)) {
            return normalized;
        }
        return FONT_JETBRAINS_MONO;
    }

    public static boolean isValidCustomFontFile(@Nullable File customFontFile) {
        return customFontFile != null && customFontFile.isFile() && customFontFile.length() > 0L;
    }

    @NonNull
    public static String getEffectiveFontId(@NonNull Context context) {
        return getEffectiveFontId(context, TermuxConstants.TERMUX_FONT_FILE);
    }

    @NonNull
    public static String getEffectiveFontId(@NonNull Context context, @Nullable File customFontFile) {
        String persisted = TermuxAppearancePreferences.getSelectedFontId(context);
        return resolveEffectiveFontId(persisted, customFontFile);
    }

    public static long setSelectedFontId(@NonNull Context context, @Nullable String fontId) {
        return TermuxAppearancePreferences.setSelectedFontId(context, fontId);
    }

    @NonNull
    public static Typeface getActiveTypeface(@NonNull Context context) {
        return getActiveTypeface(context, TermuxConstants.TERMUX_FONT_FILE);
    }

    @NonNull
    public static Typeface getActiveTypeface(@NonNull Context context, @Nullable File customFontFile) {
        String effectiveId = getEffectiveFontId(context, customFontFile);
        return loadTypefaceForId(context, effectiveId, customFontFile);
    }

    /**
     * Loads the {@link Typeface} for {@code fontId}, falling back safely to bundled
     * JetBrainsMono Nerd Font Mono and finally {@link Typeface#MONOSPACE} if a custom font
     * file is missing, empty, or corrupt. Never modifies or deletes {@code customFontFile}.
     */
    @NonNull
    public static Typeface loadTypefaceForId(
        @NonNull Context context,
        @Nullable String fontId,
        @Nullable File customFontFile
    ) {
        if (FONT_CUSTOM.equals(fontId)) {
            if (isValidCustomFontFile(customFontFile)) {
                try {
                    Typeface custom = Typeface.createFromFile(customFontFile);
                    if (custom != null && !Typeface.DEFAULT.equals(custom)) {
                        return custom;
                    }
                    if (custom != null) {
                        return custom;
                    }
                } catch (Throwable ignored) {
                    // Fall through to bundled default Nerd Font
                }
            }
            return loadBundledTypeface(context, DESC_JETBRAINS_MONO.assetPath);
        }

        FontDescriptor descriptor = getBuiltInById(fontId);
        return loadBundledTypeface(context, descriptor.assetPath);
    }

    @NonNull
    public static Typeface loadBundledTypeface(@NonNull Context context, @NonNull String assetPath) {
        Typeface cached = ASSET_TYPEFACE_CACHE.get(assetPath);
        if (cached != null) {
            return cached;
        }
        try {
            Typeface loaded = Typeface.createFromAsset(context.getAssets(), assetPath);
            if (loaded != null) {
                ASSET_TYPEFACE_CACHE.put(assetPath, loaded);
                return loaded;
            }
        } catch (Throwable ignored) {
            // Fall through to default asset or Typeface.MONOSPACE
        }

        if (!ASSET_JETBRAINS_MONO.equals(assetPath)) {
            try {
                Typeface fallback = Typeface.createFromAsset(context.getAssets(), ASSET_JETBRAINS_MONO);
                if (fallback != null) {
                    ASSET_TYPEFACE_CACHE.put(ASSET_JETBRAINS_MONO, fallback);
                    return fallback;
                }
            } catch (Throwable ignored) {
                // Fall through to Typeface.MONOSPACE
            }
        }

        return Typeface.MONOSPACE;
    }

}
