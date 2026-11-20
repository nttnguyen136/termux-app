package com.termux.app.ssh;

import android.app.Application;
import android.content.Intent;

import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TermuxServiceSshLaunchTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private TermuxService service;

    @Before
    public void setUp() {
        TermuxShellManager manager = TermuxShellManager.getShellManager();
        if (manager != null) {
            manager.mTermuxSessions.clear();
            manager.mTermuxTasks.clear();
        }
        Application app = RuntimeEnvironment.getApplication();
        resetAppearanceState(app);
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app);
        if (prefs != null) {
            prefs.setCurrentSession(null);
        }
        ServiceController<TermuxService> serviceController = Robolectric.buildService(TermuxService.class).create();
        service = serviceController.get();
    }

    @After
    public void tearDown() {
        TermuxShellManager manager = TermuxShellManager.getShellManager();
        if (manager != null) {
            manager.mTermuxSessions.clear();
            manager.mTermuxTasks.clear();
        }
        Application app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app);
        if (prefs != null) {
            prefs.setCurrentSession(null);
        }
        resetAppearanceState(app);
    }

    private static void resetAppearanceState(Application app) {
        if (com.termux.shared.termux.theme.TermuxAppearancePreferences.getPreferences(app) != null) {
            com.termux.shared.termux.theme.TermuxAppearancePreferences.getPreferences(app)
                .edit().clear().commit();
        }
        com.termux.terminal.TerminalColors.COLOR_SCHEME.updateWith(new java.util.Properties());
        com.termux.shared.theme.NightMode.setAppNightMode(com.termux.shared.theme.NightMode.TRUE.getName());
    }

    @Test
    public void createTermuxSession_forwardsAdditionalEnvironmentAndSessionNameToTerminalSession()
        throws Exception {
        HashMap<String, String> extraEnv = new HashMap<>();
        extraEnv.put("SSH_ASKPASS", "/tmp/askpass-test.sh");
        extraEnv.put("SSH_ASKPASS_REQUIRE", "force");
        extraEnv.put("DISPLAY", ":0");

        TermuxSession termuxSession = service.createTermuxSession(
            "/data/data/com.termux/files/usr/bin/ssh",
            new String[]{"-p", "2222", "deploy@prod.example.com"},
            null,
            "/data/data/com.termux/files/home",
            false,
            "Prod Web",
            extraEnv
        );

        assertNotNull(termuxSession);
        assertEquals(1, service.getTermuxSessionsSize());
        assertEquals("Prod Web", termuxSession.getExecutionCommand().shellName);

        TerminalSession terminalSession = termuxSession.getTerminalSession();
        assertNotNull(terminalSession);
        assertEquals("Prod Web", terminalSession.mSessionName);
        assertEquals(
            Arrays.asList("ssh", "-p", "2222", "deploy@prod.example.com"),
            Arrays.asList(termuxSession.getExecutionCommand().arguments)
        );

        Field envField = TerminalSession.class.getDeclaredField("mEnv");
        envField.setAccessible(true);
        String[] envArray = (String[]) envField.get(terminalSession);
        assertNotNull(envArray);
        List<String> envList = Arrays.asList(envArray);
        assertTrue(envList.contains("SSH_ASKPASS=/tmp/askpass-test.sh"));
        assertTrue(envList.contains("SSH_ASKPASS_REQUIRE=force"));
        assertTrue(envList.contains("DISPLAY=:0"));
    }

    @Test
    public void termuxSshSessionLaunchGateway_createsSelectsSessionAndOpensTermuxActivity()
        throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-gw");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home-gw");
        File tmpDir = tempFolder.newFolder("tmp-gw");
        File storageDir = tempFolder.newFolder("storage-gw");

        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        SshProfile profile = SshProfile.builder()
            .displayName("Gateway Host")
            .host("gw.example.com")
            .username("ops")
            .authType(SshAuthType.PASSWORD)
            .build();
        repo.saveProfileWithSecret(profile, "gw-secret");

        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class)) {
            SettingsActivity callerActivity = controller.create().start().resume().get();

            SshConnectionLauncher launcher = new SshConnectionLauncher(
                callerActivity,
                repo,
                prefixDir,
                homeDir,
                tmpDir,
                TermuxSshSessionLaunchGateway.forService(callerActivity, service),
                () -> 1_900_000_000_000L
            );

            SshConnectionLauncher.LaunchOutcome outcome = launcher.connect(profile);
            assertEquals(SshConnectionLauncher.LaunchStatus.LAUNCHED, outcome.status);
            assertEquals(1, service.getTermuxSessionsSize());

            TermuxSession created = service.getLastTermuxSession();
            assertNotNull(created);
            assertEquals("Gateway Host", created.getTerminalSession().mSessionName);
            assertEquals(sshFile.getAbsolutePath(), created.getExecutionCommand().executable);

            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(callerActivity);
            assertNotNull(prefs);
            assertEquals(created.getTerminalSession().mHandle, prefs.getCurrentSession());

            Intent launchedActivity = Shadows.shadowOf(callerActivity).getNextStartedActivity();
            assertNotNull(launchedActivity);
            assertEquals(TermuxActivity.class.getName(), launchedActivity.getComponent().getClassName());
            assertEquals(1_900_000_000_000L, repo.getById(profile.id).lastUsedAtMillis);
        }
    }

    @Test
    public void termuxSshSessionLaunchGateway_enforcesMaxSessionsAndCleansUpAskPassFiles()
        throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-max");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home-max");
        File tmpDir = tempFolder.newFolder("tmp-max");
        File storageDir = tempFolder.newFolder("storage-max");

        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        SshProfile profile = SshProfile.builder()
            .displayName("Overflow Host")
            .host("overflow.example.com")
            .username("ops")
            .authType(SshAuthType.PASSWORD)
            .build();
        repo.saveProfileWithSecret(profile, "secret");

        for (int i = 0; i < com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS; i++) {
            service.createTermuxSession("/system/bin/sh", null, null, "/", true, "S" + i);
        }
        assertEquals(
            com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS,
            service.getTermuxSessionsSize()
        );

        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class)) {
            SettingsActivity callerActivity = controller.create().start().resume().get();

            com.termux.shared.termux.theme.TermuxThemeManager.setSelectedThemeId(
                callerActivity,
                com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
            );
            com.termux.shared.termux.theme.TermuxColorPalette latte =
                com.termux.shared.termux.theme.TermuxThemePresets.getBuiltInById(
                    com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
                );

            SshConnectionLauncher launcher = new SshConnectionLauncher(
                callerActivity,
                repo,
                prefixDir,
                homeDir,
                tmpDir,
                TermuxSshSessionLaunchGateway.forService(callerActivity, service),
                () -> 1_950_000_000_000L
            );

            SshConnectionLauncher.LaunchOutcome outcome = launcher.connect(profile);
            assertEquals(SshConnectionLauncher.LaunchStatus.LAUNCH_FAILED, outcome.status);
            assertEquals(
                com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS,
                service.getTermuxSessionsSize()
            );
            assertTrue(tmpDir.listFiles() == null || tmpDir.listFiles().length == 0);
            assertEquals(0L, repo.getById(profile.id).lastUsedAtMillis);

            android.app.Dialog latestDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(latestDialog instanceof androidx.appcompat.app.AlertDialog);
            androidx.appcompat.app.AlertDialog maxDialog = (androidx.appcompat.app.AlertDialog) latestDialog;
            assertTrue(maxDialog.isShowing());

            android.graphics.drawable.Drawable bg = maxDialog.getWindow().getDecorView().getBackground();
            assertTrue(bg instanceof android.graphics.drawable.GradientDrawable);
            android.graphics.drawable.GradientDrawable windowBg = (android.graphics.drawable.GradientDrawable) bg;
            assertEquals(latte.surfaceContainerHigh, windowBg.getColor().getDefaultColor());
            assertEquals(
                latte.onSurface,
                ((android.widget.TextView) maxDialog.findViewById(androidx.appcompat.R.id.alertTitle)).getCurrentTextColor()
            );
            assertEquals(
                latte.onSurfaceVariant,
                ((android.widget.TextView) maxDialog.findViewById(android.R.id.message)).getCurrentTextColor()
            );
            assertEquals(
                latte.accent,
                maxDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getCurrentTextColor()
            );
        }
    }

    @Test
    public void termuxSshSessionLaunchGateway_forActivityBindsServiceAndLaunchesFromNonTerminalActivity()
        throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-bind");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home-bind");
        File tmpDir = tempFolder.newFolder("tmp-bind");
        File storageDir = tempFolder.newFolder("storage-bind");

        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        SshProfile profile = SshProfile.builder()
            .displayName("Bound Host")
            .host("bound.example.com")
            .username("alice")
            .build();
        repo.saveProfile(profile);

        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            new android.content.ComponentName(app, TermuxService.class),
            service.onBind(new Intent(app, TermuxService.class))
        );

        try (ActivityController<SettingsActivity> controller =
                 Robolectric.buildActivity(SettingsActivity.class)) {
            SettingsActivity callerActivity = controller.create().start().resume().get();

            SshConnectionLauncher launcher = new SshConnectionLauncher(
                callerActivity,
                repo,
                prefixDir,
                homeDir,
                tmpDir,
                TermuxSshSessionLaunchGateway.forActivity(callerActivity),
                () -> 2_000_000_000_000L
            );

            List<SshConnectionLauncher.LaunchOutcome> outcomes = new ArrayList<>();
            SshConnectionLauncher.LaunchOutcome immediate = launcher.connect(profile, outcomes::add);
            assertNotEquals(SshConnectionLauncher.LaunchStatus.LAUNCHED, immediate.status);
            assertTrue("Async bind must not invoke LaunchCallback before onServiceConnected", outcomes.isEmpty());
            assertEquals(0, service.getTermuxSessionsSize());
            assertEquals(0L, repo.getById(profile.id).lastUsedAtMillis);

            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertEquals(1, outcomes.size());
            assertEquals(SshConnectionLauncher.LaunchStatus.LAUNCHED, outcomes.get(0).status);
            assertEquals(1, service.getTermuxSessionsSize());
            TermuxSession created = service.getLastTermuxSession();
            assertNotNull(created);
            assertEquals("Bound Host", created.getTerminalSession().mSessionName);
            assertEquals(2_000_000_000_000L, repo.getById(profile.id).lastUsedAtMillis);

            Intent launchedActivity = Shadows.shadowOf(callerActivity).getNextStartedActivity();
            assertNotNull(launchedActivity);
            assertEquals(TermuxActivity.class.getName(), launchedActivity.getComponent().getClassName());
        }
    }

    @Test
    public void termuxSshSessionLaunchGateway_forActivityAsyncMaxSessionsFailureKeepsCallerAliveCleansAskPassAndShowsThemedDialog()
        throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-async-max");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home-async-max");
        File tmpDir = tempFolder.newFolder("tmp-async-max");
        File storageDir = tempFolder.newFolder("storage-async-max");

        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        SshProfile profile = SshProfile.builder()
            .displayName("Async Max Host")
            .host("async-max.example.com")
            .username("ops")
            .authType(SshAuthType.PASSWORD)
            .build();
        repo.saveProfileWithSecret(profile, "async-secret");

        for (int i = 0; i < com.termux.app.terminal.TermuxTerminalSessionActivityClient.MAX_SESSIONS; i++) {
            service.createTermuxSession("/system/bin/sh", null, null, "/", true, "S" + i);
        }

        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            new android.content.ComponentName(app, TermuxService.class),
            service.onBind(new Intent(app, TermuxService.class))
        );

        SshHostsActivity.setEnvironmentFactoryForTests(activity -> new SshHostsEnvironment(
            repo,
            prefixDir,
            homeDir,
            tmpDir,
            TermuxSshSessionLaunchGateway.forActivity(activity),
            (pfx, home, bin, args) -> SshKeygenRunner.ExecutionResult.failure(1, "", "unused"),
            () -> 2_200_000_000_000L,
            Runnable::run
        ));

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(app))) {
            SshHostsActivity sshHostsActivity = controller.create().start().resume().get();
            com.termux.shared.termux.theme.TermuxThemeManager.setSelectedThemeId(
                sshHostsActivity,
                com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
            );
            com.termux.shared.termux.theme.TermuxColorPalette latte =
                com.termux.shared.termux.theme.TermuxThemePresets.getBuiltInById(
                    com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
                );

            android.widget.LinearLayout listContainer = sshHostsActivity.findViewById(com.termux.R.id.ssh_hosts_list_container);
            assertEquals(1, listContainer.getChildCount());
            listContainer.getChildAt(0).performClick();

            // Before onServiceConnected fires, SshHostsActivity must NOT finish and lastUsed must remain 0
            assertFalse(sshHostsActivity.isFinishing());
            assertEquals(0L, repo.getById(profile.id).lastUsedAtMillis);

            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertFalse(sshHostsActivity.isFinishing());
            assertEquals(0L, repo.getById(profile.id).lastUsedAtMillis);
            assertTrue(tmpDir.listFiles() == null || tmpDir.listFiles().length == 0);

            android.app.Dialog latest = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(latest instanceof androidx.appcompat.app.AlertDialog);
            androidx.appcompat.app.AlertDialog maxDialog = (androidx.appcompat.app.AlertDialog) latest;
            assertTrue(maxDialog.isShowing());
            assertEquals(
                sshHostsActivity.getString(com.termux.R.string.title_max_terminals_reached),
                ((android.widget.TextView) maxDialog.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString()
            );
            assertEquals(
                latte.onSurface,
                ((android.widget.TextView) maxDialog.findViewById(androidx.appcompat.R.id.alertTitle)).getCurrentTextColor()
            );
            assertEquals(
                latte.onSurfaceVariant,
                ((android.widget.TextView) maxDialog.findViewById(android.R.id.message)).getCurrentTextColor()
            );
            assertEquals(
                latte.surfaceContainerHigh,
                ((android.graphics.drawable.GradientDrawable) maxDialog.getWindow().getDecorView().getBackground())
                    .getColor().getDefaultColor()
            );
        } finally {
            SshHostsActivity.setEnvironmentFactoryForTests(null);
        }
    }

    @Test
    public void termuxSshSessionLaunchGateway_forActivityAsyncNullSessionFailureKeepsCallerAliveCleansAskPassAndShowsThemedDialog()
        throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-async-null");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home-async-null");
        File tmpDir = tempFolder.newFolder("tmp-async-null");
        File storageDir = tempFolder.newFolder("storage-async-null");

        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        SshProfile profile = SshProfile.builder()
            .displayName("Async Null Host")
            .host("async-null.example.com")
            .username("ops")
            .authType(SshAuthType.PASSWORD)
            .build();
        repo.saveProfileWithSecret(profile, "null-session-secret");

        Application app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            new android.content.ComponentName(app, TermuxService.class),
            service.onBind(new Intent(app, TermuxService.class))
        );

        final int[] createCalls = {0};
        SshHostsActivity.setEnvironmentFactoryForTests(activity -> new SshHostsEnvironment(
            repo,
            prefixDir,
            homeDir,
            tmpDir,
            TermuxSshSessionLaunchGateway.forActivityForTests(activity, (svc, req) -> {
                createCalls[0]++;
                return null;
            }),
            (pfx, home, bin, args) -> SshKeygenRunner.ExecutionResult.failure(1, "", "unused"),
            () -> 2_300_000_000_000L,
            Runnable::run
        ));

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(app))) {
            SshHostsActivity sshHostsActivity = controller.create().start().resume().get();
            com.termux.shared.termux.theme.TermuxThemeManager.setSelectedThemeId(
                sshHostsActivity,
                com.termux.shared.termux.theme.TermuxColorPalette.THEME_DRACULA
            );
            com.termux.shared.termux.theme.TermuxColorPalette dracula =
                com.termux.shared.termux.theme.TermuxThemePresets.getBuiltInById(
                    com.termux.shared.termux.theme.TermuxColorPalette.THEME_DRACULA
                );

            android.widget.LinearLayout listContainer = sshHostsActivity.findViewById(com.termux.R.id.ssh_hosts_list_container);
            // Rapid double-tap while async bind is pending should only initiate a single launch
            listContainer.getChildAt(0).performClick();
            listContainer.getChildAt(0).performClick();

            assertFalse(sshHostsActivity.isFinishing());
            assertEquals(0L, repo.getById(profile.id).lastUsedAtMillis);

            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertEquals(1, createCalls[0]);
            assertFalse(sshHostsActivity.isFinishing());
            assertEquals(0L, repo.getById(profile.id).lastUsedAtMillis);
            assertTrue(tmpDir.listFiles() == null || tmpDir.listFiles().length == 0);

            android.app.Dialog latest = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(latest instanceof androidx.appcompat.app.AlertDialog);
            androidx.appcompat.app.AlertDialog errDialog = (androidx.appcompat.app.AlertDialog) latest;
            assertTrue(errDialog.isShowing());
            assertEquals(
                sshHostsActivity.getString(com.termux.R.string.ssh_launch_error_title),
                ((android.widget.TextView) errDialog.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString()
            );
            assertEquals(
                sshHostsActivity.getString(com.termux.R.string.ssh_launch_error_session),
                ((android.widget.TextView) errDialog.findViewById(android.R.id.message)).getText().toString()
            );
            assertEquals(
                dracula.onSurface,
                ((android.widget.TextView) errDialog.findViewById(androidx.appcompat.R.id.alertTitle)).getCurrentTextColor()
            );
            assertEquals(
                dracula.onSurfaceVariant,
                ((android.widget.TextView) errDialog.findViewById(android.R.id.message)).getCurrentTextColor()
            );
            assertEquals(
                dracula.surfaceContainerHigh,
                ((android.graphics.drawable.GradientDrawable) errDialog.getWindow().getDecorView().getBackground())
                    .getColor().getDefaultColor()
            );
        } finally {
            SshHostsActivity.setEnvironmentFactoryForTests(null);
        }
    }
}
