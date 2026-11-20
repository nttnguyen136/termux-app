package com.termux.app.activities;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.termux.R;
import com.termux.shared.termux.extrakeys.ExtraKeysLayoutModel;
import com.termux.shared.termux.extrakeys.ExtraKeysLayoutModel.Key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The dialog to add or edit an extra key in the {@link ExtraKeysEditorActivity}. A key can be
 * edited as a single key, a combo of modifiers with one key, or a macro of a sequence of keys.
 * Combos and macros are both saved as {@code macro}.
 */
public class ExtraKeyEditDialog {

    /** The callbacks for the dialog actions. */
    public interface Callbacks {
        /** Called when OK is pressed with the edited key. */
        void onSave(@NonNull Key key);
        /** Called when Delete is pressed. Only called if an existing key is being edited. */
        void onDelete();
        /**
         * Called when a move button is pressed. The move is applied immediately, while the dialog
         * stays open. Only called if {@code canMove} was passed.
         *
         * @param dx The column offset, {@code -1} for left and {@code 1} for right.
         * @param dy The row offset, {@code -1} for up and {@code 1} for down.
         */
        void onMove(int dx, int dy);
    }

    /** The special keys suggested in the key fields. */
    public static final List<String> SPECIAL_KEYS = Arrays.asList(
        "ESC", "TAB", "CTRL", "ALT", "SHIFT", "FN", "ENTER", "SPACE", "BKSP", "DEL", "INS",
        "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PGUP", "PGDN",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "KEYBOARD", "DRAWER", "PASTE", "SCROLL", "SETTINGS", "TERMUX_SETTINGS",
        "LAYOUT", "LAYOUT_1", "LAYOUT_2", "LAYOUT_3", "LAYOUT_4", "LAYOUT_5");

    /** The keys shown as quick insert buttons for macros. */
    private static final List<String> MACRO_QUICK_KEYS = Arrays.asList(
        "CTRL", "ALT", "SHIFT", "ESC", "TAB", "ENTER", "SPACE", "BKSP", "UP", "DOWN", "LEFT", "RIGHT");

    private final Context mContext;
    private final boolean mIsPopup;
    private final Callbacks mCallbacks;

    private RadioGroup mTypeGroup;
    private RadioButton mSingleRadio, mComboRadio, mMacroRadio;
    private View mSinglePanel, mComboPanel, mMacroPanel;
    private AutoCompleteTextView mSingleKeyText;
    private LinearLayout mComboStepsContainer;
    private final List<ComboStepViews> mComboSteps = new ArrayList<>();
    private EditText mMacroText;
    private EditText mDisplayText;
    private TextView mPopupSummaryText;
    @Nullable private Key mPopup;

    private ExtraKeyEditDialog(@NonNull Context context, boolean isPopup, @NonNull Callbacks callbacks) {
        mContext = context;
        mIsPopup = isPopup;
        mCallbacks = callbacks;
    }

    /**
     * Show the dialog.
     *
     * @param context The context.
     * @param key The key to edit, or {@code null} to add a new key.
     * @param isPopup If editing a popup key, which can't have its own popup.
     * @param canMove If the move buttons should be shown.
     * @param callbacks The {@link Callbacks}.
     */
    public static void show(@NonNull Context context, @Nullable Key key, boolean isPopup, boolean canMove,
                            @NonNull Callbacks callbacks) {
        new ExtraKeyEditDialog(context, isPopup, callbacks).show(key, canMove);
    }

    private void show(@Nullable Key key, boolean canMove) {
        View content = createContent(canMove);
        setKey(key);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
            .setTitle(mIsPopup ? R.string.extra_key_dialog_popup_title :
                (key == null ? R.string.extra_key_dialog_add_title : R.string.extra_key_dialog_title))
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null);
        if (key != null)
            builder.setNeutralButton(R.string.extra_key_dialog_delete, (d, w) -> mCallbacks.onDelete());

