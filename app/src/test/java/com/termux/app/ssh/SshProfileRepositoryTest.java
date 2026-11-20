package com.termux.app.ssh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class SshProfileRepositoryTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void persistsAndReloadsProfilesViaJsonWithoutLeakingSecretsOrTouchingSshConfig() throws Exception {
        File storageDir = tempFolder.newFolder("ssh-storage");
        File homeDir = tempFolder.newFolder("home");
        File dotSshDir = new File(homeDir, ".ssh");
        assertTrue(dotSshDir.mkdirs());
        File sshConfig = new File(dotSshDir, "config");
        String originalSshConfig = "Host legacy\n  HostName 10.0.0.10\n  User ops\n";
        Files.write(sshConfig.toPath(), originalSshConfig.getBytes(StandardCharsets.UTF_8));

        SshSecretCipher cipher = AesGcmSshSecretCipher.withRandomKey();
        SshProfileRepository repo = new SshProfileRepository(storageDir, cipher);

        SshProfile keyProfile = SshProfile.builder()
            .displayName("Prod Bastion")
            .host("bastion.example.com")
            .port(2222)
            .username("admin")
            .authType(SshAuthType.SSH_KEY)
            .privateKeyPath("/data/data/com.termux/files/home/.ssh/id_ed25519")
            .pinned(true)
            .lastUsedAtMillis(1_700_000_000_000L)
            .startupCommand("tmux attach || tmux")
            .proxyJump("jump.example.com")
            .keepAliveIntervalSeconds(30)
            .extraArgs("-A -v")
            .build();

        SshProfile passwordProfile = SshProfile.builder()
            .displayName("Staging DB")
            .host("10.20.30.40")
            .username("dbuser")
            .authType(SshAuthType.PASSWORD)
            .build();

        repo.saveProfileWithSecret(keyProfile, "key-passphrase-!@#");
        repo.saveProfileWithSecret(passwordProfile, "super-secret-password-99");

        // Invalid profile must be rejected by validation and never persisted
        SshProfile invalidProfile = SshProfile.builder()
            .host("")
            .username("nobody")
            .port(70000)
            .build();
        try {
            repo.saveProfile(invalidProfile);
            fail("Expected SshProfileValidationException");
        } catch (SshProfileValidationException expected) {
            assertFalse(expected.getValidationResult().isValid());
        }

        File profilesFile = new File(storageDir, SshProfileRepository.PROFILES_FILENAME);
        assertTrue(profilesFile.isFile());
        assertEquals(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(profilesFile.toPath())
        );

        String rawJson = new String(Files.readAllBytes(profilesFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(rawJson.contains("key-passphrase-!@#"));
        assertFalse(rawJson.contains("super-secret-password-99"));

        // Reload via a brand new repository instance over the same storage directory
        SshProfileRepository reloaded = new SshProfileRepository(storageDir, cipher);
        List<SshProfile> loaded = reloaded.getAllSorted();
        assertEquals(2, loaded.size());

        SshProfile reloadedKey = reloaded.getById(keyProfile.id);
        assertNotNull(reloadedKey);
        assertEquals(keyProfile, reloadedKey);
        assertEquals("key-passphrase-!@#", reloaded.getSecret(keyProfile.id));

        SshProfile reloadedPass = reloaded.getById(passwordProfile.id);
        assertNotNull(reloadedPass);
        assertEquals(passwordProfile, reloadedPass);
        assertEquals(SshProfile.DEFAULT_PORT, reloadedPass.port);
        assertEquals("super-secret-password-99", reloaded.getSecret(passwordProfile.id));

        // ~/.ssh/config must remain completely untouched
        assertEquals(
            originalSshConfig,
            new String(Files.readAllBytes(sshConfig.toPath()), StandardCharsets.UTF_8)
        );
    }

    @Test
    public void updatePreservesOrPurgesSecretsMarkUsedUpdatesTimestampDuplicateIsIndependentAndDeletePurges()
        throws Exception {
        File storageDir = tempFolder.newFolder("ssh-lifecycle");
        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );

        SshProfile original = SshProfile.builder()
            .displayName("App Server")
            .host("app.internal")
            .port(2200)
            .username("ubuntu")
            .authType(SshAuthType.PASSWORD)
            .pinned(true)
            .startupCommand("htop")
            .proxyJump("bastion")
            .keepAliveIntervalSeconds(45)
            .extraArgs("-C")
            .build();
        repo.saveProfileWithSecret(original, "initial-secret");

        // Metadata-only update, pin toggle, and markUsed must preserve existing secret
        repo.saveProfile(original.toBuilder().displayName("App Server Updated").build());
        assertEquals("initial-secret", repo.getSecret(original.id));

        SshProfile marked = repo.markUsed(original.id, 1_750_000_000_123L);
        assertNotNull(marked);
        assertEquals(1_750_000_000_123L, marked.lastUsedAtMillis);
        assertEquals("initial-secret", repo.getSecret(original.id));

        SshProfile unpinned = repo.setPinned(original.id, false);
        assertNotNull(unpinned);
        assertFalse(unpinned.pinned);
        assertEquals("initial-secret", repo.getSecret(original.id));

        // Duplicate creates an independent profile with a new ID and independent secret copy
        SshProfile duplicate = repo.duplicateProfile(original.id);
        assertNotEquals(original.id, duplicate.id);
        assertEquals("App Server Updated (Copy)", duplicate.displayName);
        assertEquals("app.internal", duplicate.host);
        assertEquals(2200, duplicate.port);
        assertEquals("ubuntu", duplicate.username);
        assertEquals(SshAuthType.PASSWORD, duplicate.authType);
        assertEquals("htop", duplicate.startupCommand);
        assertEquals("bastion", duplicate.proxyJump);
        assertEquals(45, duplicate.keepAliveIntervalSeconds);
        assertEquals("-C", duplicate.extraArgs);
        assertFalse(duplicate.pinned);
        assertEquals(0L, duplicate.lastUsedAtMillis);
        assertEquals("initial-secret", repo.getSecret(duplicate.id));

        // Modifying duplicate's secret leaves original's secret untouched
        repo.saveProfileWithSecret(duplicate, "copy-only-secret");
        assertEquals("copy-only-secret", repo.getSecret(duplicate.id));
        assertEquals("initial-secret", repo.getSecret(original.id));

        // Clearing secret via saveProfileWithSecret(..., null) purges secret file
        repo.saveProfileWithSecret(duplicate, null);
        assertFalse(repo.hasSecret(duplicate.id));
        assertNull(repo.getSecret(duplicate.id));
        assertEquals("initial-secret", repo.getSecret(original.id));

        // Deleting original removes profile metadata AND purges its encrypted secret, leaving duplicate intact
        assertTrue(repo.deleteProfile(original.id));
        assertNull(repo.getById(original.id));
        assertFalse(repo.hasSecret(original.id));
        assertNull(repo.getSecret(original.id));
        assertNotNull(repo.getById(duplicate.id));
        assertFalse(repo.deleteProfile("non-existent-id"));
    }

    @Test
    public void sortsPinnedFirstThenMostRecentlyUsedThenDisplayNameAndSearchesByNameHostOrUsername()
        throws Exception {
        File storageDir = tempFolder.newFolder("ssh-sort-search");
        SshProfileRepository repo = new SshProfileRepository(
            storageDir,
            AesGcmSshSecretCipher.withRandomKey()
        );

        SshProfile unpinnedRecent = SshProfile.builder()
            .id("u-recent")
            .displayName("Alpha Standard Recent")
            .host("alpha.example.com")
            .username("alice")
            .pinned(false)
            .lastUsedAtMillis(9_000L)
            .build();
        SshProfile pinnedOlder = SshProfile.builder()
            .id("p-older")
            .displayName("Zeta Pinned Older")
            .host("zeta.internal")
            .username("ops")
            .pinned(true)
            .lastUsedAtMillis(1_000L)
            .build();
        SshProfile pinnedRecent = SshProfile.builder()
            .id("p-recent")
            .displayName("Beta Pinned Recent")
            .host("beta.prod")
            .username("deploy")
            .pinned(true)
            .lastUsedAtMillis(5_000L)
            .build();
        SshProfile pinnedUnusedA = SshProfile.builder()
            .id("p-unused-a")
            .displayName("Aardvark Pinned Unused")
            .host("aardvark.local")
            .username("root")
            .pinned(true)
            .lastUsedAtMillis(0L)
            .build();
        SshProfile unpinnedUnusedB = SshProfile.builder()
            .id("u-unused-b")
            .displayName("bravo server")
            .host("10.0.0.5")
            .username("bob")
            .pinned(false)
            .lastUsedAtMillis(0L)
            .build();
        SshProfile unpinnedUnusedC = SshProfile.builder()
            .id("u-unused-c")
            .displayName("Charlie Server")
            .host("charlie.org")
            .username("charlie")
            .pinned(false)
            .lastUsedAtMillis(0L)
            .build();

        // Save in scrambled order
        repo.saveProfile(unpinnedUnusedC);
        repo.saveProfile(unpinnedRecent);
        repo.saveProfile(pinnedUnusedA);
        repo.saveProfile(pinnedOlder);
        repo.saveProfile(unpinnedUnusedB);
        repo.saveProfile(pinnedRecent);

        List<SshProfile> sorted = repo.getAllSorted();
        assertEquals(6, sorted.size());
        assertEquals("p-recent", sorted.get(0).id);
        assertEquals("p-older", sorted.get(1).id);
        assertEquals("p-unused-a", sorted.get(2).id);
        assertEquals("u-recent", sorted.get(3).id);
        assertEquals("u-unused-b", sorted.get(4).id);
        assertEquals("u-unused-c", sorted.get(5).id);

        // Blank search returns full pinned-first sorted list
        assertEquals(sorted, repo.search("   "));
        assertEquals(sorted, repo.search(null));

        // Search by display name (case-insensitive)
        List<SshProfile> byName = repo.search("PINNED");
        assertEquals(3, byName.size());
        assertEquals("p-recent", byName.get(0).id);
        assertEquals("p-older", byName.get(1).id);
        assertEquals("p-unused-a", byName.get(2).id);

        // Search by host
        List<SshProfile> byHost = repo.search("10.0.0");
        assertEquals(1, byHost.size());
        assertEquals("u-unused-b", byHost.get(0).id);

        // Search by username
        List<SshProfile> byUsername = repo.search("DEPLOY");
        assertEquals(1, byUsername.size());
        assertEquals("p-recent", byUsername.get(0).id);

        // Search matching across multiple fields keeps pinned-first order ("al" in zeta.internal, aardvark.local, alpha/alice)
        List<SshProfile> multiMatch = repo.search("al");
        assertEquals(3, multiMatch.size());
        assertEquals("p-older", multiMatch.get(0).id);
        assertEquals("p-unused-a", multiMatch.get(1).id);
        assertEquals("u-recent", multiMatch.get(2).id);
    }

    @Test
    public void createDefaultUsesAppPrivateNoBackupStorageAndCorruptJsonThrows() throws Exception {
        SshProfileRepository defaultRepo = SshProfileRepository.createDefault(
            RuntimeEnvironment.getApplication()
        );
        assertTrue(defaultRepo.getAllSorted().isEmpty());
        assertTrue(
            defaultRepo.getProfilesFile().getAbsolutePath().endsWith("/ssh/profiles.json")
        );

        File corruptDir = tempFolder.newFolder("ssh-corrupt");
        File corruptFile = new File(corruptDir, SshProfileRepository.PROFILES_FILENAME);
        Files.write(corruptFile.toPath(), "{not-valid-json".getBytes(StandardCharsets.UTF_8));
        SshProfileRepository corruptRepo = new SshProfileRepository(
            corruptDir,
            AesGcmSshSecretCipher.withRandomKey()
        );
        try {
            corruptRepo.getAllSorted();
            fail("Expected IOException when profiles.json is malformed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("JSON"));
        }
    }
}
