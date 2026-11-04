package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.termux.app.TermuxActivity;
import com.termux.app.activities.ExtraKeysEditorActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    /** The special key to switch to the next extra keys layout, cycling back to the first one. */
    public static final String KEY_LAYOUT = "LAYOUT";

    /** The prefix of the special keys {@code LAYOUT_1} .. {@code LAYOUT_N} to switch directly to
     * the extra keys layout defined by the property with that number. */
    public static final String KEY_LAYOUT_PREFIX = "LAYOUT_";

    /** The special key to open the {@link ExtraKeysEditorActivity}. */
    public static final String KEY_SETTINGS = "SETTINGS";

    /** The special key to open the Termux {@link SettingsActivity}. */
    public static final String KEY_TERMUX_SETTINGS = "TERMUX_SETTINGS";

    /** An extra keys layout loaded from the {@code extra-keys[-N]} property. */
    public static class ExtraKeysLayout {
        /** The property number {@code N} of {@code extra-keys-N}, {@code 1} for {@code extra-keys}. */
        public final int number;
        public final ExtraKeysInfo extraKeysInfo;

        public ExtraKeysLayout(int number, @NonNull ExtraKeysInfo extraKeysInfo) {
            this.number = number;
            this.extraKeysInfo = extraKeysInfo;
        }
    }

    /** The loaded extra keys layouts in order of their property number. Each layout is shown as
     * a separate page of the terminal toolbar. */
    private final List<ExtraKeysLayout> mExtraKeysLayouts = new ArrayList<>();

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
    }


    /**
     * Set the terminal extra keys layouts and style.
     *
     * Layout 1 is loaded from {@link TermuxPropertyConstants#KEY_EXTRA_KEYS} and falls back to
     * {@link TermuxPropertyConstants#DEFAULT_IVALUE_EXTRA_KEYS} if invalid. Layouts 2..N are loaded
     * from {@code extra-keys-N} and are skipped if not set or invalid.
     */
    private void setExtraKeys() {
        mExtraKeysLayouts.clear();

        // The mMap stores the extra key and style string values while loading properties
        // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
        // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
        String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

        ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
        if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
            Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
            extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
        }

        List<String> invalidKeys = new ArrayList<>();
        for (int number = 1; number <= TermuxPropertyConstants.MAX_EXTRA_KEYS_LAYOUTS; number++) {
            String key = TermuxPropertyConstants.getExtraKeysLayoutKey(number);
            String extraKeys = (String) mActivity.getProperties().getInternalPropertyValue(key, true);
            // Layouts other than the first one are optional
            if (number > 1 && (extraKeys == null || extraKeys.trim().isEmpty())) continue;

            try {
                mExtraKeysLayouts.add(new ExtraKeysLayout(number,
                    new ExtraKeysInfo(extraKeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES)));
            } catch (JSONException e) {
                invalidKeys.add(key);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + key + "\" property from the properties file: ", e);

                if (number == 1) {
                    try {
                        mExtraKeysLayouts.add(new ExtraKeysLayout(number,
                            new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES)));
                    } catch (JSONException e2) {
                        Logger.showToast(mActivity, "Can't create default extra keys",true);
                        Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e2);
                    }
                }
            }
        }

        if (!invalidKeys.isEmpty()) {
            Logger.showToast(mActivity, "Could not load and set the " + TextUtils.join(", ", invalidKeys) +
                " property from the properties file. Check logcat for details.", true);
        }
    }

    /** Reload the terminal extra keys layouts and style from the current properties. */
    public void reloadExtraKeys() {
        setExtraKeys();
    }

    /** Get the extra keys layouts. Each layout is shown as a separate page of the terminal toolbar. */
    @NonNull
    public List<ExtraKeysLayout> getExtraKeysLayouts() {
        return mExtraKeysLayouts;
    }

    /** Get the number of extra keys layouts. */
    public int getExtraKeysLayoutCount() {
        return mExtraKeysLayouts.size();
    }

    /** Get the {@link ExtraKeysInfo} for the layout at {@code index}, or {@code null} if out of range. */
    @Nullable
    public ExtraKeysInfo getExtraKeysInfo(int index) {
        if (index < 0 || index >= mExtraKeysLayouts.size()) return null;
        return mExtraKeysLayouts.get(index).extraKeysInfo;
    }

    /** Get the {@link ExtraKeysInfo} for the first layout, or {@code null} if none could be loaded. */
    @Nullable
    public ExtraKeysInfo getExtraKeysInfo() {
        return getExtraKeysInfo(0);
    }

    /** Get the maximum number of rows across all layouts. The terminal toolbar uses this as its fixed height. */
    public int getMaxExtraKeysRowCount() {
        int max = 0;
        for (ExtraKeysLayout layout : mExtraKeysLayouts)
            max = Math.max(max, layout.extraKeysInfo.getMatrix().length);
        return max;
    }

    /** Get the index (page position) of the layout loaded from the {@code extra-keys-N} property
     * with {@code number}, or {@code -1} if it does not exist. */
    public int getExtraKeysLayoutIndexForNumber(int number) {
        for (int i = 0; i < mExtraKeysLayouts.size(); i++)
            if (mExtraKeysLayouts.get(i).number == number) return i;
        return -1;
    }

    /** Switch the terminal toolbar to the next extra keys layout, cycling back to the first one.
     * The text input page is skipped. */
    public void switchToNextExtraKeysLayout() {
        ViewPager viewPager = mActivity.getTerminalToolbarViewPager();
        int count = getExtraKeysLayoutCount();
        if (viewPager == null || count <= 1) return;
        int current = viewPager.getCurrentItem();
        viewPager.setCurrentItem(current + 1 >= count ? 0 : current + 1);
    }

    /** Switch the terminal toolbar to the extra keys layout loaded from the {@code extra-keys-N}
     * property with {@code number}. Logs a warning and does nothing if it does not exist. */
    public void switchToExtraKeysLayout(int number) {
        ViewPager viewPager = mActivity.getTerminalToolbarViewPager();
        if (viewPager == null) return;
        int index = getExtraKeysLayoutIndexForNumber(number);
        if (index < 0) {
            Logger.logWarn(LOG_TAG, "Ignoring \"" + KEY_LAYOUT_PREFIX + number + "\" extra key since the \"" +
                TermuxPropertyConstants.getExtraKeysLayoutKey(number) + "\" layout is not defined or is invalid");
            return;
        }
        viewPager.setCurrentItem(index);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else if (KEY_LAYOUT.equals(key)) {
            switchToNextExtraKeysLayout();
        } else if (KEY_SETTINGS.equals(key)) {
            mActivity.startActivity(new Intent(mActivity, ExtraKeysEditorActivity.class));
        } else if (KEY_TERMUX_SETTINGS.equals(key)) {
            mActivity.startActivity(new Intent(mActivity, SettingsActivity.class));
        } else if (key != null && key.startsWith(KEY_LAYOUT_PREFIX) && isLayoutNumber(key.substring(KEY_LAYOUT_PREFIX.length()))) {
            switchToExtraKeysLayout(Integer.parseInt(key.substring(KEY_LAYOUT_PREFIX.length())));
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

    /** Check if {@code value} is a valid layout number in range {@code 1..}{@link TermuxPropertyConstants#MAX_EXTRA_KEYS_LAYOUTS}. */
    private static boolean isLayoutNumber(String value) {
        if (value.length() != 1 || !Character.isDigit(value.charAt(0))) return false;
        int number = value.charAt(0) - '0';
        return number >= 1 && number <= TermuxPropertyConstants.MAX_EXTRA_KEYS_LAYOUTS;
    }

}
