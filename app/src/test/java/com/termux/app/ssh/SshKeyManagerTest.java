package com.termux.app.ssh;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class SshKeyManagerTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void scansPrivateKeysExcludingPublicKeysMetadataConfigsAndDirectories() throws Exception {
        File missingDir = new File(tempFolder.getRoot(), "missing-ssh");
        assertTrue(SshKeyManager.scanPrivateKeys(missingDir).isEmpty());

        File sshDir = tempFolder.newFolder("dot-ssh");
        String[] filesToCreate = {
            "id_rsa",
            "id_ed25519",
            "work_server_key",
            "staging.pem",
            "id_rsa.pub",
            "id_ed25519.pub",
            "custom.PUB",
            "known_hosts",
            "known_hosts.old",
            "known_hosts2",
            "config",
            "authorized_keys",
            "authorized_keys2",
            ".DS_Store",
            ".id_ed25519.uuid.tmp",
            "agent.lock"
        };
        for (String name : filesToCreate) {
            Files.write(
                new File(sshDir, name).toPath(),
                "dummy-content".getBytes(StandardCharsets.UTF_8)
            );
        }
        assertTrue(new File(sshDir, "sockets").mkdirs());

        List<File> keys = SshKeyManager.scanPrivateKeys(sshDir);
        List<String> names = new ArrayList<>();
        for (File key : keys) {
            names.add(key.getName());
        }

        assertEquals(
            Arrays.asList("id_ed25519", "id_rsa", "staging.pem", "work_server_key"),
            names
        );
    }

    @Test
    public void importsKeyStreamCreating0700Dir0600FileAndResolvingCollisionsSafely() throws Exception {
        File homeDir = tempFolder.newFolder("import-home");
        File sshDir = new File(homeDir, ".ssh");
        assertFalse(sshDir.exists());

        byte[] firstBytes = "-----BEGIN OPENSSH PRIVATE KEY-----\nFIRST\n".getBytes(StandardCharsets.UTF_8);
        File imported = SshKeyManager.importPrivateKey(
            sshDir,
            "my_key",
            new ByteArrayInputStream(firstBytes)
        );
        assertEquals("my_key", imported.getName());
        assertEquals(sshDir.getAbsolutePath(), imported.getParentFile().getAbsolutePath());
        assertEquals(
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            ),
            Files.getPosixFilePermissions(sshDir.toPath())
        );
        assertEquals(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(imported.toPath())
        );
        assertEquals(new String(firstBytes, StandardCharsets.UTF_8), SshFsUtils.readUtf8(imported));

        // Collision on extensionless key -> my_key_1, my_key_2; original untouched
        byte[] secondBytes = "SECOND_KEY".getBytes(StandardCharsets.UTF_8);
        File importedSecond = SshKeyManager.importPrivateKey(
            sshDir,
            "my_key",
            new ByteArrayInputStream(secondBytes)
        );
        assertEquals("my_key_1", importedSecond.getName());
        assertEquals(new String(firstBytes, StandardCharsets.UTF_8), SshFsUtils.readUtf8(imported));
        assertEquals(new String(secondBytes, StandardCharsets.UTF_8), SshFsUtils.readUtf8(importedSecond));

        File importedThird = SshKeyManager.importPrivateKey(
            sshDir,
            "my_key",
            new ByteArrayInputStream("THIRD_KEY".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals("my_key_2", importedThird.getName());

        // Collision on key with extension -> preserves extension (prod_1.pem)
        File pem1 = SshKeyManager.importPrivateKey(
            sshDir,
            "prod.pem",
            new ByteArrayInputStream("PEM1".getBytes(StandardCharsets.UTF_8))
        );
        File pem2 = SshKeyManager.importPrivateKey(
            sshDir,
            "prod.pem",
            new ByteArrayInputStream("PEM2".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals("prod.pem", pem1.getName());
        assertEquals("prod_1.pem", pem2.getName());

        // Path traversal in SAF display name is sanitized into sshDir
        File traversed = SshKeyManager.importPrivateKey(
            sshDir,
            "../../etc/shadow_key",
            new ByteArrayInputStream("TRAVERSED".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals("shadow_key", traversed.getName());
        assertEquals(sshDir.getAbsolutePath(), traversed.getParentFile().getAbsolutePath());

        // Empty stream must fail without leaving an empty file behind
        int countBefore = sshDir.listFiles().length;
        try {
            SshKeyManager.importPrivateKey(
                sshDir,
                "empty_key",
                new ByteArrayInputStream(new byte[0])
            );
            fail("Expected IOException for empty key stream");
        } catch (IOException expected) {
            assertFalse(new File(sshDir, "empty_key").exists());
            assertEquals(countBefore, sshDir.listFiles().length);
        }
    }

    @Test
    public void importsKeyFromSafContentUriUsingDisplayNameAndFallbackAndRejectsUnreadableUri() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File sshDir = new File(tempFolder.newFolder("saf-home"), ".ssh");

        File backingSource = tempFolder.newFile("source-doc-key");
        String expectedContent = "-----BEGIN OPENSSH PRIVATE KEY-----\nSAF_BODY\n";
        Files.write(backingSource.toPath(), expectedContent.getBytes(StandardCharsets.UTF_8));

        ContentProvider fakeDocumentsProvider = new ContentProvider() {
            @Override
            public boolean onCreate() {
                return true;
            }

            @Nullable
            @Override
            public Cursor query(
                    @NonNull Uri uri,
                    @Nullable String[] projection,
                    @Nullable String selection,
                    @Nullable String[] selectionArgs,
                    @Nullable String sortOrder) {
                MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
                cursor.addRow(new Object[]{"work_ed25519"});
                return cursor;
            }

            @Nullable
            @Override
            public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
                    throws FileNotFoundException {
                return ParcelFileDescriptor.open(backingSource, ParcelFileDescriptor.MODE_READ_ONLY);
            }

            @Nullable
            @Override
            public String getType(@NonNull Uri uri) {
                return "application/octet-stream";
            }

            @Nullable
            @Override
            public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
                return null;
            }

            @Override
            public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
                return 0;
            }

            @Override
            public int update(
                    @NonNull Uri uri,
                    @Nullable ContentValues values,
                    @Nullable String selection,
                    @Nullable String[] selectionArgs) {
                return 0;
            }
        };
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = "com.example.documents";
        providerInfo.exported = true;
        fakeDocumentsProvider.attachInfo(context, providerInfo);
        ShadowContentResolver.registerProviderInternal("com.example.documents", fakeDocumentsProvider);

        Uri providerUri = Uri.parse("content://com.example.documents/document/42");
        File importedFromProvider = SshKeyManager.importPrivateKey(context, providerUri, sshDir);
        assertEquals("work_ed25519", importedFromProvider.getName());
        assertEquals(expectedContent, SshFsUtils.readUtf8(importedFromProvider));
        assertEquals(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(importedFromProvider.toPath())
        );

        // Fallback when provider has no DISPLAY_NAME row -> uses URI last path segment
        ContentResolver resolver = context.getContentResolver();
        Uri streamUri = Uri.parse("content://com.example.stream/keys/fallback_key");
        Shadows.shadowOf(resolver).registerInputStream(
            streamUri,
            new ByteArrayInputStream("FALLBACK_STREAM_KEY".getBytes(StandardCharsets.UTF_8))
        );
        File importedFallback = SshKeyManager.importPrivateKey(context, streamUri, sshDir);
        assertEquals("fallback_key", importedFallback.getName());
        assertEquals("FALLBACK_STREAM_KEY", SshFsUtils.readUtf8(importedFallback));

        // Unreadable URI throws IOException cleanly
        Uri missingUri = Uri.parse("content://com.example.missing/nope");
        try {
            SshKeyManager.importPrivateKey(context, missingUri, sshDir);
            fail("Expected IOException when URI cannot be opened");
        } catch (IOException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    @Test
    public void generateEd25519KeyReportsMissingOpensshWhenBinaryAbsentOrNotExecutable() throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-no-ssh");
        File homeDir = tempFolder.newFolder("home-no-ssh");
        final boolean[] invoked = {false};

        SshKeygenRunner guardRunner = (pfx, home, bin, args) -> {
            invoked[0] = true;
            return SshKeygenRunner.ExecutionResult.success("", "");
        };

        assertFalse(SshKeyManager.isSshKeygenAvailable(prefixDir));
        SshKeyGenerationResult missingResult = SshKeyManager.generateEd25519Key(
            prefixDir,
            homeDir,
            "id_ed25519",
            "",
            "termux",
            guardRunner
        );
        assertEquals(SshKeyGenerationResult.Status.MISSING_OPENSSH, missingResult.getStatus());
        assertTrue(missingResult.isMissingOpenssh());
        assertFalse(missingResult.isSuccess());
        assertNull(missingResult.getPrivateKeyPath());
        assertNull(missingResult.getPublicKeyContent());
        assertFalse(invoked[0]);

        // Exists but not executable -> still MISSING_OPENSSH
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshKeygen = new File(binDir, "ssh-keygen");
        Files.write(sshKeygen.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(sshKeygen.setExecutable(false, false));

        assertFalse(SshKeyManager.isSshKeygenAvailable(prefixDir));
        SshKeyGenerationResult nonExecResult = SshKeyManager.generateEd25519Key(
            prefixDir,
            homeDir,
            "id_ed25519",
            "",
            null,
            guardRunner
        );
        assertEquals(SshKeyGenerationResult.Status.MISSING_OPENSSH, nonExecResult.getStatus());
        assertFalse(invoked[0]);
    }

    @Test
    public void generatesEd25519KeyViaRunnerEnforcesPermissionsAvoidsCollisionsAndReturnsPubKey() throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-ok");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshKeygen = new File(binDir, "ssh-keygen");
        Files.write(sshKeygen.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(sshKeygen.setExecutable(true, true));
        assertTrue(SshKeyManager.isSshKeygenAvailable(prefixDir));

        File homeDir = tempFolder.newFolder("home-keygen");
        File sshDir = new File(homeDir, ".ssh");
        assertTrue(sshDir.mkdirs());
        Files.write(new File(sshDir, "id_ed25519").toPath(), "EXISTING".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(sshDir, "other_key.pub").toPath(), "EXISTING_PUB".getBytes(StandardCharsets.UTF_8));

        final List<String> capturedArgs = new ArrayList<>();
        final File[] capturedBinary = new File[1];
        final String expectedPub = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITestKeyForUnitTests termux-mobile";

        SshKeygenRunner fakeRunner = (pfx, home, bin, args) -> {
            capturedBinary[0] = bin;
            capturedArgs.clear();
            capturedArgs.addAll(args);
            String targetPath = args.get(args.indexOf("-f") + 1);
            File priv = new File(targetPath);
            File pub = new File(targetPath + ".pub");
            try {
                Files.write(priv.toPath(), "PRIVATE_ED25519".getBytes(StandardCharsets.UTF_8));
                Files.write(pub.toPath(), (expectedPub + "\n").getBytes(StandardCharsets.UTF_8));
                // Simulate loose umask permissions so SshKeyManager must tighten them
                Files.setPosixFilePermissions(priv.toPath(), EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OTHERS_READ
                ));
                Files.setPosixFilePermissions(pub.toPath(), EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OTHERS_WRITE
                ));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return SshKeygenRunner.ExecutionResult.success("Your identification has been saved.", "");
        };

        SshKeyGenerationResult result = SshKeyManager.generateEd25519Key(
            prefixDir,
            homeDir,
            null,
            "",
            "termux-mobile",
            fakeRunner
        );

        assertEquals(SshKeyGenerationResult.Status.SUCCESS, result.getStatus());
        assertTrue(result.isSuccess());
        assertFalse(result.isMissingOpenssh());
        assertEquals(sshKeygen.getAbsolutePath(), capturedBinary[0].getAbsolutePath());

        File expectedPriv = new File(sshDir, "id_ed25519_1");
        File expectedPubFile = new File(sshDir, "id_ed25519_1.pub");
        assertEquals(
            Arrays.asList("-t", "ed25519", "-f", expectedPriv.getAbsolutePath(), "-N", "", "-C", "termux-mobile"),
            capturedArgs
        );
        assertNotNull(result.getPrivateKeyFile());
        assertEquals(expectedPriv.getAbsolutePath(), result.getPrivateKeyPath());
        assertEquals(expectedPubFile.getAbsolutePath(), result.getPublicKeyFile().getAbsolutePath());
        assertEquals(expectedPub, result.getPublicKeyContent());
        assertEquals(expectedPub, SshKeyManager.readPublicKey(expectedPriv));

        // Verify strict permissions enforced: 0700 dir, 0600 private key, 0644 .pub
        assertEquals(
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            ),
            Files.getPosixFilePermissions(sshDir.toPath())
        );
        assertEquals(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(expectedPriv.toPath())
        );
        assertEquals(
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ
            ),
            Files.getPosixFilePermissions(expectedPubFile.toPath())
        );

        // Collision when only .pub exists -> selects other_key_1 without touching other_key.pub
        SshKeyGenerationResult pubCollisionResult = SshKeyManager.generateEd25519Key(
            prefixDir,
            homeDir,
            "other_key",
            "passphrase-123",
            null,
            fakeRunner
        );
        assertTrue(pubCollisionResult.isSuccess());
        assertEquals("other_key_1", pubCollisionResult.getPrivateKeyFile().getName());
        assertEquals("EXISTING_PUB", SshFsUtils.readUtf8(new File(sshDir, "other_key.pub")));
        assertEquals(
            Arrays.asList(
                "-t", "ed25519",
                "-f", new File(sshDir, "other_key_1").getAbsolutePath(),
                "-N", "passphrase-123"
            ),
            capturedArgs
        );

        // Runner failure returns FAILED status with stderr message
        SshKeyGenerationResult failed = SshKeyManager.generateEd25519Key(
            prefixDir,
            homeDir,
            "fail_key",
            "x",
            null,
            (pfx, home, bin, args) -> SshKeygenRunner.ExecutionResult.failure(1, "", "Saving key failed: passphrase too short")
        );
        assertEquals(SshKeyGenerationResult.Status.FAILED, failed.getStatus());
        assertFalse(failed.isSuccess());
        assertTrue(failed.getErrorMessage().contains("passphrase too short"));
    }

    @Test
    public void concurrentEd25519KeyGenerationSerializesRunnerAndAvoidsFilenameCollision() throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-conc");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshKeygen = new File(binDir, "ssh-keygen");
        Files.write(sshKeygen.toPath(), "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(sshKeygen.setExecutable(true, true));

        File homeDir = tempFolder.newFolder("home-conc");
        AtomicInteger activeRunners = new AtomicInteger(0);
        AtomicInteger maxConcurrentRunners = new AtomicInteger(0);
        CountDownLatch firstEnteredRunner = new CountDownLatch(1);

        SshKeygenRunner slowRunner = (pfx, home, bin, args) -> {
            int active = activeRunners.incrementAndGet();
            maxConcurrentRunners.accumulateAndGet(active, Math::max);
            firstEnteredRunner.countDown();
            try {
                Thread.sleep(60L);
                String targetPath = args.get(args.indexOf("-f") + 1);
                File priv = new File(targetPath);
                File pub = new File(targetPath + ".pub");
                if (priv.exists() || pub.exists()) {
                    return SshKeygenRunner.ExecutionResult.failure(1, "", "File already exists: " + targetPath);
                }
                Files.write(priv.toPath(), ("PRIV-" + priv.getName()).getBytes(StandardCharsets.UTF_8));
                Files.write(pub.toPath(), ("ssh-ed25519 AAAA-" + priv.getName() + "\n").getBytes(StandardCharsets.UTF_8));
                return SshKeygenRunner.ExecutionResult.success("ok", "");
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                activeRunners.decrementAndGet();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SshKeyGenerationResult> first = pool.submit(() -> SshKeyManager.generateEd25519Key(
                prefixDir, homeDir, "id_ed25519", "", null, slowRunner));
            assertTrue(firstEnteredRunner.await(5, TimeUnit.SECONDS));
            Future<SshKeyGenerationResult> second = pool.submit(() -> SshKeyManager.generateEd25519Key(
                prefixDir, homeDir, "id_ed25519", "", null, slowRunner));

            SshKeyGenerationResult r1 = first.get(5, TimeUnit.SECONDS);
            SshKeyGenerationResult r2 = second.get(5, TimeUnit.SECONDS);

            assertEquals(1, maxConcurrentRunners.get());
            assertTrue("First keygen failed: " + r1.getErrorMessage(), r1.isSuccess());
            assertTrue("Second keygen failed: " + r2.getErrorMessage(), r2.isSuccess());
            assertEquals("id_ed25519", r1.getPrivateKeyFile().getName());
            assertEquals("id_ed25519_1", r2.getPrivateKeyFile().getName());
        } finally {
            pool.shutdownNow();
        }
    }
}
