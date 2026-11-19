package com.termux.app.shell;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.os.Build;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.result.ResultData;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Installs the build-time bundled offline Zsh {@code .deb} packages, Oh My Zsh, plugins
 * ({@code zsh-autosuggestions}, {@code zsh-syntax-highlighting}), minimal Nerd Font theme
 * ({@code termux-lean.zsh-theme}), default {@code ~/.zshrc}, marker-delimited Bash fallback prompt,
 * {@code ~/.termux/shell -> $PREFIX/bin/zsh} symlink, and compact 2-line welcome banner during
 * first-run bootstrap setup.
 */
public final class ZshBundleInstaller {

    private static final String LOG_TAG = "ZshBundleInstaller";

    public static final String ASSET_BUNDLE_ROOT = "shell-bundle";
    public static final String ASSET_MANIFEST_PATH = ASSET_BUNDLE_ROOT + "/manifest.properties";
    public static final String ASSET_OMZ_ARCHIVE_PATH = ASSET_BUNDLE_ROOT + "/omz-bundle.tar.gz";

    /**
     * Seam for executing {@code $PREFIX/bin/dpkg -i} synchronously (allowing unit tests to inspect
     * exact command arguments, ordering, and error handling without spawning a real Android process).
     */
    public interface DebCommandRunner {
        boolean runDpkgInstall(
                @NonNull Context context,
                @NonNull File prefixDir,
                @NonNull File homeDir,
                @NonNull File dpkgBinary,
                @NonNull List<String> arguments);
    }

    private static final DebCommandRunner DEFAULT_DEB_COMMAND_RUNNER = new DebCommandRunner() {
        @Override
        public boolean runDpkgInstall(
                @NonNull Context context,
                @NonNull File prefixDir,
                @NonNull File homeDir,
                @NonNull File dpkgBinary,
                @NonNull List<String> arguments) {
            ExecutionCommand executionCommand = new ExecutionCommand(
                -1,
                dpkgBinary.getAbsolutePath(),
                arguments.toArray(new String[0]),
                null,
                homeDir.getAbsolutePath(),
                ExecutionCommand.Runner.APP_SHELL.getName(),
                false
            );
            executionCommand.commandLabel = "Offline Zsh Bundle dpkg Install";
            executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF;

            HashMap<String, String> additionalEnvironment = new HashMap<>();
            additionalEnvironment.put("DEBIAN_FRONTEND", "noninteractive");
            additionalEnvironment.put(TermuxShellEnvironment.ENV_PREFIX, prefixDir.getAbsolutePath());
            additionalEnvironment.put(TermuxShellEnvironment.ENV_HOME, homeDir.getAbsolutePath());
            additionalEnvironment.put(TermuxShellEnvironment.ENV_TMPDIR, new File(prefixDir, "tmp").getAbsolutePath());
            if (isAptAndroid7Variant()) {
                additionalEnvironment.put(TermuxShellEnvironment.ENV_PATH, new File(prefixDir, "bin").getAbsolutePath());
            }

            AppShell appShell = AppShell.execute(
                context,
                executionCommand,
                null,
                new TermuxShellEnvironment(),
                additionalEnvironment,
                true
            );

            Integer exitCode = executionCommand.resultData.exitCode;
            boolean succeeded = appShell != null
                && executionCommand.isSuccessful()
                && exitCode != null
                && exitCode == 0;

            if (!succeeded) {
                String stdout = executionCommand.resultData.stdout.toString();
                String stderr = executionCommand.resultData.stderr.toString();
                String internalErrors = ResultData.getErrorsListMinimalString(executionCommand.resultData);
                Logger.logErrorExtended(LOG_TAG,
                    "Offline dpkg install failed (exitCode=" + exitCode + ")\n"
                    + "Internal errors: " + internalErrors + "\n"
                    + "Stdout:\n" + stdout + "\n"
                    + "Stderr:\n" + stderr);
            }
            return succeeded;
        }
    };

    private ZshBundleInstaller() {
    }

    public static void installIfNeeded(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            @Nullable BootstrapProgressListener listener) {
        installIfNeeded(context, prefixDir, homeDir, listener, DEFAULT_DEB_COMMAND_RUNNER);
    }

