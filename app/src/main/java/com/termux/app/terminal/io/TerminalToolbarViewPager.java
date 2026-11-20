package com.termux.app.terminal.io;

import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.theme.TermuxAppearanceAware;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.terminal.TerminalSession;

/**
 * The terminal toolbar pages. Pages {@code 0..N-1} are the extra keys layouts returned by
 * {@link TermuxTerminalExtraKeys#getExtraKeysLayouts()} and the last page {@code N} is the text input.
 */
public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter implements TermuxAppearanceAware {

        final TermuxActivity mActivity;
        String mSavedTextInput;
        @Nullable
        private EditText mTextInputEditText;

        /** The currently instantiated extra keys views by their page position. */
        final SparseArray<ExtraKeysView> mExtraKeysViews = new SparseArray<>();

        public PageAdapter(TermuxActivity activity, String savedTextInput) {
            this.mActivity = activity;
            this.mSavedTextInput = savedTextInput;
        }

        /** Get the number of extra keys layout pages. The text input page is at this position. */
        public int getExtraKeysLayoutCount() {
            return mActivity.getTermuxTerminalExtraKeys().getExtraKeysLayoutCount();
        }

        /** Check if {@code position} is the text input page. */
        public boolean isTextInputPage(int position) {
            return position == getExtraKeysLayoutCount();
        }

        /** Get the {@link ExtraKeysView} at {@code position}, or {@code null} if it is the text
         * input page or not instantiated. */
        @Nullable
        public ExtraKeysView getExtraKeysView(int position) {
            return mExtraKeysViews.get(position);
        }

        /** Get the saved text input that has not been set to the text input page yet, since the
         * page has not been instantiated. */
        @Nullable
        public String getPendingSavedTextInput() {
            return mSavedTextInput;
        }

        @Override
        public int getCount() {
            return getExtraKeysLayoutCount() + 1;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            View layout;
            if (!isTextInputPage(position)) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false);
                ExtraKeysView extraKeysView = (ExtraKeysView) layout;
                extraKeysView.setExtraKeysViewClient(mActivity.getTermuxTerminalExtraKeys());
                extraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
                mExtraKeysViews.put(position, extraKeysView);
                // The activity reads the modifier keys state from the extra keys view of the current page
                if (mActivity.getTerminalToolbarViewPager().getCurrentItem() == position)
                    mActivity.setExtraKeysView(extraKeysView);
                extraKeysView.reload(mActivity.getTermuxTerminalExtraKeys().getExtraKeysInfo(position),
                    mActivity.getTerminalToolbarDefaultHeight());
                extraKeysView.applyAppearance(
                    TermuxThemeManager.getActivePalette(mActivity),
                    TermuxFontManager.getActiveTypeface(mActivity)
                );

                // apply extra keys fix if enabled in prefs
                if (mActivity.getProperties().isUsingFullScreen() && mActivity.getProperties().isUsingFullScreenWorkAround()) {
                    mActivity.applyFullScreenWorkAroundOnce();
                }

            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, collection, false);
                final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);
                mTextInputEditText = editText;

                applyTextInputAppearance(
                    editText,
                    TermuxThemeManager.getActivePalette(mActivity),
                    TermuxFontManager.getActiveTypeface(mActivity)
                );

                if (mSavedTextInput != null) {
                    editText.setText(mSavedTextInput);
                    mSavedTextInput = null;
                }

                editText.setOnEditorActionListener((v, actionId, event) -> {
                    TerminalSession session = mActivity.getCurrentSession();
                    if (session != null) {
                        if (session.isRunning()) {
                            String textToSend = editText.getText().toString();
                            if (textToSend.length() == 0) textToSend = "\r";
                            session.write(textToSend);
                        } else {
                            mActivity.getTermuxTerminalSessionClient().removeFinishedSession(session);
                        }
                        editText.setText("");
                    }
                    return true;
                });
            }
            collection.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
            if (isTextInputPage(position)) {
                mTextInputEditText = null;
            } else {
                ExtraKeysView extraKeysView = mExtraKeysViews.get(position);
                if (extraKeysView != null) {
                    mExtraKeysViews.remove(position);
                    if (mActivity.getExtraKeysView() == extraKeysView)
                        mActivity.setExtraKeysView(null);
                }
            }
            collection.removeView((View) view);
        }

        @Override
        public void applyAppearance(@NonNull TermuxColorPalette palette, @NonNull Typeface typeface) {
            for (int i = 0; i < mExtraKeysViews.size(); i++) {
                ExtraKeysView extraKeysView = mExtraKeysViews.valueAt(i);
                if (extraKeysView != null) {
                    extraKeysView.applyAppearance(palette, typeface);
                }
            }
            if (mTextInputEditText != null) {
                applyTextInputAppearance(mTextInputEditText, palette, typeface);
            }
        }

        public static void applyTextInputAppearance(
            @NonNull EditText editText,
            @NonNull TermuxColorPalette palette,
            @NonNull Typeface typeface
        ) {
            editText.setBackgroundColor(palette.toolbarSurface);
            editText.setTextColor(palette.onSurface);
            editText.setHintTextColor(palette.onSurfaceVariant);
            editText.setHighlightColor(ColorUtils.setAlphaComponent(palette.accent, 96));
            editText.setTypeface(typeface);
        }

    }



    public static class OnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        final TermuxActivity mActivity;
        final ViewPager mTerminalToolbarViewPager;

        public OnPageChangeListener(TermuxActivity activity, ViewPager viewPager) {
            this.mActivity = activity;
            this.mTerminalToolbarViewPager = viewPager;
        }

        @Override
        public void onPageSelected(int position) {
            if (!(mTerminalToolbarViewPager.getAdapter() instanceof PageAdapter)) return;
            PageAdapter adapter = (PageAdapter) mTerminalToolbarViewPager.getAdapter();

            // Modifier keys are per layout and are reset when switching away from a layout
            ExtraKeysView previousExtraKeysView = mActivity.getExtraKeysView();
            if (previousExtraKeysView != null)
                previousExtraKeysView.resetSpecialButtons();
            mActivity.setExtraKeysView(adapter.getExtraKeysView(position));

            if (!adapter.isTextInputPage(position)) {
                mActivity.getTerminalView().requestFocus();
            } else {
                final EditText editText = mTerminalToolbarViewPager.findViewById(R.id.terminal_toolbar_text_input);
                if (editText != null) editText.requestFocus();
            }
        }

    }

}
