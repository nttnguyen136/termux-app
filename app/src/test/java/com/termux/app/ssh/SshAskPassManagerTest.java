package com.termux.app.ssh;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshAskPassManagerTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void creates0700HelperAnd0600SecretWithTermuxShebangPrintsArbitrarySecretOnceAndSelfDeletes()
        throws Exception {
        File shBin = new File("/usr/bin/sh");
        Assume.assumeTrue("Requires /usr/bin/sh", shBin.canExecute());

        File tmpDir = new File(tempFolder.getRoot(), "termux-tmp");
        SshAskPassManager manager = new SshAskPassManager(tmpDir);

        String trickySecret = "  p@ss'\"\\$HOME`id`-n -e \\n 🔐  ";
        SshAskPassManager.PreparedAskPass prepared = manager.createSingleUseHelper(trickySecret);

        assertTrue(tmpDir.isDirectory());
        assertEquals(
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            ),
            Files.getPosixFilePermissions(tmpDir.toPath())
        );
        assertEquals(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(prepared.secretFile.toPath())
        );
        assertEquals(
            EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            ),
            Files.getPosixFilePermissions(prepared.scriptFile.toPath())
        );
        assertTrue(prepared.scriptFile.canExecute());

        String scriptContent = new String(
            Files.readAllBytes(prepared.scriptFile.toPath()),
            StandardCharsets.UTF_8
        );
        assertTrue(scriptContent.startsWith("#!/data/data/com.termux/files/usr/bin/sh\n"));
        assertFalse("Plaintext secret must live in 0600 secretFile, not inside helper script",
            scriptContent.contains(trickySecret));

        Map<String, String> env = prepared.toEnvironment();
        assertEquals(prepared.scriptFile.getAbsolutePath(), env.get("SSH_ASKPASS"));
        assertEquals("force", env.get("SSH_ASKPASS_REQUIRE"));
        assertEquals(":0", env.get("DISPLAY"));
        assertEquals(3, env.size());

        // POSIX syntax check
        ExecResult syntaxCheck = runProcess(new String[] {
            "/usr/bin/sh", "-n", prepared.scriptFile.getAbsolutePath()
        });
        assertEquals("sh -n stderr: " + syntaxCheck.stderr, 0, syntaxCheck.exitCode);
        assertEquals("", syntaxCheck.stderr);

        // Execute helper script once (like OpenSSH invoking SSH_ASKPASS with a prompt arg)
        ExecResult runResult = runProcess(new String[] {
            "/usr/bin/sh", prepared.scriptFile.getAbsolutePath(), "user@host's password:"
        });
        assertEquals("sh stderr: " + runResult.stderr, 0, runResult.exitCode);
        assertEquals("", runResult.stderr);
        assertEquals(trickySecret, runResult.stdout);

        // Single-use self-cleanup: both secret file and script file must be deleted
        assertFalse(prepared.secretFile.exists());
        assertFalse(prepared.scriptFile.exists());
    }

    @Test
    public void cleanupDeletesBothFilesAndHandlesSpecialCharsInTempDirPath() throws Exception {
        File shBin = new File("/usr/bin/sh");
        Assume.assumeTrue("Requires /usr/bin/sh", shBin.canExecute());

        File weirdTmpDir = new File(tempFolder.getRoot(), "tmp dir's $special");
        SshAskPassManager manager = new SshAskPassManager(weirdTmpDir);

        SshAskPassManager.PreparedAskPass unused = manager.createSingleUseHelper("unused-secret");
        assertTrue(unused.secretFile.isFile());
        assertTrue(unused.scriptFile.isFile());
        unused.cleanup();
        assertFalse(unused.secretFile.exists());
        assertFalse(unused.scriptFile.exists());

        SshAskPassManager.PreparedAskPass prepared = manager.createSingleUseHelper("line1");
        ExecResult res = runProcess(new String[] {
            "/usr/bin/sh", prepared.scriptFile.getAbsolutePath()
        });
        assertEquals(0, res.exitCode);
        assertEquals("line1", res.stdout);
        assertFalse(prepared.secretFile.exists());
        assertFalse(prepared.scriptFile.exists());

        SshAskPassManager.PreparedAskPass byScriptPath = manager.createSingleUseHelper("by-path-secret");
        assertTrue(byScriptPath.secretFile.isFile());
        assertTrue(byScriptPath.scriptFile.isFile());
        SshAskPassManager.cleanupFilesForScriptPath(null);
        SshAskPassManager.cleanupFilesForScriptPath("");
        SshAskPassManager.cleanupFilesForScriptPath(byScriptPath.scriptFile.getAbsolutePath());
        assertFalse(byScriptPath.secretFile.exists());
        assertFalse(byScriptPath.scriptFile.exists());
    }

    private static ExecResult runProcess(String[] cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).start();
        byte[] out = readAll(p.getInputStream());
        byte[] err = readAll(p.getErrorStream());
        int code = p.waitFor();
        return new ExecResult(
            code,
            new String(out, StandardCharsets.UTF_8),
            new String(err, StandardCharsets.UTF_8)
        );
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static final class ExecResult {
        final int exitCode;
        final String stdout;
        final String stderr;

        ExecResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
