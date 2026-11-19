package com.termux.app.theme;

import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemePresets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DrawerAndTopTabBarTest {

    @Before
    public void resetPrefs() {
        TermuxAppearancePreferences.setSelectedThemeId(RuntimeEnvironment.getApplication(), TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        TermuxAppearancePreferences.setShowTopTabBar(RuntimeEnvironment.getApplication(), false);
    }

    @Test
    public void topTabBarHiddenByDefaultAndTogglesLiveWithDrawerTheming() {
        ActivityController<TermuxActivity> controller = Robolectric.buildActivity(TermuxActivity.class).create().start().resume();
        TermuxActivity activity = controller.get();

        View topBar = activity.findViewById(R.id.terminal_top_tab_bar);
        assertNotNull(topBar);
        assertEquals("Top Tab Bar must be hidden by default for fullscreen terminal", View.GONE, topBar.getVisibility());

        // Verify drawer branded header, shortcut buttons, and Material 3 footer buttons match Catppuccin Mocha
        TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        View leftDrawer = activity.findViewById(R.id.left_drawer);
        assertNotNull(leftDrawer);
        assertEquals(mocha.surface, ((ColorDrawable) leftDrawer.getBackground()).getColor());

        TextView subtitle = activity.findViewById(R.id.drawer_brand_subtitle);
        assertNotNull(subtitle);
        assertEquals(mocha.displayName, subtitle.getText().toString());
        assertNotNull(activity.findViewById(R.id.appearance_button));
        assertNotNull(activity.findViewById(R.id.settings_button));

        MaterialButton newSessionBtn = activity.findViewById(R.id.new_session_button);
        assertNotNull(newSessionBtn);
        assertEquals(mocha.accent, newSessionBtn.getBackgroundTintList().getDefaultColor());
        assertEquals(mocha.onAccent, newSessionBtn.getCurrentTextColor());

        // Enable Top Tab Bar and switch to Catppuccin Latte (light theme)
        TermuxAppearancePreferences.setShowTopTabBar(activity, true);
        TermuxAppearancePreferences.setSelectedThemeId(activity, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        activity.applyCurrentAppearance();

        TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        assertEquals(View.VISIBLE, topBar.getVisibility());
        assertEquals(latte.surfaceContainer, ((ColorDrawable) topBar.getBackground()).getColor());
        assertEquals(latte.surface, ((ColorDrawable) leftDrawer.getBackground()).getColor());
        assertEquals(latte.displayName, subtitle.getText().toString());
        assertEquals(latte.accent, newSessionBtn.getBackgroundTintList().getDefaultColor());

        // Disable Top Tab Bar again -> restores fullscreen layout
        TermuxAppearancePreferences.setShowTopTabBar(activity, false);
        activity.applyCurrentAppearance();
        assertEquals(View.GONE, topBar.getVisibility());
    }
}
