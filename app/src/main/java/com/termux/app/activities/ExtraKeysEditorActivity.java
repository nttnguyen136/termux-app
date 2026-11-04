package com.termux.app.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.properties.PropertiesFileEditor;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysLayoutModel;
import com.termux.shared.termux.extrakeys.ExtraKeysLayoutModel.Key;
import com.termux.shared.termux.extrakeys.ExtraKeysLayoutModel.Layout;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.shared.theme.NightMode;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * An editor for the extra keys layouts defined by the {@code extra-keys[-N]} properties, and the
 * related {@code extra-keys-style}, {@code extra-keys-text-all-caps} and
 * {@code terminal-toolbar-height} properties.
 *
 * Changes are written to the {@code termux.properties} file in use, while keeping its comments and
 * other properties, and the {@link TermuxActivity} reloads them.
 */
public class ExtraKeysEditorActivity extends AppCompatActivity {

    private static final List<String> STYLES = Arrays.asList("default", "arrows-only", "arrows-all", "all", "none");

    /** The height of a row of extra keys, same as the default height of the terminal toolbar. */
    private static final float ROW_HEIGHT_DP = 37.5f;

    private static final String LOG_TAG = "ExtraKeysEditorActivity";

    private LinearLayout mContainer;

    /** The properties file to write to. */
    private File mPropertiesFile;

    private final List<Layout> mLayouts = new ArrayList<>();
    private String mStyle;
    private boolean mTextAllCaps;
    private String mToolbarHeight;

    /** The values loaded from the file, {@code null} if not set. */
    @Nullable private String mLoadedStyle, mLoadedTextAllCaps, mLoadedToolbarHeight;

