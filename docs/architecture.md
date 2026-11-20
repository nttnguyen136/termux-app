# Architecture

Reference map for `nttnguyen136/termux-app` across its 4 Gradle modules (`:terminal-emulator`, `:terminal-view`, `:termux-shared`, `:app`).

## Module boundaries

| Module | Owns | Key entry points |
|---|---|---|
| `:terminal-emulator` | VT/ANSI emulation, `TerminalColors`, cursor styles | `TerminalSession`, `TerminalEmulator`, `TerminalColors` |
| `:terminal-view` | Canvas rendering, text selection ActionMode, `showContextMenu()` | `TerminalView`, `TerminalRenderer`, `TextSelectionCursorController` |
| `:termux-shared` | Theme/font engine, Extra Keys, shared activities/dialogs | `com.termux.shared.termux.theme.*`, `ExtraKeysView`, `ReportActivity`, `TextIOActivity`, `MessageDialogUtils`, `TextInputDialogUtils`, `com.termux.shared.activity.ActivityUtils` |
| `:app` | Main activity, offline bootstrap/shell setup, drawer/tabs/bottom sheet, Appearance Studio | `TermuxActivity`, `TermuxInstaller`, `com.termux.app.shell.*`, `SettingsActivity`, `AppearanceStudioFragment` |

Keep the theme/font engine in `:termux-shared` so both `:app` and shared activities (`ReportActivity`, `TextIOActivity`) consume one source of truth without circular dependencies.

## Build-time assets & network gotchas

- `app/nerd-fonts.gradle`: Downloads and SHA-256-verifies 3 Mono Nerd Fonts (`JetBrainsMono`, `MesloLGS`, `FiraCode`) into `${gradle.gradleUserHomeDir}/caches/termux-nerd-fonts/v3.5.1` and stages them at `assets/fonts/*.ttf`.
- `app/shell-bundle.gradle`: Bundles `libcap` + `zsh` `.deb`s (`apt-android-7` `aarch64`), Oh My Zsh, `zsh-autosuggestions`, `zsh-syntax-highlighting`, and `app/src/main/shell-bundle/termux-lean.zsh-theme` into `assets/shell-bundle/`.
- Requests to `packages-cf.termux.dev` return HTTP 403 unless an explicit `User-Agent` header is set.

## Shell boot & offline setup gotchas

- `$PREFIX/bin/login` executes `~/.termux/motd.sh` **before** loading `libtermux-exec-ld-preload.so`: any script in `motd.sh` MUST use the full shebang `#!/data/data/com.termux/files/usr/bin/sh` and POSIX `\033` escapes (never `/bin/sh` or `\x`).
- `AppShell.execute` reports `ExecutionState.SUCCESS` even when a child process exits non-zero; always inspect `exitCode == 0` explicitly and run `dpkg -i` non-interactively (`--force-confdef --force-confold`, `DEBIAN_FRONTEND=noninteractive`).
- `oh-my-zsh.sh` sources `tools/check_for_upgrade.sh`, and `zsh-syntax-highlighting.zsh` reads `.version` and `.revision-hash`; preserve those files when stripping archives.

## UI theming & navigation contracts

- `TermuxThemePresets.getBuiltInById(id)` returns built-in palettes (default: `catppuccin_mocha`); `TermuxFontManager.loadTypefaceForId(context, fontId, customFile)` resolves bundled/custom fonts.
- `TermuxActivity` tracks `TermuxAppearancePreferences.getAppearanceRevision(context)` in `onCreate()`, `onStart()`, and `onResume()`, updating all active sessions, Extra Keys (`TermuxAppearanceAware`), Left Drawer, Top Tab Bar, and system bars in-place without `Activity.recreate()`.
- Text selection `ActionMode` (`COPY`, `PASTE`, `MORE`) stays intact on `TerminalView`; tapping `MORE`, pressing `Ctrl+Alt+M`, or opening options menu routes to `TermuxActivity.showTerminalActionsBottomSheet()` (`TerminalActionsBottomSheet`).
- `SettingsActivity.createAppearanceStudioIntent(context)` opens `AppearanceStudioFragment` directly (from Left Drawer, Bottom Sheet, or Settings).

## Robolectric testing notes

- `TermuxActivity` tests must annotate `@Config(sdk = 28)` and drive lifecycle with `.create().start().resume()` rather than `.setup()` (which triggers `ShadowInstrumentation.bindService` NPE in `onServiceConnected`).
- Isolate filesystem tests (`ZshBundleInstaller`, `ShellConfigManager`, `WelcomeBannerManager`) inside JUnit `TemporaryFolder`—never touch `/data/data/com.termux/files`.
