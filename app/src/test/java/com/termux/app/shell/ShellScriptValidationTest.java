package com.termux.app.shell;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Empirically executes {@code /usr/bin/sh} on {@code ~/.termux/motd.sh} and {@code /usr/bin/bash}
 * on the marker-delimited Bash fallback prompt block to validate syntax, 2-line banner output,
 * Nerd Font glyphs, git/no-git/unborn/dirty/detached states, exit code {@code ❯} colors,
 * {@code PROMPT_COMMAND} preservation, command-injection safety, and {@code set -e} safety.
 */
public class ShellScriptValidationTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testWelcomeBannerMotdScriptPosixSyntaxAndOutput() throws Exception {
        File shBin = new File("/usr/bin/sh");
        Assume.assumeTrue("Requires /usr/bin/sh", shBin.canExecute());

        File motdScript = tempFolder.newFile("motd.sh");
        ShellFsUtils.writeUtf8(motdScript, WelcomeBannerManager.DEFAULT_WELCOME_BANNER_SCRIPT);
        ShellFsUtils.chmod0700(motdScript);

        // 1. POSIX syntax check (sh -n)
        ExecResult syntaxCheck = runProcess(new String[] { "/usr/bin/sh", "-n", motdScript.getAbsolutePath() }, null, null);
        assertEquals("sh -n stderr: " + syntaxCheck.stderr, 0, syntaxCheck.exitCode);
        assertEquals("", syntaxCheck.stderr);

        // 2. Execute script via /usr/bin/sh
        ExecResult runResult = runProcess(new String[] { "/usr/bin/sh", motdScript.getAbsolutePath() }, null, null);
        assertEquals("sh stderr: " + runResult.stderr, 0, runResult.exitCode);
        assertEquals("", runResult.stderr);

