package com.termux.shared.termux.theme;

import android.graphics.Typeface;

import androidx.annotation.NonNull;

/**
 * Callback interface for views/adapters/controllers that dynamically restyle themselves
 * when the active Termux color palette or Nerd Font changes.
 */
public interface TermuxAppearanceAware {
    void applyAppearance(@NonNull TermuxColorPalette palette, @NonNull Typeface typeface);
}