    public static void installIfNeeded(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            @Nullable BootstrapProgressListener listener,
            @NonNull DebCommandRunner debCommandRunner) {
        try {
            // Ensure required directories exist with 0700 permissions before any dpkg or dotfile operations
            ShellFsUtils.ensureDir0700(new File(prefixDir, "tmp"));
            ShellFsUtils.ensureDir0700(homeDir);
            File termuxDir = new File(homeDir, ".termux");
            ShellFsUtils.ensureDir0700(termuxDir);

            Properties manifest = loadBundleManifest(context.getAssets());
            boolean bundleEnabled = manifest != null && "true".equalsIgnoreCase(manifest.getProperty("enabled", "false"));
            String bundleVersion = manifest != null ? manifest.getProperty("bundleVersion", "1") : "1";

            // Stage 1: Offline Zsh & libcap .deb installation via $PREFIX/bin/dpkg -i
            notifyStage(
                listener,
                context,
                R.string.shell_setup_stage_installing_zsh_title,
                "Installing Zsh",
                R.string.shell_setup_stage_installing_zsh_detail,
                "Installing offline Zsh packages…",
                65
            );
            if (bundleEnabled && isAptAndroid7Variant()) {
                installBundledZshDebsIfNeeded(context, prefixDir, homeDir, manifest, debCommandRunner);
            } else {
                Logger.logInfo(LOG_TAG, "Offline .deb bundle disabled or not apt-android-7; skipping dpkg step.");
            }

            // Stage 2: Extract Oh My Zsh, plugins, and custom minimal Nerd Font theme
            notifyStage(
                listener,
                context,
                R.string.shell_setup_stage_extracting_omz_title,
                "Setting up Oh My Zsh",
                R.string.shell_setup_stage_extracting_omz_detail,
                "Unpacking Oh My Zsh, plugins, and theme…",
                80
            );
            extractOmzBundleIfNeeded(context, homeDir, manifest, false);

            // Stage 3: Configure default dotfiles (~/.zshrc, ~/.bashrc), ~/.termux/shell symlink, and MOTD
            notifyStage(
                listener,
                context,
                R.string.shell_setup_stage_configuring_title,
                "Configuring shell",
                R.string.shell_setup_stage_configuring_detail,
                "Applying default shell, prompt, and welcome banner…",
                92
            );
            configureInitialDotfilesAndShell(context, prefixDir, homeDir);

            if (bundleEnabled) {
                TermuxShellPreferences.setInstalledShellBundleVersion(context, bundleVersion);
            }
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(
                LOG_TAG,
                "Unexpected error during offline shell bundle setup; continuing with fallback shell.",
                t
            );
        }
    }

    /**
     * Used by {@link ShellConfigManager#applyDefaults} and {@link ShellConfigManager#resetToDefaults}
     * to ensure the bundled Oh My Zsh archive and offline Zsh packages are extracted/installed.
     */
    static void ensureBundledAssetsExtractedIfNeeded(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            boolean forceReExtractOmz) {
        try {
            ShellFsUtils.ensureDir0700(new File(prefixDir, "tmp"));
            ShellFsUtils.ensureDir0700(homeDir);
            ShellFsUtils.ensureDir0700(new File(homeDir, ".termux"));

            Properties manifest = loadBundleManifest(context.getAssets());
            boolean bundleEnabled = manifest != null && "true".equalsIgnoreCase(manifest.getProperty("enabled", "false"));
            if (bundleEnabled && isAptAndroid7Variant()) {
                installBundledZshDebsIfNeeded(context, prefixDir, homeDir, manifest, DEFAULT_DEB_COMMAND_RUNNER);
            }
            extractOmzBundleIfNeeded(context, homeDir, manifest, forceReExtractOmz);
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to ensure bundled shell assets", t);
        }
    }

