package com.termux.app.shell;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;

/**
 * Manages the compact 1–2 line Nerd Font welcome banner ({@code ~/.termux/motd.sh}),
 * replacing Termux's verbose upstream MOTD and synchronizing toggle state between
 * {@link TermuxShellPreferences} and {@code ~/.hushlogin}.
 */
public final class WelcomeBannerManager {

    private static final String LOG_TAG = "WelcomeBannerManager";

    public static final String HUSHLOGIN_FILENAME = ".hushlogin";
    public static final String TERMUX_CONFIG_DIRNAME = ".termux";
    public static final String MOTD_SCRIPT_FILENAME = "motd.sh";

    /**
     * Compact 2-line Nerd Font welcome banner script.
     * Uses {@code #!/data/data/com.termux/files/usr/bin/sh} because {@code $PREFIX/bin/login}
     * executes {@code ~/.termux/motd.sh} before loading {@code libtermux-exec-ld-preload.so}.
     */
    public static final String DEFAULT_WELCOME_BANNER_SCRIPT =
        "#!/data/data/com.termux/files/usr/bin/sh\n" +
        "# Termux Modern Welcome Banner (managed by Termux; toggle in Appearance & Shell)\n" +
        "printf '\\033[1;36m\uf120 Termux\\033[0m  \\033[34m\uf0e7 Ready\\033[0m\\n'\n" +
        "printf '\\033[2mpkg install <pkg> \u00b7 pkg upgrade\\033[0m\\n'\n";

    private WelcomeBannerManager() {
    }

    @NonNull
    public static File getHushLoginFile(@NonNull File homeDir) {
        return new File(homeDir, HUSHLOGIN_FILENAME);
    }

    @NonNull
    public static File getMotdScriptFile(@NonNull File homeDir) {
        return new File(new File(homeDir, TERMUX_CONFIG_DIRNAME), MOTD_SCRIPT_FILENAME);
    }

    public static boolean isHushLoginPresent(@NonNull File homeDir) {
        return getHushLoginFile(homeDir).exists();
    }

    public static boolean isWelcomeBannerEnabled(@NonNull Context context) {
        return isWelcomeBannerEnabled(context, TermuxConstants.TERMUX_HOME_DIR);
    }

    public static boolean isWelcomeBannerEnabled(@NonNull Context context, @NonNull File homeDir) {
        if (isHushLoginPresent(homeDir)) {
            return false;
        }
        return TermuxShellPreferences.isWelcomeBannerEnabled(context);
    }

    public static void setWelcomeBannerEnabled(@NonNull Context context, boolean enabled) {
        setWelcomeBannerEnabled(context, TermuxConstants.TERMUX_HOME_DIR, enabled);
    }

    public static void setWelcomeBannerEnabled(@NonNull Context context, @NonNull File homeDir, boolean enabled) {
        TermuxShellPreferences.setWelcomeBannerEnabled(context, enabled);
        applyWelcomeBannerState(homeDir, enabled);
    }

    /**
     * Applies the welcome banner state on disk inside {@code homeDir}:
     * <ul>
     *   <li>When {@code enabled == true}: removes {@code ~/.hushlogin} and ensures executable
     *       {@code ~/.termux/motd.sh} exists (without overwriting an existing custom {@code motd.sh}).</li>
     *   <li>When {@code enabled == false}: creates {@code ~/.hushlogin} to suppress login MOTD
     *       while leaving {@code ~/.termux/motd.sh} intact.</li>
     * </ul>
     */
    public static boolean applyWelcomeBannerState(@NonNull File homeDir, boolean enabled) {
        if (!ShellFsUtils.ensureDir0700(homeDir)) {
            return false;
        }
        File hushLogin = getHushLoginFile(homeDir);
        if (enabled) {
            if (hushLogin.exists() && !ShellFsUtils.deleteLinkOrFile(hushLogin)) {
                Logger.logWarn(LOG_TAG, "Failed to remove " + hushLogin.getAbsolutePath());
            }
            return ensureWelcomeBannerScript(homeDir, false);
        } else {
            try {
                if (!hushLogin.exists()) {
                    ShellFsUtils.writeUtf8(hushLogin, "");
                    ShellFsUtils.chmod0600(hushLogin);
                }
                return true;
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to create " + hushLogin.getAbsolutePath(), e);
                return false;
            }
        }
    }

    /**
     * Installs {@code ~/.termux/motd.sh} if it does not already exist, and synchronizes {@code ~/.hushlogin}
     * if {@link TermuxShellPreferences} explicitly disabled the welcome banner. Never deletes a pre-existing
     * user {@code ~/.hushlogin} and never overwrites an existing {@code ~/.termux/motd.sh}.
     */
    public static boolean installDefaultBannerIfMissing(@NonNull Context context, @NonNull File homeDir) {
        boolean scriptOk = ensureWelcomeBannerScript(homeDir, false);
        if (TermuxShellPreferences.hasWelcomeBannerPreference(context) &&
            !TermuxShellPreferences.isWelcomeBannerEnabled(context)) {
            applyWelcomeBannerState(homeDir, false);
        }
        return scriptOk;
    }

    public static boolean installDefaultBannerIfMissing(@NonNull File homeDir) {
        return ensureWelcomeBannerScript(homeDir, false);
    }

    /**
     * Ensures {@code ~/.termux/motd.sh} exists and has {@code 0700} executable permissions.
     *
     * @param homeDir   Termux {@code $HOME} directory
     * @param overwrite if {@code true}, overwrites any existing {@code ~/.termux/motd.sh} with the default script
     */
    public static boolean ensureWelcomeBannerScript(@NonNull File homeDir, boolean overwrite) {
        File termuxDir = new File(homeDir, TERMUX_CONFIG_DIRNAME);
        if (!ShellFsUtils.ensureDir0700(termuxDir)) {
            return false;
        }
        File motdFile = new File(termuxDir, MOTD_SCRIPT_FILENAME);
        try {
            if (overwrite || !motdFile.exists()) {
                ShellFsUtils.writeUtf8(motdFile, DEFAULT_WELCOME_BANNER_SCRIPT);
            }
            ShellFsUtils.chmod0700(motdFile);
            return true;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write " + motdFile.getAbsolutePath(), e);
            return false;
        }
    }
}
