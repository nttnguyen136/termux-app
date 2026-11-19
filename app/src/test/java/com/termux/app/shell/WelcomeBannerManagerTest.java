package com.termux.app.shell;

import android.content.Context;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class WelcomeBannerManagerTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDefaultWelcomeBannerOnFreshInstall() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File homeDir = tempFolder.newFolder("home");

        assertTrue(WelcomeBannerManager.isWelcomeBannerEnabled(context, homeDir));
        assertTrue(WelcomeBannerManager.installDefaultBannerIfMissing(context, homeDir));

        File motdFile = WelcomeBannerManager.getMotdScriptFile(homeDir);
        assertTrue(motdFile.isFile());
        assertTrue(motdFile.canExecute());
        assertFalse(WelcomeBannerManager.isHushLoginPresent(homeDir));

        String script = ShellFsUtils.readUtf8(motdFile);
        assertTrue(script.startsWith("#!/data/data/com.termux/files/usr/bin/sh\n"));
        assertTrue(script.contains("\uf120 Termux"));
        assertTrue(script.contains("\uf0e7 Ready"));
    }

    @Test
    public void testToggleWelcomeBannerOffAndOn() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File homeDir = tempFolder.newFolder("home-toggle");

        WelcomeBannerManager.installDefaultBannerIfMissing(context, homeDir);
        File motdFile = WelcomeBannerManager.getMotdScriptFile(homeDir);
        File hushLogin = WelcomeBannerManager.getHushLoginFile(homeDir);

        // Toggle OFF -> persists false, creates ~/.hushlogin, preserves motd.sh
        WelcomeBannerManager.setWelcomeBannerEnabled(context, homeDir, false);
        assertFalse(WelcomeBannerManager.isWelcomeBannerEnabled(context, homeDir));
        assertFalse(TermuxShellPreferences.isWelcomeBannerEnabled(context));
        assertTrue(hushLogin.exists());
        assertTrue(motdFile.isFile());

        // Toggle ON -> persists true, deletes ~/.hushlogin, ensures motd.sh exists and is executable
        WelcomeBannerManager.setWelcomeBannerEnabled(context, homeDir, true);
        assertTrue(WelcomeBannerManager.isWelcomeBannerEnabled(context, homeDir));
        assertTrue(TermuxShellPreferences.isWelcomeBannerEnabled(context));
        assertFalse(hushLogin.exists());
        assertTrue(motdFile.isFile());
        assertTrue(motdFile.canExecute());
    }

    @Test
    public void testUpgradeSafetyRespectsPreExistingHushLoginAndCustomMotd() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File homeDir = tempFolder.newFolder("home-upgrade");

        // User already created ~/.hushlogin and custom ~/.termux/motd.sh before upgrading
        File hushLogin = WelcomeBannerManager.getHushLoginFile(homeDir);
        ShellFsUtils.writeUtf8(hushLogin, "");
        File customMotd = WelcomeBannerManager.getMotdScriptFile(homeDir);
        String customScript = "#!/data/data/com.termux/files/usr/bin/sh\necho 'My Custom Banner'\n";
        ShellFsUtils.writeUtf8(customMotd, customScript);

        // Preference is unset, but ~/.hushlogin exists -> must report disabled
        assertFalse(WelcomeBannerManager.isWelcomeBannerEnabled(context, homeDir));

        // Running installDefaultBannerIfMissing must NOT delete ~/.hushlogin or overwrite custom motd.sh
        WelcomeBannerManager.installDefaultBannerIfMissing(context, homeDir);
        assertTrue(hushLogin.exists());
        assertEquals(customScript, ShellFsUtils.readUtf8(customMotd));
    }
}
