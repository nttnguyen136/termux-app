package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Result of validating an {@link SshProfile}, holding field-specific error messages
 * for inline UI feedback as well as aggregate validity helpers.
 */
public final class SshValidationResult {

    public enum Field {
        HOST,
        USERNAME,
        PORT,
        KEEP_ALIVE_INTERVAL,
        PRIVATE_KEY_PATH,
        PROXY_JUMP
    }

    private final Map<Field, String> fieldErrors;
    private final List<String> errors;

    public SshValidationResult(@NonNull Map<Field, String> fieldErrors) {
        EnumMap<Field, String> copy = new EnumMap<>(Field.class);
        copy.putAll(fieldErrors);
        this.fieldErrors = Collections.unmodifiableMap(copy);
        this.errors = Collections.unmodifiableList(new ArrayList<>(copy.values()));
    }

    public boolean isValid() {
        return fieldErrors.isEmpty();
    }

    @NonNull
    public Map<Field, String> getFieldErrors() {
        return fieldErrors;
    }

    @Nullable
    public String getFieldError(@NonNull Field field) {
        return fieldErrors.get(field);
    }

    @NonNull
    public List<String> getErrors() {
        return errors;
    }

    @Nullable
    public String getFirstErrorMessage() {
        return errors.isEmpty() ? null : errors.get(0);
    }

    @NonNull
    public String getJoinedErrorMessage() {
        if (errors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(errors.get(i));
        }
        return sb.toString();
    }
}
