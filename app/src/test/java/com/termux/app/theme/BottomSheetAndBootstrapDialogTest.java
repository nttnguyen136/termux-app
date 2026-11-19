package com.termux.app.theme;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.termux.R;
import com.termux.app.BootstrapProgressDialog;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.terminal.TerminalActionsBottomSheet;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.shared.termux.theme.TermuxThemePresets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BottomSheetAndBootstrapDialogTest {

    @Test
    public void showTerminalActionsBottomSheet_rendersMaterialSheetWithThemeAndActions() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class)
            .create().start().resume().get();

        TermuxThemeManager.setSelectedThemeId(activity, TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
        TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);

        activity.showTerminalActionsBottomSheet();
        BottomSheetDialog dialog = activity.getTerminalActionsBottomSheet();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());

        View root = dialog.findViewById(R.id.terminal_actions_sheet_root);
        assertNotNull(root);
        assertTrue(root.getBackground() instanceof GradientDrawable);

        TextView title = dialog.findViewById(R.id.terminal_actions_title);
        assertNotNull(title);
        assertEquals(mocha.onSurface, title.getCurrentTextColor());

        LinearLayout list = dialog.findViewById(R.id.terminal_actions_list);
        assertNotNull(list);
        assertNotNull(list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.PASTE));
        assertNotNull(list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.NEW_SESSION));
        assertNotNull(list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.APPEARANCE));
        assertNotNull(list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.TOGGLE_EXTRA_KEYS));
        assertNotNull(list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.KEEP_SCREEN_ON));
        assertNotNull(list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.SETTINGS));

        // Clicking Appearance & Shell launches SettingsActivity with Appearance Studio extra
        View appearanceRow = list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.APPEARANCE);
        appearanceRow.performClick();
        assertFalse(dialog.isShowing());

        Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(SettingsActivity.class.getName(), launched.getComponent().getClassName());
        assertEquals(
            SettingsActivity.FRAGMENT_APPEARANCE_STUDIO,
            launched.getStringExtra(SettingsActivity.EXTRA_INITIAL_FRAGMENT)
        );
    }

    @Test
    public void bootstrapProgressDialog_updatesStagesAndProgressDeterminately() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class)
            .create().start().resume().get();

        TermuxThemeManager.setSelectedThemeId(activity, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(TermuxColorPalette.THEME_CATPPUCCIN_LATTE);

        BootstrapProgressDialog dialog = BootstrapProgressDialog.show(activity);
        assertTrue(dialog.isShowing());
        assertEquals(latte.onSurface, dialog.getStageTitleView().getCurrentTextColor());

        dialog.updateStage("Setting up Zsh", "Configuring Oh My Zsh and plugins", 65);
        assertEquals("Setting up Zsh", dialog.getStageTitleView().getText().toString());
        assertEquals("Configuring Oh My Zsh and plugins", dialog.getStageDetailView().getText().toString());
        assertEquals(View.VISIBLE, dialog.getStageDetailView().getVisibility());
        assertFalse(dialog.getProgressBar().isIndeterminate());
        assertEquals(65, dialog.getProgressBar().getProgress());

        dialog.dismiss();
        assertFalse(dialog.isShowing());
    }
}
