package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.shell.ShellConfigManager;
import com.termux.app.shell.WelcomeBannerManager;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.shared.termux.theme.TermuxThemePresets;
import com.termux.terminal.TerminalEmulator;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * In-app "Appearance & Shell" Studio providing live Oh My Zsh + Nerd Font preview,
 * Material 3 developer color palette selection, bundled Nerd Font switching,
 * font size & cursor style controls, Top Tab Bar & Welcome Banner toggles,
 * and non-destructive Shell / Oh My Zsh management.
 */
public class AppearanceStudioFragment extends Fragment {

    private View mScrollView;
    private View mRootView;
    private View mPreviewCard;
    private View mPreviewDotRed;
    private View mPreviewDotYellow;
    private View mPreviewDotGreen;
    private TextView mPreviewCaption;
    private TextView mPreviewTerminalText;

    private LinearLayout mThemeGrid;
    private LinearLayout mFontList;

    private View mTypographyCard;
    private TextView mFontSizeTitle;
    private TextView mFontSizeValue;
    private MaterialButton mFontSizeDecrease;
    private MaterialButton mFontSizeIncrease;
    private TextView mCursorStyleTitle;
    private MaterialButton mCursorBlock;
    private MaterialButton mCursorUnderline;
    private MaterialButton mCursorBar;

    private View mInterfaceCard;
    private View mInterfaceDivider;
    private TextView mTopTabBarTitle;
    private TextView mTopTabBarSummary;
    private MaterialSwitch mTopTabBarSwitch;
    private TextView mWelcomeBannerTitle;
    private TextView mWelcomeBannerSummary;
    private MaterialSwitch mWelcomeBannerSwitch;

    private View mShellCard;
    private TextView mShellStatusText;
    private MaterialButton mShellApplyButton;
    private MaterialButton mShellResetButton;

