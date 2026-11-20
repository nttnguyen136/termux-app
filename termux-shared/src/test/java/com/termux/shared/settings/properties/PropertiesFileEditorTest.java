package com.termux.shared.settings.properties;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class PropertiesFileEditorTest {

    private static Properties load(List<String> lines) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(String.join("\n", lines)));
        return properties;
    }

    @Test
    public void replacesMultiLineValueAndKeepsComments() throws IOException {
        PropertiesFileEditor editor = new PropertiesFileEditor(Arrays.asList(
            "# My config",
            "extra-keys = [['ESC'], \\",
            "              ['TAB']]",
            "# extra-keys = [['old']]",
            "bell-character=ignore"));

        editor.setProperty("extra-keys", Arrays.asList("[['A', 'B'],", "['C']]"));

        assertEquals(Arrays.asList(
            "# My config",
            "extra-keys = [['A', 'B'], \\",
            "    ['C']]",
            "# extra-keys = [['old']]",
            "bell-character=ignore"), editor.getLines());
        Properties properties = load(editor.getLines());
        assertEquals("[['A', 'B'], ['C']]", properties.getProperty("extra-keys"));
        assertEquals("ignore", properties.getProperty("bell-character"));
    }

    @Test
    public void appendsMissingKeyAndRemovesDuplicates() {
        PropertiesFileEditor editor = new PropertiesFileEditor(Arrays.asList(
            "extra-keys-style:arrows-only",
            "extra-keys-style   all"));
        editor.setProperty("extra-keys-style", "none");
        editor.setProperty("extra-keys-2", "[['X']]");
        assertEquals(Arrays.asList(
            "extra-keys-style = none",
            "",
            "extra-keys-2 = [['X']]"), editor.getLines());
    }

    @Test
    public void removesKeyWithContinuationLines() {
        PropertiesFileEditor editor = new PropertiesFileEditor(Arrays.asList(
            "extra-keys-3 = [['A'], \\",
            "  ['B']]",
            "other = 1"));
        editor.setProperty("extra-keys-3", (String) null);
        assertEquals(Collections.singletonList("other = 1"), editor.getLines());
    }

    @Test
    public void doesNotMatchKeyPrefixOrCommentContinuation() {
        PropertiesFileEditor editor = new PropertiesFileEditor(Arrays.asList(
            "# comment ending with backslash \\",
            "extra-keys-style = all",
            "extra-keys = [['A']]"));
        editor.setProperty("extra-keys", "[['B']]");
        assertEquals(Arrays.asList(
            "# comment ending with backslash \\",
            "extra-keys-style = all",
            "extra-keys = [['B']]"), editor.getLines());
    }

    @Test
    public void escapedValuesRoundTrip() throws IOException {
        String value = "[['\\\\', ' x', 'tab\there']]";
        PropertiesFileEditor editor = new PropertiesFileEditor(Collections.emptyList());
        editor.setProperty("extra-keys", Collections.singletonList(" " + value));
        assertEquals(" " + value, load(editor.getLines()).getProperty("extra-keys"));
    }

}
