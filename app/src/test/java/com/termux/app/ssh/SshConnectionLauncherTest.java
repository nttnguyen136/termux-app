package com.termux.app.ssh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SshConnectionLauncherTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @org.junit.Before
    @org.junit.After
    public void resetAppearancePreferences() {
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        if (com.termux.shared.termux.theme.TermuxAppearancePreferences.getPreferences(context) != null) {
            com.termux.shared.termux.theme.TermuxAppearancePreferences.getPreferences(context)
                .edit().clear().commit();
        }
        com.termux.terminal.TerminalColors.COLOR_SCHEME.updateWith(new java.util.Properties());
        com.termux.shared.theme.NightMode.setAppNightMode(com.termux.shared.theme.NightMode.TRUE.getName());
    }

    @Test
    public void launchesDirectSshSessionWithAskPassForSavedSecretAndWithoutAskPassWhenNoSecretAndUpdatesLastUsedOnSuccess()
        throws Exception {
        File prefixDir = tempFolder.newFolder("prefix");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home");
        File tmpDir = tempFolder.newFolder("tmp");
        File storageDir = tempFolder.newFolder("storage");

        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );

        SshProfile passwordProfile = SshProfile.builder()
            .displayName("Staging API")
            .host("staging.example.com")
            .port(2222)
            .username("deploy")
            .authType(SshAuthType.PASSWORD)
            .build();
        repo.saveProfileWithSecret(passwordProfile, "s3cr3t-password!");

        SshProfile keyNoPassphrase = SshProfile.builder()
            .displayName("Prod Bastion")
            .host("bastion.example.com")
            .username("admin")
            .authType(SshAuthType.SSH_KEY)
            .privateKeyPath("/home/.ssh/id_ed25519")
            .build();
        repo.saveProfile(keyNoPassphrase);

        List<SshSessionLaunchRequest> launched = new ArrayList<>();
        AtomicBoolean gatewayShouldSucceed = new AtomicBoolean(true);
        AtomicLong now = new AtomicLong(1_800_000_000_000L);

        SshSessionLaunchGateway fakeGateway = request -> {
            launched.add(request);
            return gatewayShouldSucceed.get();
        };

        SshConnectionLauncher launcher = new SshConnectionLauncher(
            null,
            repo,
            prefixDir,
            homeDir,
            tmpDir,
            fakeGateway,
            now::get,
            (profile, onConfirm) -> {
                throw new AssertionError("Should not prompt to install openssh when ssh exists");
            }
        );

        SshConnectionLauncher.LaunchOutcome passOutcome = launcher.connect(passwordProfile);
        assertEquals(SshConnectionLauncher.LaunchStatus.LAUNCHED, passOutcome.status);
        assertEquals(1, launched.size());

        SshSessionLaunchRequest passReq = launched.get(0);
        assertEquals(sshFile.getAbsolutePath(), passReq.executablePath);
        assertEquals("Staging API", passReq.sessionTitle);
        assertEquals(homeDir.getAbsolutePath(), passReq.workingDirectory);
        assertTrue(passReq.arguments.contains("-p"));
        assertTrue(passReq.arguments.contains("2222"));
        assertTrue(passReq.arguments.contains("deploy@staging.example.com"));

        String askPassPath = passReq.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS);
        assertNotNull(askPassPath);
        File askPassFile = new File(askPassPath);
        assertTrue(askPassFile.isFile());
        assertTrue(askPassFile.canExecute());
        assertTrue(askPassPath.startsWith(tmpDir.getAbsolutePath()));
        assertEquals("force", passReq.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS_REQUIRE));
        assertEquals(":0", passReq.additionalEnvironment.get(SshAskPassManager.ENV_DISPLAY));

        SshProfile updatedPass = repo.getById(passwordProfile.id);
        assertNotNull(updatedPass);
        assertEquals(1_800_000_000_000L, updatedPass.lastUsedAtMillis);

        // Clean up files created during first launch so we can inspect failure cleanup cleanly
        File[] existingTmpFiles = tmpDir.listFiles();
        if (existingTmpFiles != null) {
            for (File f : existingTmpFiles) {
                assertTrue(f.delete());
            }
        }

        // Launch key profile with no saved passphrase -> no SSH_ASKPASS env vars
        now.set(1_800_000_005_000L);
        SshConnectionLauncher.LaunchOutcome keyOutcome = launcher.connect(keyNoPassphrase);
        assertEquals(SshConnectionLauncher.LaunchStatus.LAUNCHED, keyOutcome.status);
        assertEquals(2, launched.size());
        SshSessionLaunchRequest keyReq = launched.get(1);
        assertEquals("Prod Bastion", keyReq.sessionTitle);
        assertTrue(keyReq.additionalEnvironment.isEmpty());
        assertTrue(keyReq.arguments.contains("-i"));
        assertTrue(keyReq.arguments.contains("/home/.ssh/id_ed25519"));
        assertTrue(keyReq.arguments.contains("IdentitiesOnly=yes"));
        assertEquals(1_800_000_005_000L, repo.getById(keyNoPassphrase.id).lastUsedAtMillis);
        assertEquals(0, tmpDir.listFiles().length);

        // When gateway fails, askpass helper/secret must be cleaned up and lastUsed must not advance
        gatewayShouldSucceed.set(false);
        now.set(1_900_000_000_000L);
        SshConnectionLauncher.LaunchOutcome failOutcome = launcher.connect(passwordProfile);
        assertEquals(SshConnectionLauncher.LaunchStatus.LAUNCH_FAILED, failOutcome.status);
        assertEquals(0, tmpDir.listFiles().length);
        assertEquals(1_800_000_000_000L, repo.getById(passwordProfile.id).lastUsedAtMillis);
    }

    @Test
    public void missingOpenSshPromptsMaterial3DialogAndLaunchesInstallThenConnectOnConfirm()
        throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-missing");
        File homeDir = tempFolder.newFolder("home-missing");
        File tmpDir = tempFolder.newFolder("tmp-missing");
        File storageDir = tempFolder.newFolder("storage-missing");

        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        SshProfile profile = SshProfile.builder()
            .displayName("Edge Server")
            .host("edge.example.com")
            .port(2202)
            .username("ubuntu")
            .authType(SshAuthType.PASSWORD)
            .startupCommand("htop")
            .build();
        repo.saveProfileWithSecret(profile, "edge-pass-!");

        List<SshSessionLaunchRequest> launched = new ArrayList<>();
        AtomicLong now = new AtomicLong(1_850_000_000_000L);

        try (org.robolectric.android.controller.ActivityController<com.termux.app.activities.SettingsActivity> controller =
                 org.robolectric.Robolectric.buildActivity(com.termux.app.activities.SettingsActivity.class)) {
            com.termux.app.activities.SettingsActivity activity = controller.create().start().resume().get();

            com.termux.shared.termux.theme.TermuxThemeManager.setSelectedThemeId(
                activity,
                com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
            );
            com.termux.shared.termux.theme.TermuxColorPalette latte =
                com.termux.shared.termux.theme.TermuxThemePresets.getBuiltInById(
                    com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
                );

            SshConnectionLauncher launcher = new SshConnectionLauncher(
                activity,
                repo,
                prefixDir,
                homeDir,
                tmpDir,
                request -> {
                    launched.add(request);
                    return true;
                },
                now::get
            );

            assertFalse(launcher.isOpenSshInstalled());

            SshConnectionLauncher.LaunchOutcome promptOutcome = launcher.connect(profile);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(SshConnectionLauncher.LaunchStatus.PROMPTED_INSTALL_OPENSSH, promptOutcome.status);
            androidx.appcompat.app.AlertDialog dialog = promptOutcome.dialog;
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());

            // Before confirming: nothing launched, no temp secrets created, lastUsed untouched
            assertTrue(launched.isEmpty());
            assertTrue(tmpDir.listFiles() == null || tmpDir.listFiles().length == 0);
            assertEquals(0L, repo.getById(profile.id).lastUsedAtMillis);

            android.view.View root = dialog.findViewById(com.termux.R.id.ssh_install_dialog_root);
            assertNotNull(root);
            assertTrue(root.getBackground() instanceof android.graphics.drawable.GradientDrawable);

            android.widget.TextView titleView = dialog.findViewById(com.termux.R.id.ssh_install_dialog_title);
            android.widget.TextView messageView = dialog.findViewById(com.termux.R.id.ssh_install_dialog_message);
            android.widget.TextView previewView = dialog.findViewById(com.termux.R.id.ssh_install_dialog_command_preview);
            assertNotNull(titleView);
            assertNotNull(messageView);
            assertNotNull(previewView);
            assertEquals(latte.onSurface, titleView.getCurrentTextColor());
            assertEquals(latte.onSurfaceVariant, messageView.getCurrentTextColor());
            assertTrue(messageView.getText().toString().contains("Edge Server"));
            assertEquals("pkg install -y openssh", previewView.getText().toString());
            assertTrue(previewView.getBackground() instanceof android.graphics.drawable.GradientDrawable);
            assertEquals(
                latte.accent,
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getCurrentTextColor()
            );

            // Cancel dismisses without launching or writing secrets
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(dialog.isShowing());
            assertTrue(launched.isEmpty());
            assertTrue(tmpDir.listFiles() == null || tmpDir.listFiles().length == 0);

            // Re-open with Dracula dark theme and tap Install & Connect
            com.termux.shared.termux.theme.TermuxThemeManager.setSelectedThemeId(
                activity,
                com.termux.shared.termux.theme.TermuxColorPalette.THEME_DRACULA
            );
            com.termux.shared.termux.theme.TermuxColorPalette dracula =
                com.termux.shared.termux.theme.TermuxThemePresets.getBuiltInById(
                    com.termux.shared.termux.theme.TermuxColorPalette.THEME_DRACULA
                );

            SshConnectionLauncher.LaunchOutcome secondOutcome = launcher.connect(profile);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog secondDialog = secondOutcome.dialog;
            assertNotNull(secondDialog);
            assertTrue(secondDialog.isShowing());
            android.widget.TextView secondTitle = secondDialog.findViewById(com.termux.R.id.ssh_install_dialog_title);
            assertEquals(dracula.onSurface, secondTitle.getCurrentTextColor());
            assertEquals(
                dracula.accent,
                secondDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getCurrentTextColor()
            );

            secondDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(secondDialog.isShowing());

            assertEquals(1, launched.size());
            SshSessionLaunchRequest req = launched.get(0);
            assertEquals(new File(prefixDir, "bin/sh").getAbsolutePath(), req.executablePath);
            assertEquals(2, req.arguments.size());
            assertEquals("-c", req.arguments.get(0));
            assertTrue(req.arguments.get(1).startsWith("pkg install -y openssh && exec "));
            assertTrue(req.arguments.get(1).contains("ubuntu@edge.example.com"));
            assertTrue(req.arguments.get(1).contains("htop"));
            assertEquals("Edge Server", req.sessionTitle);
            assertNotNull(req.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS));
            assertEquals("force", req.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS_REQUIRE));
            assertEquals(":0", req.additionalEnvironment.get(SshAskPassManager.ENV_DISPLAY));
            assertEquals(1_850_000_000_000L, repo.getById(profile.id).lastUsedAtMillis);
        }
    }

    @Test
    public void credentialDecryptionFailureAbortsLaunchAndShowsMaterial3ErrorDialog() throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-cred-err");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home-cred-err");
        File tmpDir = tempFolder.newFolder("tmp-cred-err");
        File storageDir = tempFolder.newFolder("storage-cred-err");

        SshProfileRepository writerRepo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        SshProfile profile = SshProfile.builder()
            .displayName("Broken Key Host")
            .host("broken.example.com")
            .username("ops")
            .authType(SshAuthType.PASSWORD)
            .build();
        writerRepo.saveProfileWithSecret(profile, "encrypted-with-old-key");

        // Re-open with a different key so decryption throws AEADBadTagException
        SshProfileRepository readerRepo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );

        List<SshSessionLaunchRequest> launched = new ArrayList<>();
        try (org.robolectric.android.controller.ActivityController<com.termux.app.activities.SettingsActivity> controller =
                 org.robolectric.Robolectric.buildActivity(com.termux.app.activities.SettingsActivity.class)) {
            com.termux.app.activities.SettingsActivity activity = controller.create().start().resume().get();

            com.termux.shared.termux.theme.TermuxThemeManager.setSelectedThemeId(
                activity,
                com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
            );
            com.termux.shared.termux.theme.TermuxColorPalette latte =
                com.termux.shared.termux.theme.TermuxThemePresets.getBuiltInById(
                    com.termux.shared.termux.theme.TermuxColorPalette.THEME_CATPPUCCIN_LATTE
                );

            SshConnectionLauncher launcher = new SshConnectionLauncher(
                activity,
                readerRepo,
                prefixDir,
                homeDir,
                tmpDir,
                request -> {
                    launched.add(request);
                    return true;
                },
                () -> 2_100_000_000_000L
            );

            SshConnectionLauncher.LaunchOutcome outcome = launcher.connect(profile);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertEquals(SshConnectionLauncher.LaunchStatus.CREDENTIAL_ERROR, outcome.status);
            assertTrue(launched.isEmpty());
            assertTrue(tmpDir.listFiles() == null || tmpDir.listFiles().length == 0);
            assertEquals(0L, readerRepo.getById(profile.id).lastUsedAtMillis);

            android.app.Dialog latest = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(latest instanceof androidx.appcompat.app.AlertDialog);
            androidx.appcompat.app.AlertDialog errorDialog = (androidx.appcompat.app.AlertDialog) latest;
            assertTrue(errorDialog.isShowing());

            android.graphics.drawable.Drawable bg = errorDialog.getWindow().getDecorView().getBackground();
            assertTrue(bg instanceof android.graphics.drawable.GradientDrawable);
            android.graphics.drawable.GradientDrawable windowBg = (android.graphics.drawable.GradientDrawable) bg;
            assertEquals(latte.surfaceContainerHigh, windowBg.getColor().getDefaultColor());
            float expectedRadius = 24f * activity.getResources().getDisplayMetrics().density;
            assertEquals(expectedRadius, windowBg.getCornerRadius(), 0.5f);

            android.widget.TextView titleView = errorDialog.findViewById(androidx.appcompat.R.id.alertTitle);
            android.widget.TextView messageView = errorDialog.findViewById(android.R.id.message);
            assertNotNull(titleView);
            assertNotNull(messageView);
            assertEquals(latte.onSurface, titleView.getCurrentTextColor());
            assertEquals(latte.onSurfaceVariant, messageView.getCurrentTextColor());
            assertEquals(
                latte.accent,
                errorDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getCurrentTextColor()
            );
        }
    }
}
