package com.termux.app.terminal;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.GravityCompat;
import androidx.core.widget.ImageViewCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.theme.TermuxAppearanceAware;
import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.terminal.TerminalSession;

import java.util.List;

/**
 * Controller for the optional compact Top Tab Bar in {@link TermuxActivity}.
 * Hidden by default to preserve fullscreen terminal rows; toggled via {@link TermuxAppearancePreferences#KEY_SHOW_TOP_TAB_BAR}.
 */
public final class TermuxTopTabBarController implements TermuxAppearanceAware {

    private final TermuxActivity mActivity;
    @Nullable private final View mTopTabBar;
    @Nullable private final LinearLayout mTabsContainer;
    @Nullable private final View mDivider;
    @Nullable private final ImageButton mDrawerButton;
    @Nullable private final ImageButton mNewSessionButton;
    @Nullable private final ImageButton mSettingsButton;

    @NonNull private TermuxColorPalette mPalette;
    @NonNull private Typeface mTypeface;

    public TermuxTopTabBarController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mTopTabBar = activity.findViewById(R.id.terminal_top_tab_bar);
        mTabsContainer = activity.findViewById(R.id.top_bar_tabs_container);
        mDivider = activity.findViewById(R.id.top_bar_divider);
        mDrawerButton = activity.findViewById(R.id.top_bar_drawer_button);
        mNewSessionButton = activity.findViewById(R.id.top_bar_new_session_button);
        mSettingsButton = activity.findViewById(R.id.top_bar_settings_button);

        mPalette = TermuxThemeManager.getActivePalette(activity);
        mTypeface = TermuxFontManager.getActiveTypeface(activity);

        if (mDrawerButton != null) {
            mDrawerButton.setOnClickListener(v -> {
                if (mActivity.getDrawer() != null) {
                    mActivity.getDrawer().openDrawer(GravityCompat.START);
                }
            });
        }
        if (mNewSessionButton != null) {
            mNewSessionButton.setOnClickListener(v -> {
                if (mActivity.getTermuxTerminalSessionClient() != null) {
                    mActivity.getTermuxTerminalSessionClient().addNewSession(false, null);
                }
            });
        }
        if (mSettingsButton != null) {
            mSettingsButton.setOnClickListener(v ->
                ActivityUtils.startActivity(mActivity, new Intent(mActivity, SettingsActivity.class)));
        }

        updateVisibility();
    }

    public void updateVisibility() {
        if (mTopTabBar == null) return;
        boolean show = TermuxAppearancePreferences.shouldShowTopTabBar(mActivity);
        mTopTabBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            rebuildTabs();
        }
    }

    @Override
    public void applyAppearance(@NonNull TermuxColorPalette palette, @NonNull Typeface typeface) {
        mPalette = palette;
        mTypeface = typeface;

        if (mTopTabBar != null) {
            mTopTabBar.setBackgroundColor(palette.surfaceContainer);
        }
        if (mDivider != null) {
            mDivider.setBackgroundColor(ColorUtils.setAlphaComponent(palette.outline, 96));
        }
        ColorStateList iconTint = ColorStateList.valueOf(palette.onSurface);
        if (mDrawerButton != null) ImageViewCompat.setImageTintList(mDrawerButton, iconTint);
        if (mNewSessionButton != null) ImageViewCompat.setImageTintList(mNewSessionButton, iconTint);
        if (mSettingsButton != null) ImageViewCompat.setImageTintList(mSettingsButton, iconTint);

        updateVisibility();
    }

    public void rebuildTabs() {
        if (mTabsContainer == null || mTopTabBar == null || mTopTabBar.getVisibility() != View.VISIBLE) {
            return;
        }
        mTabsContainer.removeAllViews();

        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        List<TermuxSession> sessions = service.getTermuxSessions();
        TerminalSession currentSession = mActivity.getCurrentSession();

        int padH = dpToPx(10f);
        int padV = dpToPx(4f);
        int marginEnd = dpToPx(6f);
        int maxWidth = dpToPx(140f);
        float radiusPx = dpToPx(8f);

        for (int i = 0; i < sessions.size(); i++) {
            TermuxSession termuxSession = sessions.get(i);
            TerminalSession terminalSession = termuxSession != null ? termuxSession.getTerminalSession() : null;
            if (terminalSession == null) continue;

            boolean isSelected = terminalSession == currentSession;
            boolean exitedWithError = !terminalSession.isRunning() && terminalSession.getExitStatus() != 0;

            String label = buildTabLabel(i, terminalSession);

            TextView tab = new TextView(mActivity);
            tab.setText(label);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            tab.setMaxWidth(maxWidth);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(padH, padV, padH, padV);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            tab.setTypeface(mTypeface, isSelected ? Typeface.BOLD : Typeface.NORMAL);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(radiusPx);
            if (isSelected) {
                bg.setColor(exitedWithError ? mPalette.error : mPalette.accent);
                tab.setTextColor(exitedWithError ? mPalette.onError : mPalette.onAccent);
            } else {
                bg.setColor(mPalette.surfaceContainerHigh);
                tab.setTextColor(exitedWithError ? mPalette.error : mPalette.onSurfaceVariant);
            }
            tab.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(marginEnd);
            tab.setLayoutParams(lp);

            final TerminalSession targetSession = terminalSession;
            tab.setOnClickListener(v -> {
                if (mActivity.getTermuxTerminalSessionClient() != null) {
                    mActivity.getTermuxTerminalSessionClient().setCurrentSession(targetSession);
                }
            });
            tab.setOnLongClickListener(v -> {
                if (mActivity.getTermuxTerminalSessionClient() != null) {
                    mActivity.getTermuxTerminalSessionClient().renameSession(targetSession);
                }
                return true;
            });

            mTabsContainer.addView(tab);
        }
    }

    @NonNull
    static String buildTabLabel(int index, @NonNull TerminalSession session) {
        String name = session.mSessionName;
        String title = session.getTitle();
        String base = !TextUtils.isEmpty(name) ? name : (!TextUtils.isEmpty(title) ? title : "shell");
        return (index + 1) + " · " + base;
    }

    private int dpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, mActivity.getResources().getDisplayMetrics()));
    }
}
