package com.termux.app.theme;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.extrakeys.SpecialButtonState;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemePresets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ExtraKeysAppearanceTest {

    @Test
    public void roundedKeycapsAndActiveStatesMatchPaletteAndReloadLive() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);

        ExtraKeysView extraKeysView = new ExtraKeysView(context, null);
        ExtraKeysInfo info = new ExtraKeysInfo(
            "[[\"ESC\",\"CTRL\",\"ALT\",\"TAB\"]]",
            "default",
            ExtraKeysConstants.CONTROL_CHARS_ALIASES
        );
        extraKeysView.reload(info, 120f);

        TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        extraKeysView.applyAppearance(mocha, Typeface.MONOSPACE);

        assertEquals(4, extraKeysView.getChildCount());
        assertEquals(mocha.toolbarSurface, ((ColorDrawable) extraKeysView.getBackground()).getColor());

        MaterialButton escButton = (MaterialButton) extraKeysView.getChildAt(0);
        assertTrue("Keycap corner radius should be > 0", escButton.getCornerRadius() > 0);
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) escButton.getLayoutParams();
        assertTrue("Keycap should have slim horizontal margin", mlp.leftMargin > 0 && mlp.rightMargin > 0);
        assertEquals(mocha.keycapBackground, escButton.getBackgroundTintList().getDefaultColor());
        assertEquals(mocha.keycapText, escButton.getCurrentTextColor());
        assertEquals(Typeface.MONOSPACE, escButton.getTypeface());

        // Toggle CTRL active and locked states
        Map<SpecialButton, SpecialButtonState> specials = extraKeysView.getSpecialButtons();
        assertNotNull(specials);
        SpecialButtonState ctrlState = specials.get(SpecialButton.CTRL);
        assertNotNull(ctrlState);
        MaterialButton ctrlButton = (MaterialButton) extraKeysView.getChildAt(1);

        ctrlState.setIsActive(true);
        assertEquals(mocha.keycapActiveBackground, ctrlButton.getBackgroundTintList().getDefaultColor());
        assertEquals(mocha.keycapActiveText, ctrlButton.getCurrentTextColor());

        ctrlState.setIsLocked(true);
        assertEquals(mocha.keycapLockedBackground, ctrlButton.getBackgroundTintList().getDefaultColor());
        assertEquals(mocha.keycapLockedText, ctrlButton.getCurrentTextColor());

        // Live-switch to Catppuccin Latte (light theme)
        TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        extraKeysView.applyAppearance(latte, Typeface.SERIF);
        assertEquals(latte.toolbarSurface, ((ColorDrawable) extraKeysView.getBackground()).getColor());
        assertEquals(latte.keycapBackground, escButton.getBackgroundTintList().getDefaultColor());
        assertEquals(latte.keycapText, escButton.getCurrentTextColor());
        assertEquals(latte.keycapLockedBackground, ctrlButton.getBackgroundTintList().getDefaultColor());
        assertEquals(latte.keycapLockedText, ctrlButton.getCurrentTextColor());
        assertEquals(Typeface.SERIF, escButton.getTypeface());
    }

    @Test
    public void textInputBarThemesCleanlyInDarkAndLightPalettes() {
        Context context = RuntimeEnvironment.getApplication();
        EditText editText = new EditText(context);

        TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        TerminalToolbarViewPager.PageAdapter.applyTextInputAppearance(editText, mocha, Typeface.MONOSPACE);
        assertEquals(mocha.toolbarSurface, ((ColorDrawable) editText.getBackground()).getColor());
        assertEquals(mocha.onSurface, editText.getCurrentTextColor());
        assertEquals(mocha.onSurfaceVariant, editText.getCurrentHintTextColor());
        assertEquals(Typeface.MONOSPACE, editText.getTypeface());

        TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        TerminalToolbarViewPager.PageAdapter.applyTextInputAppearance(editText, latte, Typeface.SANS_SERIF);
        assertEquals(latte.toolbarSurface, ((ColorDrawable) editText.getBackground()).getColor());
        assertEquals(latte.onSurface, editText.getCurrentTextColor());
        assertEquals(latte.onSurfaceVariant, editText.getCurrentHintTextColor());
        assertEquals(Typeface.SANS_SERIF, editText.getTypeface());
    }
}
