package com.termux.shared.termux.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.extrakeys.ExtraKeysLayoutModel.Key;
import com.termux.shared.termux.extrakeys.ExtraKeysLayoutModel.Layout;

import org.json.JSONException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ExtraKeysLayoutModelTest {

    @Test
    public void parsesDefaultLayoutWithPopup() throws JSONException {
        Layout layout = ExtraKeysLayoutModel.parseLayout(
            "[['ESC','/',{key: '-', popup: '|'},'HOME'], ['TAB','CTRL']]");
        assertEquals(2, layout.rows.size());
        assertEquals(4, layout.rows.get(0).size());
        Key dash = layout.rows.get(0).get(2);
        assertEquals(Key.Type.KEY, dash.type);
        assertEquals("-", dash.value);
        assertEquals("|", dash.popup.value);
    }

    @Test
    public void parsesUnquotedKeysAndMacros() throws JSONException {
        Layout layout = ExtraKeysLayoutModel.parseLayout(
            "[[ESC, {macro: \"CTRL SHIFT t\", display: NewTab, popup: {macro: 'ALT g', display: A-g}}]]");
        Key macro = layout.rows.get(0).get(1);
        assertEquals(Key.Type.MACRO, macro.type);
        assertEquals("CTRL SHIFT t", macro.value);
        assertEquals("NewTab", macro.display);
        assertEquals(Key.Type.MACRO, macro.popup.type);
        assertEquals("A-g", macro.popup.display);
        assertTrue(macro.isCombo());
    }

    @Test(expected = JSONException.class)
    public void rejectsKeyWithBothKeyAndMacro() throws JSONException {
        ExtraKeysLayoutModel.parseLayout("[[{key: a, macro: b}]]");
    }

    @Test
    public void serializeRoundTripsSpecialCharacters() throws JSONException {
        Layout layout = new Layout(new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(Key.single("\\"), Key.single("'"), Key.single("\""))),
            new ArrayList<>(Collections.singletonList(
                new Key(Key.Type.MACRO, "CTRL c", "^C", Key.single("|")))))));

        String value = ExtraKeysLayoutModel.serializeLayout(layout);
        Layout parsed = ExtraKeysLayoutModel.parseLayout(value);
        assertEquals(layout.rows, parsed.rows);
    }

    @Test
    public void serializesOneStringPerRow() {
        Layout layout = new Layout(new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(Key.single("ESC"), Key.single("TAB"))),
            new ArrayList<>(Collections.singletonList(Key.single("CTRL"))))));
        assertEquals(Arrays.asList("[['ESC', 'TAB'],", "['CTRL']]"), ExtraKeysLayoutModel.serializeLayoutRows(layout));
    }

    @Test
    public void comboDetection() {
        assertTrue(new Key(Key.Type.MACRO, "CTRL c", null, null).isCombo());
        assertTrue(new Key(Key.Type.MACRO, "CTRL b $", null, null).isCombo());
        assertTrue(new Key(Key.Type.MACRO, "a CTRL b", null, null).isCombo());
        assertFalse(new Key(Key.Type.MACRO, "CTRL", null, null).isCombo());
        assertFalse(new Key(Key.Type.MACRO, "a b c", null, null).isCombo());
        assertFalse(new Key(Key.Type.MACRO, "CTRL a ALT", null, null).isCombo());
        assertFalse(Key.single("CTRL c").isCombo());
    }

    @Test
    public void comboStepsRoundTrip() {
        List<ExtraKeysLayoutModel.ComboStep> steps = ExtraKeysLayoutModel.parseComboSteps("SHIFT CTRL b  $");
        assertEquals(2, steps.size());
        // Modifiers are ordered like MODIFIER_KEYS
        assertEquals(Arrays.asList("CTRL", "SHIFT"), steps.get(0).modifiers);
        assertEquals("b", steps.get(0).key);
        assertTrue(steps.get(1).modifiers.isEmpty());
        assertEquals("$", steps.get(1).key);
        assertEquals("CTRL SHIFT b $", ExtraKeysLayoutModel.serializeComboSteps(steps));
        assertEquals(null, ExtraKeysLayoutModel.parseComboSteps("CTRL a ALT"));
    }

    private static List<Layout> layoutsWithReferences() throws JSONException {
        return new ArrayList<>(Arrays.asList(
            ExtraKeysLayoutModel.parseLayout("[['A', LAYOUT_2, {key: LAYOUT, popup: LAYOUT_3}]]"),
            ExtraKeysLayoutModel.parseLayout("[['B', {macro: 'CTRL c LAYOUT_1'}]]"),
            ExtraKeysLayoutModel.parseLayout("[['C', LAYOUT_1]]")));
    }

    @Test
    public void moveLayoutKeepsReferencesPointingToSameLayout() throws JSONException {
        List<Layout> layouts = layoutsWithReferences();
        // A B C -> C A B
        ExtraKeysLayoutModel.moveLayout(layouts, 2, 0);
        assertEquals("C", layouts.get(0).rows.get(0).get(0).value);
        assertEquals("LAYOUT_2", layouts.get(0).rows.get(0).get(1).value); // A
        assertEquals("LAYOUT_3", layouts.get(1).rows.get(0).get(1).value); // B
        assertEquals("LAYOUT_1", layouts.get(1).rows.get(0).get(2).popup.value); // C
        assertEquals("CTRL c LAYOUT_2", layouts.get(2).rows.get(0).get(1).value); // A
    }

    @Test
    public void removeLayoutReplacesReferencesToRemovedLayout() throws JSONException {
        List<Layout> layouts = layoutsWithReferences();
        int replaced = ExtraKeysLayoutModel.removeLayout(layouts, 1); // remove B
        assertEquals(1, replaced);
        assertEquals(2, layouts.size());
        assertEquals("LAYOUT", layouts.get(0).rows.get(0).get(1).value);
        assertEquals("LAYOUT_2", layouts.get(0).rows.get(0).get(2).popup.value); // C
        assertEquals("LAYOUT_1", layouts.get(1).rows.get(0).get(1).value);
    }

    @Test
    public void findsInvalidReferences() throws JSONException {
        List<Layout> layouts = new ArrayList<>(Collections.singletonList(
            ExtraKeysLayoutModel.parseLayout("[[LAYOUT_1, LAYOUT_4, {macro: 'LAYOUT_9'}]]")));
        Set<Integer> invalid = ExtraKeysLayoutModel.getInvalidLayoutReferences(layouts);
        assertEquals(2, invalid.size());
        assertTrue(invalid.contains(4));
        assertTrue(invalid.contains(9));
        // Check that the layouts were not changed
        assertEquals("LAYOUT_4", layouts.get(0).rows.get(0).get(1).value);
    }

    @Test
    public void layoutReferenceParsing() {
        assertEquals(3, ExtraKeysLayoutModel.getLayoutReference("LAYOUT_3"));
        assertEquals(-1, ExtraKeysLayoutModel.getLayoutReference("LAYOUT"));
        assertEquals(-1, ExtraKeysLayoutModel.getLayoutReference("LAYOUT_x"));
        assertEquals(-1, ExtraKeysLayoutModel.getLayoutReference("ESC"));
    }

}
