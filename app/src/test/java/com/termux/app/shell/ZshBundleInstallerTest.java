package com.termux.app.shell;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ZshBundleInstallerTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDetectTermuxArchReadsDpkgStatusFirst() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File prefixDir = tempFolder.newFolder("prefix-arch");
        File dpkgDir = new File(prefixDir, "var/lib/dpkg");
        assertTrue(dpkgDir.mkdirs());
        File statusFile = new File(dpkgDir, "status");

        // Even if first package is Architecture: all, should pick the first concrete arch
        String statusContent =
            "Package: termux-keyring\n" +
            "Status: install ok installed\n" +
            "Architecture: all\n" +
            "\n" +
            "Package: bash\n" +
            "Status: install ok installed\n" +
            "Architecture: aarch64\n";
        ShellFsUtils.writeUtf8(statusFile, statusContent);
        assertEquals("aarch64", ZshBundleInstaller.detectTermuxArch(context, prefixDir));

        // 32-bit arm bootstrap on 64-bit host kernel must report "arm" from dpkg status
        String armStatusContent =
            "Package: dpkg\n" +
            "Status: install ok installed\n" +
            "Architecture: arm\n";
        ShellFsUtils.writeUtf8(statusFile, armStatusContent);
        assertEquals("arm", ZshBundleInstaller.detectTermuxArch(context, prefixDir));
    }

    @Test
    public void testOfflineInstallSuccessOrdersLibcapBeforeZshAndCleansUpTempDebs() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File prefixDir = tempFolder.newFolder("prefix-ok");
        File homeDir = tempFolder.newFolder("home-ok");

        setupFakePrefixWithDpkg(prefixDir, "aarch64");

        final List<String> capturedArgs = new ArrayList<>();
        final List<Boolean> tempFilesExistedDuringRun = new ArrayList<>();
        final List<Integer> progressStages = new ArrayList<>();

        BootstrapProgressListener listener = new BootstrapProgressListener() {
            @Override
            public void onStageChanged(@NonNull String title, @Nullable String detail, int progressPercent) {
                progressStages.add(progressPercent);
            }
        };

        ZshBundleInstaller.DebCommandRunner fakeRunner = new ZshBundleInstaller.DebCommandRunner() {
            @Override
            public boolean runDpkgInstall(
                    @NonNull Context ctx,
                    @NonNull File pfx,
                    @NonNull File home,
                    @NonNull File dpkgBinary,
                    @NonNull List<String> arguments) {
                capturedArgs.addAll(arguments);
                for (int i = 3; i < arguments.size(); i++) {
                    File stagedDeb = new File(arguments.get(i));
                    tempFilesExistedDuringRun.add(stagedDeb.isFile() && stagedDeb.length() > 0);
                }
                // Simulate dpkg unpacking $PREFIX/bin/zsh
                try {
                    File zshBin = new File(pfx, "bin/zsh");
                    ShellFsUtils.writeUtf8(zshBin, "#!/bin/sh\n");
                    ShellFsUtils.chmod0700(zshBin);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return true;
            }
        };

        ZshBundleInstaller.installIfNeeded(context, prefixDir, homeDir, listener, fakeRunner);

        // Verify progress stages emitted (65 -> 80 -> 92)
        assertEquals(3, progressStages.size());
        assertEquals(Integer.valueOf(65), progressStages.get(0));
        assertEquals(Integer.valueOf(80), progressStages.get(1));
        assertEquals(Integer.valueOf(92), progressStages.get(2));

        // Verify exact dpkg flags and libcap.deb ordered before zsh.deb
        assertEquals(5, capturedArgs.size());
        assertEquals("-i", capturedArgs.get(0));
        assertEquals("--force-confdef", capturedArgs.get(1));
        assertEquals("--force-confold", capturedArgs.get(2));
        assertTrue(capturedArgs.get(3).endsWith("/libcap.deb"));
        assertTrue(capturedArgs.get(4).endsWith("/zsh.deb"));
        assertEquals(2, tempFilesExistedDuringRun.size());
        assertTrue(tempFilesExistedDuringRun.get(0));
        assertTrue(tempFilesExistedDuringRun.get(1));

        // Verify temp $PREFIX/tmp/shell-bundle directory was cleaned up in finally block
        assertFalse(new File(prefixDir, "tmp/shell-bundle").exists());

        // Verify Oh My Zsh, plugins, theme, .zshrc, .bashrc, motd.sh, and ~/.termux/shell symlink
        ShellConfigManager.ShellStatus status = ShellConfigManager.getShellStatus(context, prefixDir, homeDir);
        assertTrue(status.isZshInstalled());
        assertTrue(status.isZshDefault());
        assertTrue(status.isOhMyZshInstalled());
        assertTrue(status.arePluginsInstalled());
        assertFalse(status.hasCustomZshrc());
        assertFalse(status.hasCustomBashrc());
        assertTrue(status.isWelcomeBannerEnabled());
        assertNotNull(TermuxShellPreferences.getInstalledShellBundleVersion(context));
    }

    @Test
    public void testGracefulFallbackWhenDpkgFailsDoesNotCreateDanglingShellSymlink() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File prefixDir = tempFolder.newFolder("prefix-fail");
        File homeDir = tempFolder.newFolder("home-fail");

        setupFakePrefixWithDpkg(prefixDir, "aarch64");

        ZshBundleInstaller.DebCommandRunner failingRunner = new ZshBundleInstaller.DebCommandRunner() {
            @Override
            public boolean runDpkgInstall(
                    @NonNull Context ctx,
                    @NonNull File pfx,
                    @NonNull File home,
                    @NonNull File dpkgBinary,
                    @NonNull List<String> arguments) {
                return false;
            }
        };

        ZshBundleInstaller.installIfNeeded(context, prefixDir, homeDir, null, failingRunner);

        // Temp debs still cleaned up
        assertFalse(new File(prefixDir, "tmp/shell-bundle").exists());

        // CRITICAL: ~/.termux/shell must NOT be created when $PREFIX/bin/zsh is missing
        File shellLink = new File(homeDir, ".termux/shell");
        assertFalse(ShellFsUtils.existsOrIsSymlink(shellLink));

        // Bash fallback prompt, OMZ files, and MOTD are still configured cleanly
        ShellConfigManager.ShellStatus status = ShellConfigManager.getShellStatus(context, prefixDir, homeDir);
        assertFalse(status.isZshInstalled());
        assertFalse(status.isZshDefault());
        assertTrue(status.getDefaultShellPath().endsWith("/bin/bash"));
        assertTrue(status.isOhMyZshInstalled());
        assertTrue(ShellConfigManager.hasBashPromptBlock(new File(homeDir, ".bashrc")));
        assertTrue(WelcomeBannerManager.getMotdScriptFile(homeDir).isFile());
    }

    @Test
    public void testFirstRunUpgradeSafetyNeverClobbersExistingUserDotfiles() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File prefixDir = tempFolder.newFolder("prefix-upgrade");
        File homeDir = tempFolder.newFolder("home-upgrade");

        setupFakePrefixWithDpkg(prefixDir, "aarch64");
        File zshBin = new File(prefixDir, "bin/zsh");
        ShellFsUtils.writeUtf8(zshBin, "#!/bin/sh\n");
        ShellFsUtils.chmod0700(zshBin);

        // Pre-populate existing user dotfiles in $HOME
        File zshrc = new File(homeDir, ".zshrc");
        String userZshrc = "# User custom zshrc\nexport CUSTOM_ZSH=1\n";
        ShellFsUtils.writeUtf8(zshrc, userZshrc);

        File bashrc = new File(homeDir, ".bashrc");
        String userBashrc = "# User unmarked bashrc\nexport CUSTOM_BASH=1\n";
        ShellFsUtils.writeUtf8(bashrc, userBashrc);

        File hushLogin = WelcomeBannerManager.getHushLoginFile(homeDir);
        ShellFsUtils.writeUtf8(hushLogin, "");

        File customShellLink = new File(homeDir, ".termux/shell");
        File bashBin = new File(prefixDir, "bin/bash");
        ShellFsUtils.createSymlink(bashBin.getAbsolutePath(), customShellLink, true);

        ZshBundleInstaller.installIfNeeded(context, prefixDir, homeDir, null);

        // Verify none of the user's pre-existing dotfiles or shell symlink were overwritten
        assertEquals(userZshrc, ShellFsUtils.readUtf8(zshrc));
        assertEquals(userBashrc, ShellFsUtils.readUtf8(bashrc));
        assertTrue(hushLogin.exists());
        ShellConfigManager.ShellStatus status = ShellConfigManager.getShellStatus(context, prefixDir, homeDir);
        assertFalse(status.isZshDefault());
        assertTrue(status.getDefaultShellPath().endsWith("/bin/bash"));
        assertFalse(status.isWelcomeBannerEnabled());
    }

    private static void setupFakePrefixWithDpkg(File prefixDir, String arch) throws Exception {
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File dpkgBin = new File(binDir, "dpkg");
        ShellFsUtils.writeUtf8(dpkgBin, "#!/bin/sh\nexit 0\n");
        ShellFsUtils.chmod0700(dpkgBin);

        File bashBin = new File(binDir, "bash");
        ShellFsUtils.writeUtf8(bashBin, "#!/bin/sh\nexit 0\n");
        ShellFsUtils.chmod0700(bashBin);

        File dpkgStatus = new File(prefixDir, "var/lib/dpkg/status");
        ShellFsUtils.writeUtf8(dpkgStatus,
            "Package: bash\n" +
            "Status: install ok installed\n" +
            "Architecture: " + arch + "\n");
    }
}
