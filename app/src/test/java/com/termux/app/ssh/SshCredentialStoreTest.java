package com.termux.app.ssh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import javax.crypto.AEADBadTagException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SshCredentialStoreTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void encryptsDecryptsSecretWithUniqueIvPerWriteAndNeverStoresPlaintext() throws Exception {
        File secretsDir = tempFolder.newFolder("secrets");
        SshSecretCipher cipher = AesGcmSshSecretCipher.withRandomKey();
        SshCredentialStore store = new SshCredentialStore(secretsDir, cipher);

        String profileId = "profile-1";
        String secret = "p@ssphrase-with-unic0de-🔐!";

        assertFalse(store.hasSecret(profileId));
        assertNull(store.getSecret(profileId));

        store.saveSecret(profileId, secret);
        assertTrue(store.hasSecret(profileId));
        assertEquals(secret, store.getSecret(profileId));

        File[] filesFirst = secretsDir.listFiles();
        assertNotNull(filesFirst);
        assertEquals(1, filesFirst.length);
        byte[] firstCiphertext = Files.readAllBytes(filesFirst[0].toPath());
        assertFalse(
            new String(firstCiphertext, StandardCharsets.UTF_8).contains(secret)
        );

        // Re-encrypting the exact same secret must produce a fresh IV and different bytes on disk
        store.saveSecret(profileId, secret);
        byte[] secondCiphertext = Files.readAllBytes(filesFirst[0].toPath());
        assertFalse(Arrays.equals(firstCiphertext, secondCiphertext));
        assertEquals(secret, store.getSecret(profileId));
    }

    @Test
    public void rejectsTamperedCiphertextAndCrossProfileSwapAndEnforcesPurgeAndPermissions() throws Exception {
        File secretsDir = tempFolder.newFolder("secrets-security");
        SshSecretCipher cipher = AesGcmSshSecretCipher.withRandomKey();
        SshCredentialStore store = new SshCredentialStore(secretsDir, cipher);

        store.saveSecret("alpha", "alpha-password");
        store.saveSecret("beta", "beta-password");

        Set<PosixFilePermission> dirPerms = Files.getPosixFilePermissions(secretsDir.toPath());
        assertEquals(
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            ),
            dirPerms
        );

        File[] files = secretsDir.listFiles();
        assertNotNull(files);
        assertEquals(2, files.length);
        for (File f : files) {
            Set<PosixFilePermission> filePerms = Files.getPosixFilePermissions(f.toPath());
            assertEquals(
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                filePerms
            );
        }

        // Cross-profile file swap must fail GCM AAD authentication
        byte[] file0Bytes = Files.readAllBytes(files[0].toPath());
        byte[] file1Bytes = Files.readAllBytes(files[1].toPath());
        Files.write(files[0].toPath(), file1Bytes);
        Files.write(files[1].toPath(), file0Bytes);
        try {
            store.getSecret("alpha");
            fail("Expected AEADBadTagException on swapped encrypted secret file");
        } catch (GeneralSecurityException expected) {
            assertTrue(expected instanceof AEADBadTagException);
        }

        // Restore alpha and tamper with GCM auth tag -> must fail decryption
        store.saveSecret("alpha", "alpha-updated");
        assertEquals("alpha-updated", store.getSecret("alpha"));

        for (File f : files) {
            byte[] bytes = Files.readAllBytes(f.toPath());
            bytes[bytes.length - 1] ^= 0x01;
            Files.write(f.toPath(), bytes);
        }
        try {
            store.getSecret("alpha");
            fail("Expected AEADBadTagException when encrypted payload is tampered");
        } catch (GeneralSecurityException expected) {
            assertTrue(expected instanceof AEADBadTagException);
        }

        // Purge via deleteSecret and via empty save
        assertTrue(store.deleteSecret("alpha"));
        assertFalse(store.hasSecret("alpha"));
        assertNull(store.getSecret("alpha"));

        store.saveSecret("beta", "beta-restored");
        assertTrue(store.hasSecret("beta"));
        store.saveSecret("beta", "");
        assertFalse(store.hasSecret("beta"));
        assertNull(store.getSecret("beta"));
    }
}
