package com.termux.shared.settings.properties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Edit the lines of a {@link java.util.Properties} file in place, while keeping comments, blank
 * lines and other properties as is.
 *
 * Logical lines follow the {@link java.util.Properties#load(java.io.Reader)} format: a key is
 * separated from its value by {@code =}, {@code :} or whitespace, a line ending with an odd number
 * of backslashes continues on the next line, and lines starting with {@code #} or {@code !} are
 * comments.
 */
public class PropertiesFileEditor {

    /** The indent used for the continuation lines of multi-line values written by the editor. */
    public static final String CONTINUATION_INDENT = "    ";

    private final List<String> mLines;

    public PropertiesFileEditor(@NonNull List<String> lines) {
        mLines = new ArrayList<>(lines);
    }

    /** Get the current lines. */
    @NonNull
    public List<String> getLines() {
        return Collections.unmodifiableList(mLines);
    }

    /**
     * Set the value of {@code key}. The value may be split into multiple parts which are written on
     * separate continuation lines, like a row of extra keys per line. The first existing occurrence
     * of the key is replaced and others are removed. If the key does not exist, it is appended.
     *
     * @param key The property key.
     * @param valueParts The unescaped value parts. They are joined with a single space when read
     *                   back, since leading whitespace of continuation lines is ignored. If
     *                   {@code null} or empty, the key is removed.
     */
    public void setProperty(@NonNull String key, @Nullable List<String> valueParts) {
        List<String> newLines = null;
        if (valueParts != null && !valueParts.isEmpty()) {
            newLines = new ArrayList<>();
            for (int i = 0; i < valueParts.size(); i++) {
                String part = escapeValue(valueParts.get(i), i == 0);
                boolean last = i == valueParts.size() - 1;
                String prefix = i == 0 ? key + " = " : CONTINUATION_INDENT;
                newLines.add(prefix + part + (last ? "" : " \\"));
            }
        }

        boolean replaced = false;
        int i = 0;
        while (i < mLines.size()) {
            int end = getLogicalLineEnd(i);
            String lineKey = getKey(mLines.get(i));
            if (key.equals(lineKey)) {
                for (int j = end; j >= i; j--) mLines.remove(j);
                if (!replaced && newLines != null) {
                    mLines.addAll(i, newLines);
                    i += newLines.size();
                }
                replaced = true;
            } else {
                i = end + 1;
            }
        }

        if (!replaced && newLines != null) {
            if (!mLines.isEmpty() && !mLines.get(mLines.size() - 1).trim().isEmpty())
                mLines.add("");
            mLines.addAll(newLines);
        }
    }

    /** Set the single line value of {@code key}, or remove the key if {@code value} is {@code null}. */
    public void setProperty(@NonNull String key, @Nullable String value) {
        setProperty(key, value == null ? null : Collections.singletonList(value));
    }

    /** Get the index of the last physical line of the logical line starting at {@code start}. */
    private int getLogicalLineEnd(int start) {
        String line = mLines.get(start);
        if (isCommentOrBlank(line)) return start;
        int i = start;
        while (i < mLines.size() - 1 && endsWithContinuation(mLines.get(i))) i++;
        return i;
    }

    static boolean isCommentOrBlank(@NonNull String line) {
        String trimmed = trimLeading(line);
        return trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!';
    }

    static boolean endsWithContinuation(@NonNull String line) {
        int count = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) count++;
        return count % 2 == 1;
    }

    /** Get the unescaped key of a physical line, or {@code null} if it is a comment or blank. */
    @Nullable
    static String getKey(@NonNull String line) {
        if (isCommentOrBlank(line)) return null;
        String trimmed = trimLeading(line);
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\\' && i + 1 < trimmed.length()) {
                key.append(trimmed.charAt(++i));
            } else if (c == '=' || c == ':' || c == ' ' || c == '\t' || c == '\f') {
                break;
            } else {
                key.append(c);
            }
        }
        return key.toString();
    }

    @NonNull
    private static String trimLeading(@NonNull String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t' || line.charAt(i) == '\f')) i++;
        return line.substring(i);
    }

    /**
     * Escape a value so that {@link java.util.Properties#load(java.io.Reader)} reads it back as is.
     *
     * @param value The value to escape.
     * @param escapeLeadingSpace If leading spaces should be escaped. Leading whitespace of
     *                           continuation lines is always ignored, so it can't be preserved.
     */
    @NonNull
    static String escapeValue(@NonNull String value, boolean escapeLeadingSpace) {
        StringBuilder sb = new StringBuilder();
        boolean leading = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\f': sb.append("\\f"); break;
                case ' ':
                    if (leading && escapeLeadingSpace) sb.append("\\ ");
                    else sb.append(' ');
                    break;
                default: sb.append(c);
            }
            if (c != ' ') leading = false;
        }
        return sb.toString();
    }

}