    /** The state when loaded or last saved, to detect unsaved changes. */
    private String mSavedState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_extra_keys_editor);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        mContainer = findViewById(R.id.extra_keys_editor_container);

        load();
        mSavedState = getState();
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.extra_keys_editor_save)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            save();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (getState().equals(mSavedState)) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.extra_keys_editor_discard_title)
            .setMessage(R.string.extra_keys_editor_discard_message)
            .setPositiveButton(R.string.extra_keys_editor_discard, (d, w) -> finish())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }



    /** Load the layouts and options from the properties file. */
    private void load() {
        File file = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        mPropertiesFile = file != null ? file : TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;

        Properties properties = new Properties();
        if (file != null) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read " + file, e);
            }
        }

        // Load layouts, skipping missing numbers
        List<Integer> loadedNumbers = new ArrayList<>();
        for (int number = 1; number <= TermuxPropertyConstants.MAX_EXTRA_KEYS_LAYOUTS; number++) {
            String key = TermuxPropertyConstants.getExtraKeysLayoutKey(number);
            String value = properties.getProperty(key);
            if (value == null || value.trim().isEmpty()) {
                if (number > 1) continue;
                value = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;
            }
            try {
                mLayouts.add(ExtraKeysLayoutModel.parseLayout(value));
                loadedNumbers.add(number);
            } catch (JSONException e) {
                Logger.showToast(this, getString(R.string.extra_keys_editor_load_failed, key, e.getMessage()), true);
                if (number == 1) {
                    mLayouts.add(getDefaultLayout());
                    loadedNumbers.add(number);
                }
            }
        }

        // Make LAYOUT_N refer to the position of the layout, since missing numbers were skipped.
        // References to layouts that do not exist are kept invalid.
        final int count = mLayouts.size();
        ExtraKeysLayoutModel.remapLayoutReferences(mLayouts, number -> {
            int index = loadedNumbers.indexOf(number);
            if (index >= 0) return index + 1;
            return number > count ? number : TermuxPropertyConstants.MAX_EXTRA_KEYS_LAYOUTS;
        });

        mLoadedStyle = properties.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE);
        mLoadedTextAllCaps = properties.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS);
        mLoadedToolbarHeight = properties.getProperty(TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR);

        mStyle = mLoadedStyle != null && !mLoadedStyle.trim().isEmpty() ? mLoadedStyle.trim() : TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
        mTextAllCaps = mLoadedTextAllCaps == null || !"false".equalsIgnoreCase(mLoadedTextAllCaps.trim());
        mToolbarHeight = mLoadedToolbarHeight != null && !mLoadedToolbarHeight.trim().isEmpty() ? mLoadedToolbarHeight.trim() :
            String.valueOf(TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR);
    }

    @NonNull
    private static Layout getDefaultLayout() {
        try {
            return ExtraKeysLayoutModel.parseLayout(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS);
        } catch (JSONException e) {
            // Should not happen
            return new Layout(new ArrayList<>(Collections.singletonList(
                new ArrayList<>(Collections.singletonList(Key.single("ESC"))))));
        }
    }

    @NonNull
    private String getState() {
        StringBuilder sb = new StringBuilder();
        for (Layout layout : mLayouts) sb.append(ExtraKeysLayoutModel.serializeLayout(layout)).append('\n');
        sb.append(mStyle).append('\n').append(mTextAllCaps).append('\n').append(mToolbarHeight);
        return sb.toString();
    }



    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** Rebuild all the views from the current state. */
    private void render() {
        mContainer.removeAllViews();
        mContainer.addView(createGeneralSection());
        for (int i = 0; i < mLayouts.size(); i++)
            mContainer.addView(createLayoutSection(i));

        Button addLayout = createButton(getString(R.string.extra_keys_editor_add_layout));
        addLayout.setEnabled(mLayouts.size() < TermuxPropertyConstants.MAX_EXTRA_KEYS_LAYOUTS);
        addLayout.setOnClickListener(v -> {
            mLayouts.add(new Layout(new ArrayList<>(Collections.singletonList(
                new ArrayList<>(Collections.singletonList(Key.single(ExtraKeysLayoutModel.KEY_LAYOUT)))))));
            render();
        });
        mContainer.addView(addLayout);
    }

    @NonNull
    private TextView createHeader(@NonNull String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextSize(18);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(0, dp(16), 0, dp(4));
        return header;
    }

    @NonNull
    private Button createButton(@NonNull String text) {
        Button button = new Button(this, null, android.R.attr.buttonBarButtonStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), dp(6), dp(10), dp(6));
        return button;
    }

    @NonNull
    private View createGeneralSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(createHeader(getString(R.string.extra_keys_editor_general)));

        // Style
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView styleLabel = new TextView(this);
        styleLabel.setText(R.string.extra_keys_editor_style);
        styleLabel.setPadding(0, 0, dp(12), 0);
        styleRow.addView(styleLabel);
        List<String> styles = new ArrayList<>(STYLES);
        if (!styles.contains(mStyle)) styles.add(mStyle);
        Spinner styleSpinner = new Spinner(this);
        styleSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, styles));
        styleSpinner.setSelection(styles.indexOf(mStyle));
        styleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String style = styles.get(position);
                if (style.equals(mStyle)) return;
                mStyle = style;
                render();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        styleRow.addView(styleSpinner);
        section.addView(styleRow);

        // Text all caps
        CheckBox allCaps = new CheckBox(this);
        allCaps.setText(R.string.extra_keys_editor_text_all_caps);
        allCaps.setChecked(mTextAllCaps);
        allCaps.setOnCheckedChangeListener((v, checked) -> {
            mTextAllCaps = checked;
            render();
        });
        section.addView(allCaps);

        // Toolbar height
        TextView heightLabel = new TextView(this);
        heightLabel.setText(R.string.extra_keys_editor_toolbar_height);
        heightLabel.setPadding(0, dp(8), 0, 0);
        section.addView(heightLabel);
        EditText height = new EditText(this);
        height.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        height.setText(mToolbarHeight);
        height.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { mToolbarHeight = s.toString().trim(); }
        });
        section.addView(height);

        return section;
    }

    @NonNull
    private View createLayoutSection(final int layoutIndex) {
        final Layout layout = mLayouts.get(layoutIndex);

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        // Header with layout actions
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView header = createHeader(getString(R.string.extra_keys_editor_layout_title, layoutIndex + 1));
        headerRow.addView(header, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button up = createButton("▲");
        up.setEnabled(layoutIndex > 0);
        up.setOnClickListener(v -> { ExtraKeysLayoutModel.moveLayout(mLayouts, layoutIndex, layoutIndex - 1); render(); });
        headerRow.addView(up);

        Button down = createButton("▼");
        down.setEnabled(layoutIndex < mLayouts.size() - 1);
        down.setOnClickListener(v -> { ExtraKeysLayoutModel.moveLayout(mLayouts, layoutIndex, layoutIndex + 1); render(); });
        headerRow.addView(down);

        Button reset = createButton(getString(R.string.extra_keys_editor_reset_layout));
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setMessage(R.string.extra_keys_editor_reset_layout_confirm)
            .setPositiveButton(android.R.string.ok, (d, w) -> { mLayouts.set(layoutIndex, getDefaultLayout()); render(); })
            .setNegativeButton(android.R.string.cancel, null)
            .show());
        headerRow.addView(reset);

        Button delete = createButton("✕");
        delete.setEnabled(mLayouts.size() > 1);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setMessage(getString(R.string.extra_keys_editor_delete_layout_confirm, layoutIndex + 1))
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                int replaced = ExtraKeysLayoutModel.removeLayout(mLayouts, layoutIndex);
                if (replaced > 0)
                    Logger.showToast(this, getString(R.string.extra_keys_editor_references_replaced, replaced), true);
                render();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show());
        headerRow.addView(delete);
        section.addView(headerRow);

        // Preview with the real ExtraKeysView
        section.addView(createPreview(layout));

        // Rows
        for (int rowIndex = 0; rowIndex < layout.rows.size(); rowIndex++)
            section.addView(createRow(layout, rowIndex));

        Button addRow = createButton(getString(R.string.extra_keys_editor_add_row));
        addRow.setOnClickListener(v -> { layout.rows.add(new ArrayList<>()); render(); });
        LinearLayout addRowContainer = new LinearLayout(this);
        addRowContainer.setGravity(Gravity.END);
        addRowContainer.addView(addRow);
        section.addView(addRowContainer);

        return section;
    }

    @NonNull
    private View createPreview(@NonNull Layout layout) {
        int rowCount = 0;
        for (List<Key> row : layout.rows) if (!row.isEmpty()) rowCount++;

        try {
            if (rowCount == 0) throw new JSONException("no keys");
            Layout nonEmpty = layout.copy();
            removeEmptyRows(nonEmpty);
            ExtraKeysInfo info = new ExtraKeysInfo(ExtraKeysLayoutModel.serializeLayout(nonEmpty), mStyle,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);

            FrameLayout frame = new FrameLayout(this);
            TermuxColorPalette palette = TermuxThemeManager.getActivePalette(this);
            frame.setBackgroundColor(palette.toolbarSurface);
            ExtraKeysView preview = new ExtraKeysView(this, null);
            preview.setButtonTextAllCaps(mTextAllCaps);
            frame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ROW_HEIGHT_DP) * rowCount));
            preview.reload(info, dp(ROW_HEIGHT_DP));
            preview.applyAppearance(palette, TermuxFontManager.getActiveTypeface(this));
            return frame;
        } catch (JSONException e) {
            TextView error = new TextView(this);
            error.setText(getString(R.string.extra_keys_editor_preview_error, e.getMessage()));
            error.setTextColor(0xFFE53935);
            return error;
        }
    }

    @NonNull
    private View createRow(@NonNull Layout layout, final int rowIndex) {
        final List<Key> row = layout.rows.get(rowIndex);

        LinearLayout rowView = new LinearLayout(this);
        rowView.setGravity(Gravity.CENTER_VERTICAL);
        rowView.setPadding(0, dp(4), 0, dp(4));

        TextView label = new TextView(this);
        label.setText(getString(R.string.extra_keys_editor_row, rowIndex + 1));
        label.setPadding(0, 0, dp(8), 0);
        rowView.addView(label);

        // Keys
        LinearLayout keys = new LinearLayout(this);
        keys.setGravity(Gravity.CENTER_VERTICAL);
        for (final Key key : row) {
            String text = key.getLabel();
            if (key.popup != null) text += "  ↑" + key.popup.getLabel();
            Button keyButton = createButton(text);
            keyButton.setOnClickListener(v -> editKey(layout, key));
            keys.addView(keyButton);
        }
        Button addKey = createButton("+");
        addKey.setOnClickListener(v -> ExtraKeyEditDialog.show(this, null, false, false, new ExtraKeyEditDialog.Callbacks() {
            @Override public void onSave(@NonNull Key key) { row.add(key); render(); }
            @Override public void onDelete() {}
            @Override public void onMove(int dx, int dy) {}
        }));
        keys.addView(addKey);
        HorizontalScrollView keysScroll = new HorizontalScrollView(this);
        keysScroll.addView(keys);
        rowView.addView(keysScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Row actions
        Button up = createButton("▲");
        up.setEnabled(rowIndex > 0);
        up.setOnClickListener(v -> { Collections.swap(layout.rows, rowIndex, rowIndex - 1); render(); });
        rowView.addView(up);

        Button down = createButton("▼");
        down.setEnabled(rowIndex < layout.rows.size() - 1);
        down.setOnClickListener(v -> { Collections.swap(layout.rows, rowIndex, rowIndex + 1); render(); });
        rowView.addView(down);

        Button delete = createButton("✕");
        delete.setOnClickListener(v -> { layout.rows.remove(rowIndex); render(); });
        rowView.addView(delete);

        return rowView;
    }

    /** Edit an existing key. The key is found by identity, since it may be moved while editing. */
    private void editKey(@NonNull Layout layout, @NonNull Key key) {
        ExtraKeyEditDialog.show(this, key, false, true, new ExtraKeyEditDialog.Callbacks() {
            @Override
            public void onSave(@NonNull Key newKey) {
                int[] position = findKey(layout, key);
                if (position == null) return;
                // Update in place so that the identity of the key is kept
                key.type = newKey.type;
                key.value = newKey.value;
                key.display = newKey.display;
                key.popup = newKey.popup;
                render();
            }

            @Override
            public void onDelete() {
                int[] position = findKey(layout, key);
                if (position == null) return;
                layout.rows.get(position[0]).remove(position[1]);
                render();
            }

            @Override
            public void onMove(int dx, int dy) {
                moveKey(layout, key, dx, dy);
                render();
            }
        });
    }

    @Nullable
    private static int[] findKey(@NonNull Layout layout, @NonNull Key key) {
        for (int r = 0; r < layout.rows.size(); r++) {
            List<Key> row = layout.rows.get(r);
            for (int c = 0; c < row.size(); c++)
                if (row.get(c) == key) return new int[]{r, c};
        }
        return null;
    }

    private static void moveKey(@NonNull Layout layout, @NonNull Key key, int dx, int dy) {
        int[] position = findKey(layout, key);
        if (position == null) return;
        List<Key> row = layout.rows.get(position[0]);
        if (dx != 0) {
            int to = position[1] + dx;
            if (to < 0 || to >= row.size()) return;
            Collections.swap(row, position[1], to);
        } else if (dy != 0) {
            int toRow = position[0] + dy;
            if (toRow < 0 || toRow >= layout.rows.size()) return;
            row.remove(position[1]);
            List<Key> targetRow = layout.rows.get(toRow);
            targetRow.add(Math.min(position[1], targetRow.size()), key);
        }
    }

    private static void removeEmptyRows(@NonNull Layout layout) {
        for (int i = layout.rows.size() - 1; i >= 0; i--)
            if (layout.rows.get(i).isEmpty()) layout.rows.remove(i);
    }



    /** Validate and save the changes, asking for confirmation if there are invalid references. */
    private void save() {
        float height;
        try {
            height = Float.parseFloat(mToolbarHeight);
        } catch (NumberFormatException e) {
            height = -1;
        }
        if (height < TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MIN ||
            height > TermuxPropertyConstants.IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR_MAX) {
            showError(getString(R.string.extra_keys_editor_invalid_toolbar_height));
            return;
        }

        for (int i = 0; i < mLayouts.size(); i++) {
            if (mLayouts.get(i).getKeyCount() == 0) {
                showError(getString(R.string.extra_keys_editor_empty_layout, i + 1));
                return;
            }
        }

        Set<Integer> invalid = new TreeSet<>(ExtraKeysLayoutModel.getInvalidLayoutReferences(mLayouts));
        if (!invalid.isEmpty()) {
            StringBuilder references = new StringBuilder();
            for (int number : invalid) {
                if (references.length() > 0) references.append(", ");
                references.append(ExtraKeysLayoutModel.KEY_LAYOUT_PREFIX).append(number);
            }
            new AlertDialog.Builder(this)
                .setMessage(getString(R.string.extra_keys_editor_invalid_references, references))
                .setPositiveButton(R.string.extra_keys_editor_save_anyway, (d, w) -> writeToFile())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        writeToFile();
    }

    private void showError(@NonNull String message) {
        new AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void writeToFile() {
        try {
            List<String> lines = new ArrayList<>();
            if (mPropertiesFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(mPropertiesFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) lines.add(line);
                }
                backupPropertiesFile();
            }

            PropertiesFileEditor editor = new PropertiesFileEditor(lines);
            for (int number = 1; number <= TermuxPropertyConstants.MAX_EXTRA_KEYS_LAYOUTS; number++) {
                String key = TermuxPropertyConstants.getExtraKeysLayoutKey(number);
                if (number <= mLayouts.size()) {
                    Layout layout = mLayouts.get(number - 1).copy();
                    removeEmptyRows(layout);
                    editor.setProperty(key, ExtraKeysLayoutModel.serializeLayoutRows(layout));
                } else {
                    editor.setProperty(key, (String) null);
                }
            }

            // Only write the options if they were already set or have been changed from the defaults
            String defaultHeight = String.valueOf(TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR);
            if (mLoadedStyle != null || !mStyle.equals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE))
                editor.setProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, mStyle);
            if (mLoadedTextAllCaps != null || !mTextAllCaps)
                editor.setProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_TEXT_ALL_CAPS, String.valueOf(mTextAllCaps));
            if (mLoadedToolbarHeight != null || !mToolbarHeight.equals(defaultHeight))
                editor.setProperty(TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR, mToolbarHeight);

            File parent = mPropertiesFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs())
                throw new IOException("Failed to create directory " + parent);
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(mPropertiesFile), StandardCharsets.UTF_8)) {
                for (String line : editor.getLines()) writer.write(line + "\n");
            }
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save the extra keys to " + mPropertiesFile, e);
            showError(getString(R.string.extra_keys_editor_save_failed, e.getMessage()));
            return;
        }

        mLoadedStyle = mStyle;
        mLoadedTextAllCaps = String.valueOf(mTextAllCaps);
        mLoadedToolbarHeight = mToolbarHeight;
        mSavedState = getState();

        // Reload in TermuxActivity, now if it is visible, otherwise when it is started again
        TermuxActivity.requestReloadStylingOnStart();
        TermuxActivity.updateTermuxActivityStyling(this, false);

        Logger.showToast(this, getString(R.string.extra_keys_editor_saved, mPropertiesFile.getAbsolutePath()), true);
    }

    /** Backup the properties file to {@code termux.properties.bak} if a backup does not exist yet. */
    private void backupPropertiesFile() {
        File backup = new File(mPropertiesFile.getAbsolutePath() + ".bak");
        if (backup.exists()) return;
        try (InputStream in = new FileInputStream(mPropertiesFile); OutputStream out = new FileOutputStream(backup)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to backup " + mPropertiesFile + " to " + backup, e);
        }
    }

}
