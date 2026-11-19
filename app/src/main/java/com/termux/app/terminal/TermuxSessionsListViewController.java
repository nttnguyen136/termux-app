package com.termux.app.terminal;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.theme.TermuxAppearanceAware;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController extends ArrayAdapter<TermuxSession>
    implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, TermuxAppearanceAware {

    final TermuxActivity mActivity;

    @NonNull
    private TermuxColorPalette mPalette;
    @NonNull
    private Typeface mTypeface;

    public TermuxSessionsListViewController(TermuxActivity activity, List<TermuxSession> sessionList) {
        super(activity.getApplicationContext(), R.layout.item_terminal_sessions_list, sessionList);
        this.mActivity = activity;
        this.mPalette = TermuxThemeManager.getActivePalette(activity);
        this.mTypeface = TermuxFontManager.getActiveTypeface(activity);
    }

    @Override
    public void applyAppearance(@NonNull TermuxColorPalette palette, @NonNull Typeface typeface) {
        mPalette = palette;
        mTypeface = typeface;
        notifyDataSetChanged();
    }

    @SuppressLint("SetTextI18n")
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View sessionRowView = convertView;
        if (sessionRowView == null) {
            LayoutInflater inflater = mActivity.getLayoutInflater();
            sessionRowView = inflater.inflate(R.layout.item_terminal_sessions_list, parent, false);
        }

        View cardContainer = sessionRowView.findViewById(R.id.session_card_container);
        View activeIndicator = sessionRowView.findViewById(R.id.session_active_indicator);
        View statusDot = sessionRowView.findViewById(R.id.session_status_dot);
        TextView sessionTitleView = sessionRowView.findViewById(R.id.session_title);
        ImageButton closeButton = sessionRowView.findViewById(R.id.session_close_button);

        TermuxSession termuxSession = getItem(position);
        TerminalSession sessionAtRow = termuxSession != null ? termuxSession.getTerminalSession() : null;
        if (sessionAtRow == null) {
            sessionTitleView.setText("null session");
            return sessionRowView;
        }

        boolean isCurrentSession = sessionAtRow == mActivity.getCurrentSession();
        boolean sessionRunning = sessionAtRow.isRunning();
        boolean exitedWithError = !sessionRunning && sessionAtRow.getExitStatus() != 0;

        float radiusPx = dpToPx(12f);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(radiusPx);
        if (isCurrentSession) {
            cardBg.setColor(mPalette.surfaceContainerHigh);
            cardBg.setStroke(dpToPx(1f), ColorUtils.setAlphaComponent(mPalette.accent, 140));
        } else {
            cardBg.setColor(ColorUtils.setAlphaComponent(mPalette.surfaceContainer, 180));
            cardBg.setStroke(dpToPx(1f), Color.TRANSPARENT);
        }
        if (cardContainer != null) {
            cardContainer.setBackground(cardBg);
        }

        if (activeIndicator != null) {
            activeIndicator.setVisibility(isCurrentSession ? View.VISIBLE : View.INVISIBLE);
            GradientDrawable indicatorBg = new GradientDrawable();
            indicatorBg.setCornerRadius(dpToPx(2f));
            indicatorBg.setColor(exitedWithError ? mPalette.error : mPalette.accent);
            activeIndicator.setBackground(indicatorBg);
        }

        if (statusDot != null) {
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            int dotColor;
            if (sessionRunning) {
                dotColor = mPalette.ansiColors[2]; // ANSI green
            } else if (exitedWithError) {
                dotColor = mPalette.error;
            } else {
                dotColor = mPalette.onSurfaceVariant;
            }
            dotBg.setColor(dotColor);
            statusDot.setBackground(dotBg);
        }

        String name = sessionAtRow.mSessionName;
        String sessionTitle = sessionAtRow.getTitle();

        String numberPart = "[" + (position + 1) + "] ";
        String sessionNamePart = (TextUtils.isEmpty(name) ? "" : name);
        String sessionTitlePart = (TextUtils.isEmpty(sessionTitle) ? "" : ((sessionNamePart.isEmpty() ? "" : "\n") + sessionTitle));

        String fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart;
        SpannableString fullSessionTitleStyled = new SpannableString(fullSessionTitle);
        int headerEnd = numberPart.length() + sessionNamePart.length();
        fullSessionTitleStyled.setSpan(new StyleSpan(Typeface.BOLD), 0, headerEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (headerEnd < fullSessionTitle.length()) {
            fullSessionTitleStyled.setSpan(new ForegroundColorSpan(mPalette.onSurfaceVariant), headerEnd, fullSessionTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        sessionTitleView.setText(fullSessionTitleStyled);
        sessionTitleView.setTypeface(mTypeface);

        if (sessionRunning) {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            sessionTitleView.setPaintFlags(sessionTitleView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        int primaryTextColor = exitedWithError ? mPalette.error : mPalette.onSurface;
        sessionTitleView.setTextColor(primaryTextColor);

        if (closeButton != null) {
            ImageViewCompat.setImageTintList(closeButton, ColorStateList.valueOf(mPalette.onSurfaceVariant));
            closeButton.setOnClickListener(v -> {
                if (sessionAtRow.isRunning()) {
                    sessionAtRow.finishIfRunning();
                } else {
                    mActivity.getTermuxTerminalSessionClient().removeFinishedSession(sessionAtRow);
                }
            });
        }

        return sessionRowView;
    }

    private int dpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, mActivity.getResources().getDisplayMetrics()));
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        TermuxSession clickedSession = getItem(position);
        if (clickedSession == null) return;
        mActivity.getTermuxTerminalSessionClient().setCurrentSession(clickedSession.getTerminalSession());
        mActivity.getDrawer().closeDrawers();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        final TermuxSession selectedSession = getItem(position);
        if (selectedSession == null) return false;
        mActivity.getTermuxTerminalSessionClient().renameSession(selectedSession.getTerminalSession());
        return true;
    }

}