        AlertDialog dialog = builder.create();
        dialog.show();
        // Set the listener after show() so that the dialog is not dismissed if the key is invalid
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Key newKey = buildKey();
            if (newKey == null) return;
            mCallbacks.onSave(newKey);
            dialog.dismiss();
        });
    }

    private int dp(float value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }

    @NonNull
    private View createContent(boolean canMove) {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(8));

        // Type
        mTypeGroup = new RadioGroup(mContext);
        mTypeGroup.setOrientation(RadioGroup.HORIZONTAL);
        mSingleRadio = addRadio(mTypeGroup, R.string.extra_key_dialog_type_single);
        mComboRadio = addRadio(mTypeGroup, R.string.extra_key_dialog_type_combo);
        mMacroRadio = addRadio(mTypeGroup, R.string.extra_key_dialog_type_macro);
        mTypeGroup.setOnCheckedChangeListener((group, checkedId) -> updatePanels());
        root.addView(mTypeGroup);

        // Single
        mSingleKeyText = createKeyField(R.string.extra_key_dialog_key_hint);
        mSinglePanel = mSingleKeyText;
        root.addView(mSinglePanel);

        // Combo, a sequence of steps of modifiers and one key, like Ctrl+b then $
        LinearLayout comboPanel = new LinearLayout(mContext);
        comboPanel.setOrientation(LinearLayout.VERTICAL);
        mComboStepsContainer = new LinearLayout(mContext);
        mComboStepsContainer.setOrientation(LinearLayout.VERTICAL);
        comboPanel.addView(mComboStepsContainer);
        Button addStep = createSmallButton(mContext.getString(R.string.extra_key_dialog_combo_add_step));
        addStep.setOnClickListener(v -> addComboStep(null));
        comboPanel.addView(addStep);
        mComboPanel = comboPanel;
        root.addView(mComboPanel);

        // Macro
        LinearLayout macroPanel = new LinearLayout(mContext);
        macroPanel.setOrientation(LinearLayout.VERTICAL);
        mMacroText = new EditText(mContext);
        mMacroText.setHint(R.string.extra_key_dialog_macro_hint);
        mMacroText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        macroPanel.addView(mMacroText);
        LinearLayout quickKeysRow = new LinearLayout(mContext);
        for (String quickKey : MACRO_QUICK_KEYS) {
            Button button = createSmallButton(quickKey);
            button.setOnClickListener(v -> insertMacroToken(quickKey));
            quickKeysRow.addView(button);
        }
        HorizontalScrollView quickKeysScroll = new HorizontalScrollView(mContext);
        quickKeysScroll.addView(quickKeysRow);
        macroPanel.addView(quickKeysScroll);
        mMacroPanel = macroPanel;
        root.addView(mMacroPanel);

        // Display
        mDisplayText = new EditText(mContext);
        mDisplayText.setHint(R.string.extra_key_dialog_display_hint);
        mDisplayText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        mDisplayText.setSingleLine(true);
        root.addView(mDisplayText);

        // Popup
        if (!mIsPopup) {
            mPopupSummaryText = new TextView(mContext);
            mPopupSummaryText.setPadding(0, dp(12), 0, 0);
            root.addView(mPopupSummaryText);

            LinearLayout popupRow = new LinearLayout(mContext);
            Button editPopup = createSmallButton(mContext.getString(R.string.extra_key_dialog_popup_edit));
            editPopup.setOnClickListener(v -> ExtraKeyEditDialog.show(mContext, mPopup, true, false, new Callbacks() {
                @Override public void onSave(@NonNull Key key) { mPopup = key; updatePopupSummary(); }
                @Override public void onDelete() { mPopup = null; updatePopupSummary(); }
                @Override public void onMove(int dx, int dy) {}
            }));
            popupRow.addView(editPopup);
            Button removePopup = createSmallButton(mContext.getString(R.string.extra_key_dialog_popup_remove));
            removePopup.setOnClickListener(v -> { mPopup = null; updatePopupSummary(); });
            popupRow.addView(removePopup);
            root.addView(popupRow);
        }

        // Move
        if (canMove) {
            LinearLayout moveRow = new LinearLayout(mContext);
            moveRow.setPadding(0, dp(8), 0, 0);
            TextView moveLabel = new TextView(mContext);
            moveLabel.setText(R.string.extra_key_dialog_move);
            moveLabel.setPadding(0, 0, dp(8), 0);
            moveRow.addView(moveLabel);
            moveRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            addMoveButton(moveRow, "◀", -1, 0);
            addMoveButton(moveRow, "▶", 1, 0);
            addMoveButton(moveRow, "▲", 0, -1);
            addMoveButton(moveRow, "▼", 0, 1);
            root.addView(moveRow);
        }

        ScrollView scrollView = new ScrollView(mContext);
        scrollView.addView(root);
        return scrollView;
    }

    private RadioButton addRadio(@NonNull RadioGroup group, int textResId) {
        RadioButton radio = new RadioButton(mContext);
        radio.setId(View.generateViewId());
        radio.setText(textResId);
        group.addView(radio);
        return radio;
    }

    @NonNull
    private AutoCompleteTextView createKeyField(int hintResId) {
        AutoCompleteTextView field = new AutoCompleteTextView(mContext);
        field.setHint(hintResId);
        field.setSingleLine(true);
        field.setThreshold(1);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setAdapter(new ArrayAdapter<>(mContext, android.R.layout.simple_dropdown_item_1line, SPECIAL_KEYS));
        return field;
    }

    @NonNull
    private Button createSmallButton(@NonNull String text) {
        Button button = new Button(mContext, null, android.R.attr.buttonBarButtonStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private void addMoveButton(@NonNull ViewGroup parent, @NonNull String text, int dx, int dy) {
        Button button = createSmallButton(text);
        button.setOnClickListener(v -> mCallbacks.onMove(dx, dy));
        parent.addView(button);
    }

    /** The views of a combo step. */
    private static class ComboStepViews {
        final List<CheckBox> modifierCheckBoxes = new ArrayList<>();
        AutoCompleteTextView keyText;
        TextView label;
    }

    /** Add a combo step, optionally filled from {@code step}. */
    private void addComboStep(@Nullable ExtraKeysLayoutModel.ComboStep step) {
        LinearLayout stepView = new LinearLayout(mContext);
        stepView.setOrientation(LinearLayout.VERTICAL);
        stepView.setPadding(0, dp(4), 0, dp(4));
        ComboStepViews views = new ComboStepViews();

        LinearLayout header = new LinearLayout(mContext);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        views.label = new TextView(mContext);
        header.addView(views.label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button remove = createSmallButton("✕");
        remove.setOnClickListener(v -> {
            if (mComboSteps.size() <= 1) return;
            mComboSteps.remove(views);
            mComboStepsContainer.removeView(stepView);
            updateComboStepLabels();
        });
        header.addView(remove);
        stepView.addView(header);

        LinearLayout modifiersRow = new LinearLayout(mContext);
        for (String modifier : ExtraKeysLayoutModel.MODIFIER_KEYS) {
            CheckBox checkBox = new CheckBox(mContext);
            checkBox.setText(modifier);
            checkBox.setChecked(step != null && step.modifiers.contains(modifier));
            modifiersRow.addView(checkBox);
            views.modifierCheckBoxes.add(checkBox);
        }
        HorizontalScrollView modifiersScroll = new HorizontalScrollView(mContext);
        modifiersScroll.addView(modifiersRow);
        stepView.addView(modifiersScroll);

        views.keyText = createKeyField(R.string.extra_key_dialog_combo_key_hint);
        if (step != null) views.keyText.setText(step.key);
        stepView.addView(views.keyText);

        mComboSteps.add(views);
        mComboStepsContainer.addView(stepView);
        updateComboStepLabels();
    }

    private void updateComboStepLabels() {
        for (int i = 0; i < mComboSteps.size(); i++)
            mComboSteps.get(i).label.setText(mContext.getString(R.string.extra_key_dialog_combo_step, i + 1));
    }

    private void insertMacroToken(@NonNull String token) {
        String text = mMacroText.getText().toString();
        if (!text.isEmpty() && !text.endsWith(" ")) text += " ";
        text += token + " ";
        mMacroText.setText(text);
        mMacroText.setSelection(text.length());
    }

    private void updatePanels() {
        int checked = mTypeGroup.getCheckedRadioButtonId();
        mSinglePanel.setVisibility(checked == mSingleRadio.getId() ? View.VISIBLE : View.GONE);
        mComboPanel.setVisibility(checked == mComboRadio.getId() ? View.VISIBLE : View.GONE);
        mMacroPanel.setVisibility(checked == mMacroRadio.getId() ? View.VISIBLE : View.GONE);
    }

    private void updatePopupSummary() {
        if (mPopupSummaryText == null) return;
        mPopupSummaryText.setText(mContext.getString(R.string.extra_key_dialog_popup,
            mPopup == null ? mContext.getString(R.string.extra_key_dialog_popup_none) : mPopup.getLabel()));
    }

    private void setKey(@Nullable Key key) {
        if (key == null || key.type == Key.Type.KEY) {
            mSingleRadio.setChecked(true);
            if (key != null) mSingleKeyText.setText(key.value);
        } else if (key.isCombo()) {
            mComboRadio.setChecked(true);
            List<ExtraKeysLayoutModel.ComboStep> steps = ExtraKeysLayoutModel.parseComboSteps(key.value);
            if (steps != null)
                for (ExtraKeysLayoutModel.ComboStep step : steps) addComboStep(step);
        } else {
            mMacroRadio.setChecked(true);
            mMacroText.setText(key.value);
        }

        if (key != null && key.display != null) mDisplayText.setText(key.display);
        if (mComboSteps.isEmpty()) addComboStep(null);
        mPopup = key == null || key.popup == null ? null : key.popup.copy();
        updatePopupSummary();
        updatePanels();
    }

    /** Build the key from the fields, or show an error and return {@code null} if invalid. */
    @Nullable
    private Key buildKey() {
        String display = mDisplayText.getText().toString();
        if (display.trim().isEmpty()) display = null;
        Key popup = mIsPopup ? null : mPopup;

        int checked = mTypeGroup.getCheckedRadioButtonId();
        if (checked == mComboRadio.getId()) {
            List<ExtraKeysLayoutModel.ComboStep> steps = new ArrayList<>();
            boolean hasModifier = false;
            for (ComboStepViews stepViews : mComboSteps) {
                String key = stepViews.keyText.getText().toString().trim();
                if (key.isEmpty() || key.contains(" ") || ExtraKeysLayoutModel.MODIFIER_KEYS.contains(key)) {
                    stepViews.keyText.setError(mContext.getString(R.string.extra_key_dialog_value_required));
                    return null;
                }
                List<String> modifiers = new ArrayList<>();
                for (int i = 0; i < stepViews.modifierCheckBoxes.size(); i++)
                    if (stepViews.modifierCheckBoxes.get(i).isChecked())
                        modifiers.add(ExtraKeysLayoutModel.MODIFIER_KEYS.get(i));
                if (!modifiers.isEmpty()) hasModifier = true;
                steps.add(new ExtraKeysLayoutModel.ComboStep(modifiers, key));
            }
            if (steps.isEmpty()) return null;
            // A single step without any modifier is just a single key
            if (steps.size() == 1 && !hasModifier) return new Key(Key.Type.KEY, steps.get(0).key, display, popup);
            return new Key(Key.Type.MACRO, ExtraKeysLayoutModel.serializeComboSteps(steps), display, popup);
        } else if (checked == mMacroRadio.getId()) {
            String[] tokens = ExtraKeysLayoutModel.splitMacro(mMacroText.getText().toString());
            if (tokens.length == 0) {
                mMacroText.setError(mContext.getString(R.string.extra_key_dialog_value_required));
                return null;
            }
            StringBuilder macro = new StringBuilder();
            for (String token : tokens) {
                if (macro.length() > 0) macro.append(' ');
                macro.append(token);
            }
            return new Key(Key.Type.MACRO, macro.toString(), display, popup);
        } else {
            String key = mSingleKeyText.getText().toString().trim();
            if (key.isEmpty()) {
                mSingleKeyText.setError(mContext.getString(R.string.extra_key_dialog_value_required));
                return null;
            }
            return new Key(Key.Type.KEY, key, display, popup);
        }
    }

}
