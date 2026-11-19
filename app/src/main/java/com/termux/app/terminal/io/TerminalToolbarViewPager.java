package com.termux.app.terminal.io;

import android.graphics.Typeface;
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

public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter implements TermuxAppearanceAware {

        final TermuxActivity mActivity;
        String mSavedTextInput;
        @Nullable
        private EditText mTextInputEditText;

        public PageAdapter(TermuxActivity activity, String savedTextInput) {
            this.mActivity = activity;
            this.mSavedTextInput = savedTextInput;
        }

        @Override
        public int getCount() {
            return 2;
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
            if (position == 0) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false);
                ExtraKeysView extraKeysView = (ExtraKeysView) layout;
                extraKeysView.setExtraKeysViewClient(mActivity.getTermuxTerminalExtraKeys());
                extraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
                mActivity.setExtraKeysView(extraKeysView);
                extraKeysView.reload(mActivity.getTermuxTerminalExtraKeys().getExtraKeysInfo(),
                    mActivity.getTerminalToolbarDefaultHeight());

                // apply extra keys fix if enabled in prefs
                if (mActivity.getProperties().isUsingFullScreen() && mActivity.getProperties().isUsingFullScreenWorkAround()) {
                    FullScreenWorkAround.apply(mActivity);
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
            if (position == 1 && mTextInputEditText != null && view == mTextInputEditText) {
                mTextInputEditText = null;
            }
            collection.removeView((View) view);
        }

        @Override
        public void applyAppearance(@NonNull TermuxColorPalette palette, @NonNull Typeface typeface) {
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
            if (position == 0) {
                mActivity.getTerminalView().requestFocus();
            } else {
                final EditText editText = mTerminalToolbarViewPager.findViewById(R.id.terminal_toolbar_text_input);
                if (editText != null) editText.requestFocus();
            }
        }

    }

}