    private TextView[] mSectionLabels;
    private TermuxAppSharedPreferences mAppPreferences;
    private boolean mSuppressSwitchListeners = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_appearance_studio, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        mAppPreferences = TermuxAppSharedPreferences.build(context, true);

        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle(R.string.action_open_appearance);
            }
        }

        bindViews(view);
        wireControls();
        refreshAll();
    }

    private void bindViews(@NonNull View view) {
        mScrollView = view.findViewById(R.id.appearance_studio_scroll);
        mRootView = view.findViewById(R.id.appearance_studio_root);
        mPreviewCard = view.findViewById(R.id.studio_preview_card);
        mPreviewDotRed = view.findViewById(R.id.preview_dot_red);
        mPreviewDotYellow = view.findViewById(R.id.preview_dot_yellow);
        mPreviewDotGreen = view.findViewById(R.id.preview_dot_green);
        mPreviewCaption = view.findViewById(R.id.studio_preview_caption);
        mPreviewTerminalText = view.findViewById(R.id.studio_preview_terminal_text);

        mThemeGrid = view.findViewById(R.id.studio_theme_grid);
        mFontList = view.findViewById(R.id.studio_font_list);

        mTypographyCard = view.findViewById(R.id.studio_typography_card);
        mFontSizeTitle = view.findViewById(R.id.studio_font_size_title);
        mFontSizeValue = view.findViewById(R.id.studio_font_size_value);
        mFontSizeDecrease = view.findViewById(R.id.studio_font_size_decrease);
        mFontSizeIncrease = view.findViewById(R.id.studio_font_size_increase);
        mCursorStyleTitle = view.findViewById(R.id.studio_cursor_style_title);
        mCursorBlock = view.findViewById(R.id.studio_cursor_block);
        mCursorUnderline = view.findViewById(R.id.studio_cursor_underline);
        mCursorBar = view.findViewById(R.id.studio_cursor_bar);

        mInterfaceCard = view.findViewById(R.id.studio_interface_card);
        mInterfaceDivider = view.findViewById(R.id.studio_interface_divider);
        mTopTabBarTitle = view.findViewById(R.id.studio_top_tab_bar_title);
        mTopTabBarSummary = view.findViewById(R.id.studio_top_tab_bar_summary);
        mTopTabBarSwitch = view.findViewById(R.id.studio_switch_top_tab_bar);
        mWelcomeBannerTitle = view.findViewById(R.id.studio_welcome_banner_title);
        mWelcomeBannerSummary = view.findViewById(R.id.studio_welcome_banner_summary);
        mWelcomeBannerSwitch = view.findViewById(R.id.studio_switch_welcome_banner);

        mShellCard = view.findViewById(R.id.studio_shell_card);
        mShellStatusText = view.findViewById(R.id.studio_shell_status_text);
        mShellApplyButton = view.findViewById(R.id.studio_shell_apply_button);
        mShellResetButton = view.findViewById(R.id.studio_shell_reset_button);

        mSectionLabels = new TextView[]{
            view.findViewById(R.id.studio_section_preview_label),
            view.findViewById(R.id.studio_section_themes_label),
            view.findViewById(R.id.studio_section_fonts_label),
            view.findViewById(R.id.studio_section_typography_label),
            view.findViewById(R.id.studio_section_interface_label),
            view.findViewById(R.id.studio_section_shell_label)
        };
    }

    private void wireControls() {
        mFontSizeDecrease.setOnClickListener(v -> adjustFontSize(false));
        mFontSizeIncrease.setOnClickListener(v -> adjustFontSize(true));

        mCursorBlock.setOnClickListener(v -> selectCursorStyle(TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK));
        mCursorUnderline.setOnClickListener(v -> selectCursorStyle(TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE));
        mCursorBar.setOnClickListener(v -> selectCursorStyle(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR));

        mTopTabBarSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mSuppressSwitchListeners) return;
            Context context = requireContext();
            TermuxAppearancePreferences.setShowTopTabBar(context, isChecked);
            notifyTermuxActivity();
        });

        mWelcomeBannerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mSuppressSwitchListeners) return;
            Context context = requireContext();
            WelcomeBannerManager.setWelcomeBannerEnabled(context, getHomeDir(), isChecked);
            refreshLivePreview(TermuxThemeManager.getActivePalette(context), TermuxFontManager.getActiveTypeface(context));
            refreshShellStatus();
        });

        mShellApplyButton.setOnClickListener(v -> {
            Context context = requireContext();
            ShellConfigManager.applyDefaults(context, getPrefixDir(), getHomeDir(), false);
            refreshShellStatus();
            Toast.makeText(context, R.string.appearance_shell_apply_toast, Toast.LENGTH_SHORT).show();
        });

        mShellResetButton.setOnClickListener(v -> showResetShellConfirmationDialog());
    }

    @NonNull
    protected File getPrefixDir() {
        return TermuxConstants.TERMUX_PREFIX_DIR;
    }

    @NonNull
    protected File getHomeDir() {
        return TermuxConstants.TERMUX_HOME_DIR;
    }

    private void adjustFontSize(boolean increase) {
        if (mAppPreferences == null) return;
        Context context = requireContext();
        mAppPreferences.changeFontSize(increase);
        TermuxAppearancePreferences.bumpAppearanceRevision(context);
        refreshAll();
        notifyTermuxActivity();
    }

    private void selectTheme(@NonNull String themeId) {
        Context context = requireContext();
        TermuxThemeManager.setSelectedThemeId(context, themeId);
        refreshAll();
        notifyTermuxActivity();
    }

    private void selectFont(@NonNull String fontId) {
        Context context = requireContext();
        TermuxFontManager.setSelectedFontId(context, fontId);
        refreshAll();
        notifyTermuxActivity();
    }

    private void selectCursorStyle(int cursorStyle) {
        Context context = requireContext();
        TermuxAppearancePreferences.setCursorStyle(context, cursorStyle);
        refreshAll();
        notifyTermuxActivity();
    }

    private void notifyTermuxActivity() {
        Context context = getContext();
        if (context != null) {
            TermuxActivity.updateTermuxActivityStyling(context, false);
        }
    }

    private void showResetShellConfirmationDialog() {
        Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.appearance_shell_reset_title)
            .setMessage(R.string.appearance_shell_reset_message)
            .setPositiveButton(R.string.appearance_shell_reset_confirm, (dialog, which) -> {
                ShellConfigManager.resetToDefaults(context, getPrefixDir(), getHomeDir(), true);
                refreshAll();
                Toast.makeText(context, R.string.appearance_shell_reset_toast, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    public void refreshAll() {
        Context context = getContext();
        if (context == null) return;

        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(context);
        Typeface typeface = TermuxFontManager.getActiveTypeface(context);
        float density = context.getResources().getDisplayMetrics().density;

        if (getActivity() instanceof AppCompatActivity) {
            TermuxThemeManager.applyActivityChrome((AppCompatActivity) getActivity(), palette);
        }

        if (mScrollView != null) {
            mScrollView.setBackgroundColor(palette.surface);
        }
        if (mRootView != null) {
            mRootView.setBackgroundColor(palette.surface);
        }

        for (TextView label : mSectionLabels) {
            if (label != null) {
                label.setTextColor(palette.accent);
            }
        }

        refreshLivePreview(palette, typeface);
        rebuildThemeGrid(palette, density);
        rebuildFontList(palette, density);
        refreshTypographyAndCursor(palette, density);
        refreshInterfaceCard(palette, density);
        refreshShellCard(palette, density);
    }

    private void refreshLivePreview(@NonNull TermuxColorPalette palette, @NonNull Typeface typeface) {
        Context context = requireContext();
        float density = context.getResources().getDisplayMetrics().density;

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(16f * density);
        cardBg.setColor(palette.terminalBackground);
        cardBg.setStroke(Math.max(1, Math.round(density)), palette.outline);
        mPreviewCard.setBackground(cardBg);

        styleDot(mPreviewDotRed, palette.ansiColors[1], density);
        styleDot(mPreviewDotYellow, palette.ansiColors[3], density);
        styleDot(mPreviewDotGreen, palette.ansiColors[2], density);

        String activeFontName = getFontDisplayName(context, TermuxFontManager.getEffectiveFontId(context));
        mPreviewCaption.setText(palette.displayName + " \u00b7 " + activeFontName);
        mPreviewCaption.setTextColor(palette.onSurfaceVariant);

        int fontSizePx = mAppPreferences != null ? mAppPreferences.getFontSize() : Math.round(13f * density);
        float previewPx = Math.max(10f * density, Math.min(24f * density, fontSizePx));
        mPreviewTerminalText.setTypeface(typeface);
        mPreviewTerminalText.setTextSize(TypedValue.COMPLEX_UNIT_PX, previewPx);
        mPreviewTerminalText.setTextColor(palette.terminalForeground);

        int cursorStyle = TermuxAppearancePreferences.getCursorStyle(context);
        if (cursorStyle == TermuxAppearancePreferences.CURSOR_STYLE_UNSET) {
            cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
        }
        String cursorGlyph;
        if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) {
            cursorGlyph = "\u2581";
        } else if (cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) {
            cursorGlyph = "\u258f";
        } else {
            cursorGlyph = "\u2588";
        }

        boolean showBanner = WelcomeBannerManager.isWelcomeBannerEnabled(context, getHomeDir());
        SpannableStringBuilder sb = new SpannableStringBuilder();
        if (showBanner) {
            appendColored(sb, "\uf120 Termux", palette.ansiColors[6], true);
            appendColored(sb, "  ", palette.terminalForeground, false);
            appendColored(sb, "\uf0e7 Ready\n", palette.ansiColors[4], false);
        }
        // Prompt line 1
        appendColored(sb, "\uf07c ~/projects/termux-app", palette.ansiColors[6], true);
        appendColored(sb, " \ue0a0 main", palette.ansiColors[4], false);
        appendColored(sb, "*", palette.ansiColors[3], false);
        appendColored(sb, " \u276f ", palette.ansiColors[2], true);
        appendColored(sb, "ls -la\n", palette.terminalForeground, false);
        // Output line
        appendColored(sb, "app/  ", palette.ansiColors[4], true);
        appendColored(sb, "termux-shared/  ", palette.ansiColors[4], true);
        appendColored(sb, "README.md\n", palette.onSurfaceVariant, false);
        // Active prompt line with cursor
        appendColored(sb, "\uf07c ~/projects/termux-app", palette.ansiColors[6], true);
        appendColored(sb, " \ue0a0 main", palette.ansiColors[4], false);
        appendColored(sb, "*", palette.ansiColors[3], false);
        appendColored(sb, " \u276f ", palette.ansiColors[2], true);
        appendColored(sb, cursorGlyph, palette.terminalCursor, false);

        mPreviewTerminalText.setText(sb);
    }

    private void rebuildThemeGrid(@NonNull TermuxColorPalette activePalette, float density) {
        Context context = requireContext();
        String activeId = TermuxThemeManager.getEffectiveThemeId(context);
        mThemeGrid.removeAllViews();

        List<TermuxColorPalette> builtIns = TermuxThemePresets.getBuiltInPalettes();
        int totalCount = builtIns.size() + 1; // +1 for Custom
        int margin = Math.round(6f * density);

        LinearLayout currentRow = null;
        for (int i = 0; i < totalCount; i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(context);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = margin * 2;
                currentRow.setLayoutParams(rowParams);
                mThemeGrid.addView(currentRow);
            }

            final String themeId;
            final String displayName;
            final TermuxColorPalette swatchPalette;
            if (i < builtIns.size()) {
                TermuxColorPalette p = builtIns.get(i);
                themeId = p.id;
                displayName = p.displayName;
                swatchPalette = p;
            } else {
                themeId = TermuxColorPalette.THEME_CUSTOM;
                displayName = getString(R.string.appearance_theme_custom);
                swatchPalette = TermuxThemeManager.loadCustomPalette(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE);
            }

            boolean selected = themeId.equals(activeId);
            LinearLayout card = buildThemeCard(context, themeId, displayName, swatchPalette, activePalette, selected, density);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 0) {
                cardParams.rightMargin = margin;
            } else {
                cardParams.leftMargin = margin;
            }
            card.setLayoutParams(cardParams);
            currentRow.addView(card);
        }
    }

    @NonNull
    private LinearLayout buildThemeCard(@NonNull Context context,
                                        @NonNull String themeId,
                                        @NonNull String displayName,
                                        @NonNull TermuxColorPalette swatchPalette,
                                        @NonNull TermuxColorPalette activePalette,
                                        boolean selected,
                                        float density) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("theme_" + themeId);
        int pad = Math.round(12f * density);
        card.setPadding(pad, pad, pad, pad);
        card.setClickable(true);
        card.setFocusable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(14f * density);
        bg.setColor(selected ? activePalette.surfaceContainerHigh : activePalette.surfaceContainer);
        bg.setStroke(
            Math.max(1, Math.round((selected ? 2f : 1f) * density)),
            selected ? activePalette.accent : activePalette.outline
        );
        card.setBackground(bg);

        // Swatch strip
        LinearLayout swatches = new LinearLayout(context);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        int[] colors = new int[]{
            swatchPalette.terminalBackground,
            swatchPalette.accent,
            swatchPalette.ansiColors[2],
            swatchPalette.ansiColors[6]
        };
        int dotSize = Math.round(14f * density);
        int dotGap = Math.round(6f * density);
        for (int c : colors) {
            View dot = new View(context);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dp.rightMargin = dotGap;
            dot.setLayoutParams(dp);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(c);
            dotBg.setStroke(Math.max(1, Math.round(0.75f * density)), activePalette.outline);
            dot.setBackground(dotBg);
            swatches.addView(dot);
        }
        card.addView(swatches);

        TextView nameView = new TextView(context);
        nameView.setText(displayName);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        nameView.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        nameView.setTextColor(selected ? activePalette.accent : activePalette.onSurface);
        nameView.setMaxLines(1);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = Math.round(8f * density);
        nameView.setLayoutParams(np);
        card.addView(nameView);

        card.setOnClickListener(v -> selectTheme(themeId));
        return card;
    }

    private void rebuildFontList(@NonNull TermuxColorPalette palette, float density) {
        Context context = requireContext();
        String activeFontId = TermuxFontManager.getEffectiveFontId(context);
        mFontList.removeAllViews();

        List<TermuxFontManager.FontDescriptor> builtIns = TermuxFontManager.getBuiltInFonts();
        int totalCount = builtIns.size() + 1; // +1 for Custom
        int gap = Math.round(8f * density);

        for (int i = 0; i < totalCount; i++) {
            final String fontId;
            final String label;
            if (i < builtIns.size()) {
                TermuxFontManager.FontDescriptor desc = builtIns.get(i);
                fontId = desc.id;
                label = desc.displayName;
            } else {
                fontId = TermuxFontManager.FONT_CUSTOM;
                label = getString(R.string.appearance_font_custom);
            }

            boolean selected = fontId.equals(activeFontId);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setTag("font_" + fontId);
            row.setClickable(true);
            row.setFocusable(true);
            int padH = Math.round(14f * density);
            int padV = Math.round(12f * density);
            row.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) params.topMargin = gap;
            row.setLayoutParams(params);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(12f * density);
            bg.setColor(selected ? palette.surfaceContainerHigh : palette.surfaceContainer);
            bg.setStroke(
                Math.max(1, Math.round((selected ? 2f : 1f) * density)),
                selected ? palette.accent : palette.outline
            );
            row.setBackground(bg);

            Typeface rowTypeface = TermuxFontManager.loadTypefaceForId(context, fontId, TermuxConstants.TERMUX_FONT_FILE);

            TextView nameView = new TextView(context);
            nameView.setText(label);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            nameView.setTextColor(selected ? palette.accent : palette.onSurface);
            nameView.setTypeface(rowTypeface);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameView.setLayoutParams(textParams);
            row.addView(nameView);

            TextView glyphSample = new TextView(context);
            glyphSample.setText("\uf120 \ue0a0 \u276f");
            glyphSample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            glyphSample.setTextColor(selected ? palette.accent : palette.onSurfaceVariant);
            glyphSample.setTypeface(rowTypeface);
            row.addView(glyphSample);

            row.setOnClickListener(v -> selectFont(fontId));
            mFontList.addView(row);
        }
    }

    private void refreshTypographyAndCursor(@NonNull TermuxColorPalette palette, float density) {
        Context context = requireContext();
        styleSurfaceCard(mTypographyCard, palette, density);
        mFontSizeTitle.setTextColor(palette.onSurface);
        mCursorStyleTitle.setTextColor(palette.onSurface);

        int fontSizePx = mAppPreferences != null ? mAppPreferences.getFontSize() : 24;
        mFontSizeValue.setText(String.format(Locale.US, "%d px", fontSizePx));
        mFontSizeValue.setTextColor(palette.accent);

        styleOutlinedButton(mFontSizeDecrease, palette, false);
        styleOutlinedButton(mFontSizeIncrease, palette, false);

        int cursorStyle = TermuxAppearancePreferences.getCursorStyle(context);
        if (cursorStyle == TermuxAppearancePreferences.CURSOR_STYLE_UNSET) {
            cursorStyle = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
        }
        styleOutlinedButton(mCursorBlock, palette, cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK);
        styleOutlinedButton(mCursorUnderline, palette, cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE);
        styleOutlinedButton(mCursorBar, palette, cursorStyle == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR);
    }

    private void refreshInterfaceCard(@NonNull TermuxColorPalette palette, float density) {
        Context context = requireContext();
        styleSurfaceCard(mInterfaceCard, palette, density);
        mTopTabBarTitle.setTextColor(palette.onSurface);
        mTopTabBarSummary.setTextColor(palette.onSurfaceVariant);
        mWelcomeBannerTitle.setTextColor(palette.onSurface);
        mWelcomeBannerSummary.setTextColor(palette.onSurfaceVariant);
        mInterfaceDivider.setBackgroundColor(palette.outline);
        mInterfaceDivider.setAlpha(0.3f);

        mSuppressSwitchListeners = true;
        try {
            mTopTabBarSwitch.setChecked(TermuxAppearancePreferences.shouldShowTopTabBar(context));
            mWelcomeBannerSwitch.setChecked(WelcomeBannerManager.isWelcomeBannerEnabled(context, getHomeDir()));
        } finally {
            mSuppressSwitchListeners = false;
        }
    }

    private void refreshShellCard(@NonNull TermuxColorPalette palette, float density) {
        styleSurfaceCard(mShellCard, palette, density);
        mShellStatusText.setTextColor(palette.onSurfaceVariant);

        mShellApplyButton.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        mShellApplyButton.setTextColor(palette.onAccent);

        mShellResetButton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        mShellResetButton.setStrokeColor(ColorStateList.valueOf(palette.error));
        mShellResetButton.setTextColor(palette.error);

        refreshShellStatus();
    }

    private void refreshShellStatus() {
        Context context = getContext();
        if (context == null || mShellStatusText == null) return;

        ShellConfigManager.ShellStatus status = ShellConfigManager.getShellStatus(context, getPrefixDir(), getHomeDir());
        String shellName = new File(status.defaultShellPath).getName();
        StringBuilder sb = new StringBuilder();
        sb.append("Active Shell: ").append(shellName);
        if (status.isZshDefault) {
            sb.append(" (Default)");
        }
        sb.append("\nOh My Zsh: ").append(status.isOhMyZshInstalled ? "Installed" : "Not installed");
        sb.append("\nPlugins: ").append(status.arePluginsInstalled ? "Autosuggestions & Syntax Highlighting" : "Not installed");
        if (status.hasCustomZshrc || status.hasCustomBashrc) {
            sb.append("\nCustom dotfiles detected (preserved on upgrades)");
        }
        mShellStatusText.setText(sb.toString());
    }

    private static void styleSurfaceCard(@NonNull View card, @NonNull TermuxColorPalette palette, float density) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(14f * density);
        bg.setColor(palette.surfaceContainer);
        bg.setStroke(Math.max(1, Math.round(density)), palette.outline);
        card.setBackground(bg);
    }

    private static void styleOutlinedButton(@NonNull MaterialButton button, @NonNull TermuxColorPalette palette, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
            button.setStrokeColor(ColorStateList.valueOf(palette.accent));
            button.setTextColor(palette.onAccent);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            button.setStrokeColor(ColorStateList.valueOf(palette.outline));
            button.setTextColor(palette.onSurface);
        }
    }

    private static void styleDot(@NonNull View dot, int color, float density) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        dot.setBackground(gd);
    }

    private static void appendColored(@NonNull SpannableStringBuilder sb, @NonNull String text, int color, boolean bold) {
        int start = sb.length();
        sb.append(text);
        int end = sb.length();
        sb.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @NonNull
    private static String getFontDisplayName(@NonNull Context context, @NonNull String fontId) {
        if (TermuxFontManager.FONT_CUSTOM.equals(fontId)) {
            return context.getString(R.string.appearance_font_custom);
        }
        return TermuxFontManager.getBuiltInById(fontId).displayName;
    }
}