        String[] lines = runResult.stdout.split("\n", -1);
        // Should be exactly 2 lines (plus trailing empty after final \n)
        assertEquals("Expected 2-line welcome banner output", 3, lines.length);
        assertEquals("", lines[2]);
        assertTrue(lines[0].contains("\uf120 Termux"));
        assertTrue(lines[0].contains("\uf0e7 Ready"));
        assertTrue(lines[1].contains("pkg install <pkg> \u00b7 pkg upgrade"));
    }

    @Test
    public void testBashPromptBlockSyntaxAndRuntimeScenarios() throws Exception {
        File bashBin = new File("/usr/bin/bash");
        Assume.assumeTrue("Requires /usr/bin/bash", bashBin.canExecute());

        File bashrc = tempFolder.newFile("bashrc");
        ShellFsUtils.writeUtf8(bashrc, ShellConfigManager.MANAGED_BASH_PROMPT_BLOCK + "\n");

        // 1. bash -n syntax validation
        ExecResult syntaxResult = runProcess(new String[] { "/usr/bin/bash", "-n", bashrc.getAbsolutePath() }, null, null);
        assertEquals("bash -n stderr: " + syntaxResult.stderr, 0, syntaxResult.exitCode);
        assertEquals("", syntaxResult.stderr);

        // 2. Non-git directory under set -e + exit 0 vs non-zero exit + PROMPT_COMMAND preservation
        File homeDir = tempFolder.newFolder("home");
        File deepDir = new File(homeDir, "projects/termux/sub");
        assertTrue(deepDir.mkdirs());

        String nonGitHarness =
            "set -e\n" +
            "export HOME='" + homeDir.getAbsolutePath() + "'\n" +
            "PROMPT_COMMAND='USER_HOOK_RAN=1'\n" +
            "source '" + bashrc.getAbsolutePath() + "'\n" +
            "source '" + bashrc.getAbsolutePath() + "'\n" + // idempotent double-source
            "cd \"$HOME\"\n" +
            "true\n" +
            "eval \"$PROMPT_COMMAND\"\n" +
            "echo \"HOME_PS1=$PS1\"\n" +
            "echo \"HOOK=$USER_HOOK_RAN\"\n" +
            "cd '" + deepDir.getAbsolutePath() + "'\n" +
            "(exit 42) || __termux_modern_prompt || EC=$?\n" +
            "echo \"FAIL_EC=$EC\"\n" +
            "echo \"DEEP_PS1=$PS1\"\n";

        ExecResult nonGitRes = runProcess(new String[] { "/usr/bin/bash", "-c", nonGitHarness }, homeDir, null);
        assertEquals("Non-git harness failed: " + nonGitRes.stderr, 0, nonGitRes.exitCode);
        assertEquals("", nonGitRes.stderr);
        assertTrue(nonGitRes.stdout.contains("HOOK=1"));
        assertTrue(nonGitRes.stdout.contains("HOME_PS1=\\[\\033[1;36m\\]\uf07c ~\\[\\033[0m\\] \\[\\033[1;32m\\]\u276f\\[\\033[0m\\] "));
        assertTrue(nonGitRes.stdout.contains("FAIL_EC=42"));
        assertTrue(nonGitRes.stdout.contains("…/termux/sub"));
        assertTrue(nonGitRes.stdout.contains("\\[\\033[1;31m\\]\u276f\\[\\033[0m\\]"));

        // 3. PATH hiding git -> zero stderr and clean prompt
        String noGitBinaryHarness =
            "set -e\n" +
            "export HOME='" + homeDir.getAbsolutePath() + "'\n" +
            "source '" + bashrc.getAbsolutePath() + "'\n" +
            "PATH=/nonexistent __termux_modern_prompt\n" +
            "echo \"NOGIT_PS1=$PS1\"\n";
        ExecResult noGitRes = runProcess(new String[] { "/usr/bin/bash", "-c", noGitBinaryHarness }, homeDir, null);
        assertEquals(0, noGitRes.exitCode);
        assertEquals("", noGitRes.stderr);
        assertFalse(noGitRes.stdout.contains("\ue0a0"));

        // 4. Command-injection safety on directory name containing $(...)
        File injectionDir = new File(homeDir, "$(echo INJECTED)");
        assertTrue(injectionDir.mkdirs());
        String injectionHarness =
            "set -e\n" +
            "export HOME='" + homeDir.getAbsolutePath() + "'\n" +
            "source '" + bashrc.getAbsolutePath() + "'\n" +
            "cd '" + injectionDir.getAbsolutePath() + "'\n" +
            "__termux_modern_prompt\n" +
            "EXPANDED=\"${PS1@P}\"\n" +
            "echo \"EXPANDED=$EXPANDED\"\n";
        ExecResult injRes = runProcess(new String[] { "/usr/bin/bash", "-c", injectionHarness }, homeDir, null);
        assertEquals(0, injRes.exitCode);
        assertTrue(injRes.stdout.contains("$(echo INJECTED)"));
    }

    @Test
    public void testBashPromptGitStatesCleanDirtyAndLongBranchTruncation() throws Exception {
        File bashBin = new File("/usr/bin/bash");
        File gitBin = new File("/usr/bin/git");
        Assume.assumeTrue("Requires /usr/bin/bash and /usr/bin/git", bashBin.canExecute() && gitBin.canExecute());

        File bashrc = tempFolder.newFile("bashrc-git");
        ShellFsUtils.writeUtf8(bashrc, ShellConfigManager.MANAGED_BASH_PROMPT_BLOCK + "\n");
        File repoDir = tempFolder.newFolder("repo");

        String gitHarness =
            "set -e\n" +
            "export HOME='" + repoDir.getParentFile().getAbsolutePath() + "'\n" +
            "source '" + bashrc.getAbsolutePath() + "'\n" +
            "cd '" + repoDir.getAbsolutePath() + "'\n" +
            "git init -q -b main\n" +
            "git config user.email 'test@termux.dev'\n" +
            "git config user.name 'Termux Test'\n" +
            "__termux_modern_prompt\n" +
            "echo \"UNBORN_CLEAN=$PS1\"\n" +
            "touch a.txt\n" +
            "__termux_modern_prompt\n" +
            "echo \"UNBORN_DIRTY=$PS1\"\n" +
            "git add a.txt && git commit -q -m 'init'\n" +
            "__termux_modern_prompt\n" +
            "echo \"COMMIT_CLEAN=$PS1\"\n" +
            "git checkout -q -b feature/very-long-branch-name-exceeding-twenty-chars\n" +
            "__termux_modern_prompt\n" +
            "echo \"LONG_BRANCH=$PS1\"\n";

        ExecResult gitRes = runProcess(new String[] { "/usr/bin/bash", "-c", gitHarness }, repoDir, null);
        assertEquals("Git harness stderr: " + gitRes.stderr, 0, gitRes.exitCode);
        assertTrue(gitRes.stdout.contains("\ue0a0 main\\[\\033[0m\\] "));
        assertTrue(gitRes.stdout.contains("\ue0a0 main\\[\\033[0m\\]\\[\\033[0;33m\\]*\\[\\033[0m\\]"));
        assertTrue(gitRes.stdout.contains("\ue0a0 feature/very-long-b…"));
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

    private static ExecResult runProcess(String[] cmd, File workDir, Map<String, String> extraEnv) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (workDir != null) {
            pb.directory(workDir);
        }
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
        }
        Process p = pb.start();
        byte[] outBytes = readAll(p.getInputStream());
        byte[] errBytes = readAll(p.getErrorStream());
        int code = p.waitFor();
        return new ExecResult(
            code,
            new String(outBytes, StandardCharsets.UTF_8),
            new String(errBytes, StandardCharsets.UTF_8)
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
}