    static boolean installBundledZshDebsIfNeeded(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            @Nullable Properties manifest,
            @NonNull DebCommandRunner debCommandRunner) {
        File zshBinary = new File(prefixDir, "bin/zsh");
        if (zshBinary.isFile() && zshBinary.canExecute()) {
            return true;
        }

        File dpkgBinary = new File(prefixDir, "bin/dpkg");
        if (!dpkgBinary.isFile() || !dpkgBinary.canExecute()) {
            Logger.logWarn(LOG_TAG, "Cannot run offline Zsh dpkg install: " + dpkgBinary.getAbsolutePath() + " is missing or not executable.");
            return false;
        }

        String arch = detectTermuxArch(context, prefixDir);
        List<String> debNames = resolveBundledDebNamesForArch(context.getAssets(), manifest, arch);
        if (debNames.isEmpty()) {
            Logger.logInfo(LOG_TAG, "No bundled offline .deb packages found for architecture '" + arch + "'.");
            return false;
        }

        File tmpDir = new File(prefixDir, "tmp");
        ShellFsUtils.ensureDir0700(tmpDir);
        File stagingDebDir = new File(tmpDir, "shell-bundle");
        ShellFsUtils.deleteRecursively(stagingDebDir);
        if (!ShellFsUtils.ensureDir0700(stagingDebDir)) {
            Logger.logWarn(LOG_TAG, "Failed to create temporary deb directory: " + stagingDebDir.getAbsolutePath());
            return false;
        }

        try {
            AssetManager assets = context.getAssets();
            List<File> stagedDebFiles = new ArrayList<>(debNames.size());
            for (String debName : debNames) {
                String assetPath = ASSET_BUNDLE_ROOT + "/debs/" + arch + "/" + debName;
                File destFile = new File(stagingDebDir, debName);
                try (InputStream in = assets.open(assetPath);
                     FileOutputStream out = new FileOutputStream(destFile, false)) {
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                    }
                } catch (IOException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to stage bundled .deb asset: " + assetPath, e);
                    return false;
                }
                stagedDebFiles.add(destFile);
            }

            List<String> dpkgArgs = new ArrayList<>(3 + stagedDebFiles.size());
            dpkgArgs.add("-i");
            dpkgArgs.add("--force-confdef");
            dpkgArgs.add("--force-confold");
            for (File debFile : stagedDebFiles) {
                dpkgArgs.add(debFile.getAbsolutePath());
            }

            boolean ok = debCommandRunner.runDpkgInstall(context, prefixDir, homeDir, dpkgBinary, dpkgArgs);
            if (ok && zshBinary.isFile()) {
                ShellFsUtils.chmod0700(zshBinary);
            }
            return ok;
        } finally {
            ShellFsUtils.deleteRecursively(stagingDebDir);
        }
    }

    static void extractOmzBundleIfNeeded(
            @NonNull Context context,
            @NonNull File homeDir,
            @Nullable Properties manifest,
            boolean forceOverwrite) {
        File omzDir = new File(homeDir, ".oh-my-zsh");
        File omzMainScript = new File(omzDir, "oh-my-zsh.sh");
        File themeFile = new File(omzDir, "custom/themes/termux-lean.zsh-theme");
        File autosuggestPlugin = new File(omzDir, "custom/plugins/zsh-autosuggestions/zsh-autosuggestions.plugin.zsh");
        File syntaxHighlightPlugin = new File(omzDir, "custom/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.plugin.zsh");

        boolean completeInstallPresent = omzMainScript.isFile()
            && themeFile.isFile()
            && autosuggestPlugin.isFile()
            && syntaxHighlightPlugin.isFile();

        if (completeInstallPresent && !forceOverwrite) {
            return;
        }

        String archiveAssetPath = manifest != null
            ? manifest.getProperty("omzArchive", ASSET_OMZ_ARCHIVE_PATH)
            : ASSET_OMZ_ARCHIVE_PATH;

        AssetManager assets = context.getAssets();
        try (InputStream archiveStream = openOmzArchiveAssetStream(assets, archiveAssetPath)) {
            TarGzExtractor.extract(archiveStream, omzDir);
        } catch (IOException e) {
            Logger.logInfo(LOG_TAG, "omz-bundle.tar.gz asset not available or failed to extract (" + e.getMessage() + "); ensuring theme file fallback.");
        }

        // Always ensure termux-lean.zsh-theme is present if .oh-my-zsh exists or forceOverwrite is requested
        if ((omzDir.isDirectory() || forceOverwrite) && (!themeFile.isFile() || forceOverwrite)) {
            try {
                ShellFsUtils.writeUtf8(themeFile, ShellConfigManager.DEFAULT_TERMUX_LEAN_THEME_CONTENT);
            } catch (IOException ignored) {
            }
        }
    }

    @NonNull
    public static InputStream openOmzArchiveAssetStream(@NonNull AssetManager assets) throws IOException {
        return openOmzArchiveAssetStream(assets, ASSET_OMZ_ARCHIVE_PATH);
    }

    @NonNull
    static InputStream openOmzArchiveAssetStream(
            @NonNull AssetManager assets,
            @NonNull String preferredPath) throws IOException {
        try {
            return assets.open(preferredPath);
        } catch (IOException firstErr) {
            if (preferredPath.endsWith(".gz")) {
                String strippedGzPath = preferredPath.substring(0, preferredPath.length() - 3);
                try {
                    return assets.open(strippedGzPath);
                } catch (IOException ignored) {
                }
            }
            throw firstErr;
        }
    }

    private static void configureInitialDotfilesAndShell(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir) {
        // 1. Write default ~/.zshrc ONLY if it does not already exist (never overwrite user ~/.zshrc)
        File zshrcFile = new File(homeDir, ".zshrc");
        if (!zshrcFile.exists()) {
            try {
                ShellFsUtils.writeUtf8(zshrcFile, ShellConfigManager.DEFAULT_ZSHRC_CONTENT);
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write default ~/.zshrc", e);
            }
        }

        // 2. Configure ~/.bashrc with delimited fallback prompt block safely:
        //    If ~/.bashrc does not exist or is empty, create it with the managed block;
        //    if it already has our marker block, update the block in-place;
        //    if a user already has an unmarked custom ~/.bashrc, preserve it untouched on automatic first run.
        File bashrcFile = new File(homeDir, ".bashrc");
        if (!bashrcFile.exists() || bashrcFile.length() == 0 || ShellConfigManager.hasBashPromptBlock(bashrcFile)) {
            ShellConfigManager.insertOrUpdateBashPromptBlock(bashrcFile);
        }

        // 3. Symlink ~/.termux/shell -> $PREFIX/bin/zsh ONLY if ~/.termux/shell does not exist
        //    AND $PREFIX/bin/zsh exists and is executable (never create a dangling symlink!)
        File termuxDir = new File(homeDir, ".termux");
        ShellFsUtils.ensureDir0700(termuxDir);
        File shellLink = new File(termuxDir, "shell");
        File zshBinary = new File(prefixDir, "bin/zsh");
        if (!ShellFsUtils.existsOrIsSymlink(shellLink) && zshBinary.isFile() && zshBinary.canExecute()) {
            ShellFsUtils.createSymlink(zshBinary.getAbsolutePath(), shellLink, false);
        }

        // 4. Write ~/.termux/motd.sh (0700) if absent, respecting any pre-existing ~/.hushlogin
        WelcomeBannerManager.installDefaultBannerIfMissing(context, homeDir);
    }

    /**
     * Detects the authoritative Termux {@code $PREFIX} package architecture:
     * <ol>
     *   <li>Inspects {@code $PREFIX/var/lib/dpkg/status} for the first non-{@code all} {@code Architecture:} line.</li>
     *   <li>Falls back to {@code context.getApplicationInfo().nativeLibraryDir}.</li>
     *   <li>Falls back to {@link Build#SUPPORTED_ABIS}.</li>
     *   <li>Falls back to {@code Os.uname().machine}.</li>
     * </ol>
     */
    @NonNull
    public static String detectTermuxArch(@Nullable Context context, @NonNull File prefixDir) {
        // 1. Authoritative: $PREFIX/var/lib/dpkg/status
        File dpkgStatus = new File(prefixDir, "var/lib/dpkg/status");
        if (dpkgStatus.isFile()) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(dpkgStatus), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Architecture:")) {
                        String rawArch = line.substring("Architecture:".length()).trim().toLowerCase(Locale.ROOT);
                        if (!rawArch.isEmpty() && !"all".equals(rawArch)) {
                            return normalizeTermuxArch(rawArch);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // 2. Fallback: ApplicationInfo.nativeLibraryDir
        if (context != null) {
            try {
                ApplicationInfo appInfo = context.getApplicationInfo();
                if (appInfo != null && appInfo.nativeLibraryDir != null) {
                    String libDirName = new File(appInfo.nativeLibraryDir).getName().toLowerCase(Locale.ROOT);
                    if ("arm64".equals(libDirName) || "arm64-v8a".equals(libDirName)) {
                        return "aarch64";
                    } else if ("arm".equals(libDirName) || "armeabi-v7a".equals(libDirName)) {
                        return "arm";
                    } else if ("x86_64".equals(libDirName)) {
                        return "x86_64";
                    } else if ("x86".equals(libDirName) || "i686".equals(libDirName)) {
                        return "i686";
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Fallback: Build.SUPPORTED_ABIS
        try {
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (abi != null && !abi.isEmpty()) {
                        return normalizeTermuxArch(abi);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 4. Fallback: Os.uname().machine
        try {
            String machine = Os.uname().machine;
            if (machine != null && !machine.isEmpty()) {
                return normalizeTermuxArch(machine);
            }
        } catch (Throwable ignored) {
        }

        return "aarch64";
    }

    @NonNull
    static String normalizeTermuxArch(@NonNull String raw) {
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if ("aarch64".equals(lower) || "arm64-v8a".equals(lower) || "arm64".equals(lower)) {
            return "aarch64";
        }
        if ("arm".equals(lower) || "armeabi-v7a".equals(lower) || "armeabi".equals(lower) || lower.startsWith("armv")) {
            return "arm";
        }
        if ("x86_64".equals(lower) || "amd64".equals(lower)) {
            return "x86_64";
        }
        if ("i686".equals(lower) || "x86".equals(lower) || "i386".equals(lower)) {
            return "i686";
        }
        return lower;
    }

    @NonNull
    static List<String> resolveBundledDebNamesForArch(
            @Nullable AssetManager assets,
            @Nullable Properties manifest,
            @NonNull String arch) {
        List<String> names = new ArrayList<>();
        if (manifest != null) {
            String csv = manifest.getProperty("debs." + arch);
            if (csv != null && !csv.trim().isEmpty()) {
                for (String part : csv.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        names.add(trimmed);
                    }
                }
            }
        }
        if (names.isEmpty() && assets != null) {
            String manifestTxtPath = ASSET_BUNDLE_ROOT + "/debs/" + arch + "/manifest.txt";
            try (InputStream in = assets.open(manifestTxtPath)) {
                String text = ShellFsUtils.readUtf8(in);
                for (String line : text.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        names.add(trimmed);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        if (names.isEmpty() && "aarch64".equals(arch)) {
            names = Arrays.asList("libcap.deb", "zsh.deb");
        }
        if (assets == null) {
            return names;
        }

        // Verify each deb asset actually exists in AssetManager
        List<String> verified = new ArrayList<>(names.size());
        for (String debName : names) {
            String assetPath = ASSET_BUNDLE_ROOT + "/debs/" + arch + "/" + debName;
            try (InputStream in = assets.open(assetPath)) {
                verified.add(debName);
            } catch (IOException ignored) {
                return new ArrayList<>();
            }
        }
        return verified;
    }

    @Nullable
    static Properties loadBundleManifest(@Nullable AssetManager assets) {
        if (assets == null) {
            return null;
        }
        try (InputStream in = assets.open(ASSET_MANIFEST_PATH)) {
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isAptAndroid7Variant() {
        if (TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT != null) {
            return TermuxBootstrap.isAppPackageVariantAPTAndroid7();
        }
        return "apt-android-7".equals(BuildConfig.TERMUX_PACKAGE_VARIANT);
    }

    private static void notifyStage(
            @Nullable BootstrapProgressListener listener,
            @NonNull Context context,
            int titleResId,
            @NonNull String fallbackTitle,
            int detailResId,
            @NonNull String fallbackDetail,
            int progressPercent) {
        if (listener == null) {
            return;
        }
        String title = fallbackTitle;
        String detail = fallbackDetail;
        try {
            title = context.getString(titleResId);
            detail = context.getString(detailResId);
        } catch (Throwable ignored) {
        }
        listener.onStageChanged(title, detail, progressPercent);
    }
}
