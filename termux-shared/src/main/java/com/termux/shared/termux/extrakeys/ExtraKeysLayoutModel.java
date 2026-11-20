package com.termux.shared.termux.extrakeys;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An editable model of the extra keys layouts defined by the {@code extra-keys[-N]} properties.
 *
 * Unlike {@link ExtraKeysInfo}, values are kept as is (aliases are not replaced and display text is
 * not generated) so that a layout can be parsed, edited and serialized back without changes.
 *
 * {@code LAYOUT_N} keys always refer to the layout at position {@code N} (1-based) in the list of
 * layouts, and are remapped by {@link #moveLayout(List, int, int)} and {@link #removeLayout(List, int)}.
 */
public class ExtraKeysLayoutModel {

    /** The special key to switch to the next layout. */
    public static final String KEY_LAYOUT = "LAYOUT";
    /** The prefix for the special keys to switch to a specific layout. */
    public static final String KEY_LAYOUT_PREFIX = "LAYOUT_";

    /** The modifier keys that can be combined with another key in a {@link Key.Type#MACRO}. */
    public static final List<String> MODIFIER_KEYS = Arrays.asList("CTRL", "ALT", "SHIFT", "FN");

    /** A single extra key. */
    public static class Key {

        public enum Type {
            /** A single key or literal text defined by {@code key}. */
            KEY,
            /** A space separated sequence of keys defined by {@code macro}. */
            MACRO
        }

        @NonNull public Type type;
        @NonNull public String value;
        /** The optional custom display text. */
        @Nullable public String display;
        /** The optional popup key shown on swipe up. A popup itself can't have a popup. */
        @Nullable public Key popup;

        public Key(@NonNull Type type, @NonNull String value, @Nullable String display, @Nullable Key popup) {
            this.type = type;
            this.value = value;
            this.display = display;
            this.popup = popup;
        }

        public static Key single(@NonNull String value) {
            return new Key(Type.KEY, value, null, null);
        }

        @NonNull
        public Key copy() {
            return new Key(type, value, display, popup == null ? null : popup.copy());
        }

        /** Get the text to show for the key in an editor. */
        @NonNull
        public String getLabel() {
            if (display != null && !display.isEmpty()) return display;
            return value;
        }

        /**
         * Check if the key is a {@link Type#MACRO} that can be edited as a sequence of combo steps,
         * like {@code CTRL b $} for Ctrl+b followed by $. Check {@link #parseComboSteps(String)}.
         */
        public boolean isCombo() {
            if (type != Type.MACRO) return false;
            List<ComboStep> steps = parseComboSteps(value);
            if (steps == null) return false;
            for (ComboStep step : steps)
                if (!step.modifiers.isEmpty()) return true;
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return type == k.type && value.equals(k.value) &&
                (display == null ? k.display == null : display.equals(k.display)) &&
                (popup == null ? k.popup == null : popup.equals(k.popup));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    /** An extra keys layout, a list of rows of keys. */
    public static class Layout {
        @NonNull public final List<List<Key>> rows;

        public Layout(@NonNull List<List<Key>> rows) {
            this.rows = rows;
        }

        @NonNull
        public Layout copy() {
            List<List<Key>> rowsCopy = new ArrayList<>();
            for (List<Key> row : rows) {
                List<Key> rowCopy = new ArrayList<>();
                for (Key key : row) rowCopy.add(key.copy());
                rowsCopy.add(rowCopy);
            }
            return new Layout(rowsCopy);
        }

        /** Get the total number of keys in all rows. */
        public int getKeyCount() {
            int count = 0;
            for (List<Key> row : rows) count += row.size();
            return count;
        }
    }



    /** Split a macro value into its keys. */
    @NonNull
    public static String[] splitMacro(@NonNull String macro) {
        String trimmed = macro.trim();
        if (trimmed.isEmpty()) return new String[0];
        return trimmed.split(" +");
    }

    /**
     * A step of a combo sequence: zero or more {@link #MODIFIER_KEYS} held while pressing one key.
     * In a macro, modifiers apply to the next non-modifier key only.
     */
    public static class ComboStep {
        /** The modifiers in the order of {@link #MODIFIER_KEYS}. */
        @NonNull public final List<String> modifiers;
        @NonNull public final String key;

        public ComboStep(@NonNull List<String> modifiers, @NonNull String key) {
            this.modifiers = modifiers;
            this.key = key;
        }
    }

    /**
     * Parse a macro into combo steps, like {@code CTRL b $} into {@code [CTRL+b, $]}.
     *
     * @return Returns the steps, or {@code null} if the macro is empty or ends with a modifier.
     */
    @Nullable
    public static List<ComboStep> parseComboSteps(@NonNull String macro) {
        String[] tokens = splitMacro(macro);
        if (tokens.length == 0) return null;
        List<ComboStep> steps = new ArrayList<>();
        Set<String> modifiers = new HashSet<>();
        for (String token : tokens) {
            if (MODIFIER_KEYS.contains(token)) {
                modifiers.add(token);
            } else {
                List<String> ordered = new ArrayList<>();
                for (String modifier : MODIFIER_KEYS)
                    if (modifiers.contains(modifier)) ordered.add(modifier);
                steps.add(new ComboStep(ordered, token));
                modifiers.clear();
            }
        }
        if (!modifiers.isEmpty()) return null;
        return steps;
    }

    /** Serialize combo steps to a macro, like {@code [CTRL+b, $]} to {@code CTRL b $}. */
    @NonNull
    public static String serializeComboSteps(@NonNull List<ComboStep> steps) {
        StringBuilder sb = new StringBuilder();
        for (ComboStep step : steps) {
            for (String modifier : step.modifiers) sb.append(modifier).append(' ');
            sb.append(step.key).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Parse a layout from the value of an {@code extra-keys[-N]} property.
     *
     * @throws JSONException If the value is not a valid layout.
     */
    @NonNull
    public static Layout parseLayout(@NonNull String value) throws JSONException {
        JSONArray rowsArray = new JSONArray(value);
        List<List<Key>> rows = new ArrayList<>();
        for (int i = 0; i < rowsArray.length(); i++) {
            JSONArray rowArray = rowsArray.getJSONArray(i);
            List<Key> row = new ArrayList<>();
            for (int j = 0; j < rowArray.length(); j++)
                row.add(parseKey(rowArray.get(j), true));
            rows.add(row);
        }
        return new Layout(rows);
    }

    @NonNull
    private static Key parseKey(Object object, boolean allowPopup) throws JSONException {
        if (object instanceof JSONObject) {
            JSONObject config = (JSONObject) object;
            String key = config.has(ExtraKeyButton.KEY_KEY_NAME) ? config.getString(ExtraKeyButton.KEY_KEY_NAME) : null;
            String macro = config.has(ExtraKeyButton.KEY_MACRO) ? config.getString(ExtraKeyButton.KEY_MACRO) : null;
            if (key != null && macro != null)
                throw new JSONException("Both key and macro can't be set for the same key. key: \"" + key + "\", macro: \"" + macro + "\"");
            if (key == null && macro == null)
                throw new JSONException("All keys have to specify either key or macro");

            String display = config.has(ExtraKeyButton.KEY_DISPLAY_NAME) ? config.getString(ExtraKeyButton.KEY_DISPLAY_NAME) : null;
            Key popup = null;
            if (allowPopup && config.has(ExtraKeyButton.KEY_POPUP))
                popup = parseKey(config.get(ExtraKeyButton.KEY_POPUP), false);

            return new Key(key != null ? Key.Type.KEY : Key.Type.MACRO, key != null ? key : macro, display, popup);
        } else if (object instanceof String || object instanceof Number || object instanceof Boolean) {
            return Key.single(String.valueOf(object));
        } else {
            throw new JSONException("An key in the extra keys must either be a string or an object: " + object);
        }
    }

    /**
     * Serialize a layout to one string per row, like {@code [['ESC','TAB'],} and {@code ['CTRL']]},
     * which joined together form the value of an {@code extra-keys[-N]} property.
     */
    @NonNull
    public static List<String> serializeLayoutRows(@NonNull Layout layout) {
        List<String> parts = new ArrayList<>();
        int rowCount = layout.rows.size();
        if (rowCount == 0) {
            parts.add("[]");
            return parts;
        }
        for (int i = 0; i < rowCount; i++) {
            StringBuilder sb = new StringBuilder();
            if (i == 0) sb.append('[');
            sb.append('[');
            List<Key> row = layout.rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(serializeKey(row.get(j)));
            }
            sb.append(']');
            sb.append(i == rowCount - 1 ? "]" : ",");
            parts.add(sb.toString());
        }
        return parts;
    }

    /** Serialize a layout to the value of an {@code extra-keys[-N]} property. */
    @NonNull
    public static String serializeLayout(@NonNull Layout layout) {
        StringBuilder sb = new StringBuilder();
        for (String part : serializeLayoutRows(layout)) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(part);
        }
        return sb.toString();
    }

    @NonNull
    static String serializeKey(@NonNull Key key) {
        if (key.type == Key.Type.KEY && key.display == null && key.popup == null)
            return quote(key.value);

        StringBuilder sb = new StringBuilder("{");
        sb.append(key.type == Key.Type.KEY ? ExtraKeyButton.KEY_KEY_NAME : ExtraKeyButton.KEY_MACRO);
        sb.append(": ").append(quote(key.value));
        if (key.display != null)
            sb.append(", ").append(ExtraKeyButton.KEY_DISPLAY_NAME).append(": ").append(quote(key.display));
        if (key.popup != null)
            sb.append(", ").append(ExtraKeyButton.KEY_POPUP).append(": ").append(serializeKey(key.popup));
        sb.append('}');
        return sb.toString();
    }

    /** Quote a string in single quotes, escaping backslashes and single quotes. */
    @NonNull
    static String quote(@NonNull String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }



    /** Get the layout number {@code N} if {@code token} is {@code LAYOUT_N}, otherwise {@code -1}. */
    public static int getLayoutReference(@NonNull String token) {
        if (!token.startsWith(KEY_LAYOUT_PREFIX)) return -1;
        String number = token.substring(KEY_LAYOUT_PREFIX.length());
        if (number.isEmpty() || number.length() > 2) return -1;
        for (int i = 0; i < number.length(); i++)
            if (!Character.isDigit(number.charAt(i))) return -1;
        return Integer.parseInt(number);
    }

    /** A function that maps an old layout number to a new one, or to {@code -1} if the
     * reference should be replaced with {@link #KEY_LAYOUT}. */
    public interface LayoutReferenceMapper {
        int map(int number);
    }

    /**
     * Remap all {@code LAYOUT_N} references in all layouts, including in popups and macros.
     *
     * @return Returns the number of references that were changed.
     */
    public static int remapLayoutReferences(@NonNull List<Layout> layouts, @NonNull LayoutReferenceMapper mapper) {
        int changed = 0;
        for (Layout layout : layouts)
            for (List<Key> row : layout.rows)
                for (Key key : row) {
                    changed += remapLayoutReferences(key, mapper);
                    if (key.popup != null) changed += remapLayoutReferences(key.popup, mapper);
                }
        return changed;
    }

    private static int remapLayoutReferences(@NonNull Key key, @NonNull LayoutReferenceMapper mapper) {
        if (key.type == Key.Type.KEY) {
            String newValue = remapToken(key.value, mapper);
            if (newValue.equals(key.value)) return 0;
            key.value = newValue;
            return 1;
        }

        String[] tokens = splitMacro(key.value);
        int changed = 0;
        for (int i = 0; i < tokens.length; i++) {
            String newToken = remapToken(tokens[i], mapper);
            if (!newToken.equals(tokens[i])) {
                tokens[i] = newToken;
                changed++;
            }
        }
        if (changed > 0) {
            StringBuilder sb = new StringBuilder();
            for (String token : tokens) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(token);
            }
            key.value = sb.toString();
        }
        return changed;
    }

    @NonNull
    private static String remapToken(@NonNull String token, @NonNull LayoutReferenceMapper mapper) {
        int number = getLayoutReference(token);
        if (number < 0) return token;
        int newNumber = mapper.map(number);
        if (newNumber == number) return token;
        return newNumber < 1 ? KEY_LAYOUT : KEY_LAYOUT_PREFIX + newNumber;
    }

    /**
     * Move the layout at {@code from} to {@code to} (0-based) and remap the {@code LAYOUT_N}
     * references so that they still refer to the same layouts.
     */
    public static void moveLayout(@NonNull List<Layout> layouts, int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= layouts.size() || to >= layouts.size()) return;
        layouts.add(to, layouts.remove(from));
        final int fromNumber = from + 1;
        final int toNumber = to + 1;
        remapLayoutReferences(layouts, number -> {
            if (number == fromNumber) return toNumber;
            if (fromNumber < toNumber && number > fromNumber && number <= toNumber) return number - 1;
            if (fromNumber > toNumber && number >= toNumber && number < fromNumber) return number + 1;
            return number;
        });
    }

    /**
     * Remove the layout at {@code index} (0-based) and remap the {@code LAYOUT_N} references so
     * that they still refer to the same layouts. References to the removed layout are replaced
     * with {@link #KEY_LAYOUT}.
     *
     * @return Returns the number of references to the removed layout that were replaced.
     */
    public static int removeLayout(@NonNull List<Layout> layouts, int index) {
        if (index < 0 || index >= layouts.size()) return 0;
        layouts.remove(index);
        final int removedNumber = index + 1;
        final int[] replaced = {0};
        remapLayoutReferences(layouts, number -> {
            if (number == removedNumber) {
                replaced[0]++;
                return -1;
            }
            return number > removedNumber ? number - 1 : number;
        });
        return replaced[0];
    }

    /** Get the {@code LAYOUT_N} references in all layouts that do not refer to an existing layout. */
    @NonNull
    public static Set<Integer> getInvalidLayoutReferences(@NonNull List<Layout> layouts) {
        final Set<Integer> invalid = new HashSet<>();
        final int count = layouts.size();
        // Use the mapper only to visit the references, without changing them
        List<Layout> copies = new ArrayList<>();
        for (Layout layout : layouts) copies.add(layout.copy());
        remapLayoutReferences(copies, number -> {
            if (number < 1 || number > count) invalid.add(number);
            return number;
        });
        return invalid;
    }

}
