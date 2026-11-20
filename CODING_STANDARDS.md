# Coding Standards

Review guidelines for changes in `nttnguyen136/termux-app`. Mechanical checks (banned legacy dialog imports, legacy `@color/red_400` references, unused imports) belong in `scripts/check-ui-guardrails.sh` and CI, not here.

## Material 3 UI & Live Reload

- Preserve rounded corner drawables (`GradientDrawable`) when updating view colors dynamically: use `setBackgroundTintList` or mutate the existing `GradientDrawable` instead of `View.setBackgroundColor()`, which replaces the shape drawable with a flat rectangle.
- Synchronize `NightMode` with `TermuxColorPalette.isLight` without calling `AppCompatDelegate.setDefaultNightMode`, so live theme switches apply in-place without recreating running terminal sessions.
- Keep all user-facing strings English-only across XML resources and Java code.
- Ensure dark and light palettes (especially `Catppuccin Latte`) maintain legible contrast between surface containers, text, keycaps, and active/locked accent states.

## Upgrade Safety & Offline Guarantee

- Never automatically overwrite pre-existing `~/.termux/font.ttf`, `~/.termux/colors.properties`, `~/.zshrc`, or user-managed sections of `~/.bashrc`. Destructive shell resets require explicit user confirmation via a Material 3 dialog.
- When `TermuxAppearancePreferences` has no explicit user selection, auto-detect non-empty `~/.termux/font.ttf` or `~/.termux/colors.properties` as active Custom mode instead of replacing them with fresh-install defaults.
- Keep first-run Zsh, Oh My Zsh, plugins, and Nerd Font setup 100% offline using assets packaged at build time.

## Testing & CI Discipline

- When wiring a previously stubbed navigation or UI seam (e.g., `SettingsActivity.createInitialFragment`), run the full multi-module `./gradlew test` suite—not only the new feature's test class—so seam assertions in earlier tests stay accurate.
- Keep unit tests deterministic and host-safe: operate on `TemporaryFolder` paths and clean up modified `SharedPreferences` / `TerminalColors.COLOR_SCHEME` / `NightMode` state between tests.
