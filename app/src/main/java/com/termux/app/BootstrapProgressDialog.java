package com.termux.app;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxThemeManager;

import java.util.Locale;

/**
 * Material 3 bootstrap installation progress dialog replacing deprecated {@link android.app.ProgressDialog}.
 * Displays setup stages, detail text, and a determinate {@link LinearProgressIndicator} styled with
 * the active {@link TermuxColorPalette}.
 */
public final class BootstrapProgressDialog {

    private final AlertDialog mDialog;
    private final TextView mHeaderView;
    private final TextView mStageTitleView;
    private final TextView mStageDetailView;
    private final LinearProgressIndicator mProgressBar;
    private final TextView mPercentView;

    private BootstrapProgressDialog(@NonNull AlertDialog dialog,
                                    @NonNull TextView headerView,
                                    @NonNull TextView stageTitleView,
                                    @NonNull TextView stageDetailView,
                                    @NonNull LinearProgressIndicator progressBar,
                                    @NonNull TextView percentView) {
        mDialog = dialog;
        mHeaderView = headerView;
        mStageTitleView = stageTitleView;
        mStageDetailView = stageDetailView;
        mProgressBar = progressBar;
        mPercentView = percentView;
    }

    @NonNull
    public static BootstrapProgressDialog show(@NonNull Activity activity) {
        TermuxColorPalette palette = TermuxThemeManager.getActivePalette(activity);
        float density = activity.getResources().getDisplayMetrics().density;

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_bootstrap_progress, null, false);
        TextView headerView = view.findViewById(R.id.bootstrap_dialog_header);
        TextView stageTitleView = view.findViewById(R.id.bootstrap_stage_title);
        TextView stageDetailView = view.findViewById(R.id.bootstrap_stage_detail);
        LinearProgressIndicator progressBar = view.findViewById(R.id.bootstrap_progress_bar);
        TextView percentView = view.findViewById(R.id.bootstrap_progress_percent);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setCornerRadius(24f * density);
        cardBg.setColor(palette.surfaceContainerHigh);
        view.setBackground(cardBg);

        headerView.setTextColor(palette.onSurface);
        stageTitleView.setTextColor(palette.onSurface);
        stageDetailView.setTextColor(palette.onSurfaceVariant);
        percentView.setTextColor(palette.accent);

        progressBar.setIndicatorColor(palette.accent);
        progressBar.setTrackColor(palette.surfaceContainer);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setCancelable(false)
            .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        return new BootstrapProgressDialog(dialog, headerView, stageTitleView, stageDetailView, progressBar, percentView);
    }

    public void updateStage(@NonNull String title, @Nullable String detail, int progressPercent) {
        mStageTitleView.setText(title);

        if (detail != null && !detail.isEmpty()) {
            mStageDetailView.setText(detail);
            mStageDetailView.setVisibility(View.VISIBLE);
        } else {
            mStageDetailView.setVisibility(View.GONE);
        }

        if (progressPercent >= 0) {
            int clamped = Math.max(0, Math.min(100, progressPercent));
            mProgressBar.setIndeterminate(false);
            mProgressBar.setProgress(clamped);
            mPercentView.setText(String.format(Locale.US, "%d%%", clamped));
            mPercentView.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setIndeterminate(true);
            mPercentView.setVisibility(View.GONE);
        }
    }

    public boolean isShowing() {
        return mDialog.isShowing();
    }

    public void dismiss() {
        mDialog.dismiss();
    }

    @NonNull
    public AlertDialog getDialog() {
        return mDialog;
    }

    @NonNull
    public TextView getStageTitleView() {
        return mStageTitleView;
    }

    @NonNull
    public TextView getStageDetailView() {
        return mStageDetailView;
    }

    @NonNull
    public LinearProgressIndicator getProgressBar() {
        return mProgressBar;
    }
}
