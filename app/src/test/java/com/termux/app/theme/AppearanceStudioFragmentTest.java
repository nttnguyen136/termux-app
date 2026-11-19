package com.termux.app.theme;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.AppearanceStudioFragment;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.terminal.TerminalEmulator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppearanceStudioFragmentTest {

    @Test
    public void launchViaAppearanceIntent_hostsAppearanceStudioFragmentAndUpdatesLive() {
        Intent intent = SettingsActivity.createAppearanceStudioIntent(RuntimeEnvironment.getApplication());
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class, intent)
            .create().start().resume().get();

        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.settings);
        assertTrue(fragment instanceof AppearanceStudioFragment);

        View root = fragment.requireView();
        TextView previewText = root.findViewById(R.id.studio_preview_terminal_text);
        TextView previewCaption = root.findViewById(R.id.studio_preview_caption);
        assertNotNull(previewText);
        assertNotNull(previewCaption);

        // Select Dracula theme
        View draculaCard = root.findViewWithTag("theme_" + TermuxColorPalette.THEME_DRACULA);
        assertNotNull(draculaCard);
        draculaCard.performClick();
        assertEquals(TermuxColorPalette.THEME_DRACULA, TermuxThemeManager.getEffectiveThemeId(activity));
        assertTrue(previewCaption.getText().toString().contains("Dracula"));

        // Select FiraCode Nerd Font
        View firaRow = root.findViewWithTag("font_" + TermuxFontManager.FONT_FIRA_CODE);
        assertNotNull(firaRow);
        firaRow.performClick();
        assertEquals(TermuxFontManager.FONT_FIRA_CODE, TermuxFontManager.getEffectiveFontId(activity));
        assertTrue(previewCaption.getText().toString().contains("FiraCode"));

        // Increase font size
        TermuxAppSharedPreferences appPrefs = TermuxAppSharedPreferences.build(activity, true);
        int beforeSize = appPrefs.getFontSize();
        root.findViewById(R.id.studio_font_size_increase).performClick();
        assertEquals(beforeSize + 2, appPrefs.getFontSize());

        // Select Bar cursor style and verify preview cursor glyph updates
        root.findViewById(R.id.studio_cursor_bar).performClick();
        assertEquals(TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR, TermuxAppearancePreferences.getCursorStyle(activity));
        assertTrue(previewText.getText().toString().contains("\u258f"));

        // Toggle Top Tab Bar ON
        MaterialSwitch topBarSwitch = root.findViewById(R.id.studio_switch_top_tab_bar);
        assertFalse(topBarSwitch.isChecked());
        topBarSwitch.setChecked(true);
        assertTrue(TermuxAppearancePreferences.shouldShowTopTabBar(activity));
    }
}
