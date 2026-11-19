package com.termux.app.shell;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Progress callback for first-run bootstrap extraction and offline shell setup.
 * Implementations MUST be thread-safe and dispatch UI updates to the main thread,
 * as {@link #onStageChanged(String, String, int)} is invoked from a background worker thread.
 */
public interface BootstrapProgressListener {
    void onStageChanged(@NonNull String title, @Nullable String detail, int progressPercent);
}
