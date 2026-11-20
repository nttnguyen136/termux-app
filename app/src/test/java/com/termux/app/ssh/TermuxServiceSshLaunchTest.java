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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
            assertNotNull(latestDialog);
            assertTrue(latestDialog.isShowing());
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

            SshConnectionLauncher.LaunchOutcome outcome = launcher.connect(profile);
            assertEquals(SshConnectionLauncher.LaunchStatus.LAUNCHED, outcome.status);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertEquals(1, service.getTermuxSessionsSize());
            TermuxSession created = service.getLastTermuxSession();
            assertNotNull(created);
            assertEquals("Bound Host", created.getTerminalSession().mSessionName);

            Intent launchedActivity = Shadows.shadowOf(callerActivity).getNextStartedActivity();
            assertNotNull(launchedActivity);
            assertEquals(TermuxActivity.class.getName(), launchedActivity.getComponent().getClassName());
        }
    }
}
