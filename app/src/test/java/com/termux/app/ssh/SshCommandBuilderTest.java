package com.termux.app.ssh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshCommandBuilderTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void checksOpenSshBinaryExecutableAndBuildsMinimalCommandWithDefaultAcceptNewHostKey() throws Exception {
        File prefixDir = tempFolder.newFolder("prefix");
        assertFalse(SshCommandBuilder.isSshAvailable(prefixDir));

        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshDirInsteadOfFile = new File(binDir, "ssh");
        assertTrue(sshDirInsteadOfFile.mkdirs());
        assertFalse(SshCommandBuilder.isSshAvailable(prefixDir));
        assertTrue(sshDirInsteadOfFile.delete());

        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(false, false));
        assertFalse(SshCommandBuilder.isSshAvailable(prefixDir));

        assertTrue(sshFile.setExecutable(true, true));
        assertTrue(SshCommandBuilder.isSshAvailable(prefixDir));

        SshProfile profile = SshProfile.builder()
            .displayName("Web Prod")
            .host("web.example.com")
            .username("deploy")
            .build();

        SshCommandSpec spec = SshCommandBuilder.build(profile, prefixDir);
        assertEquals(sshFile.getAbsolutePath(), spec.executablePath);
        assertEquals("Web Prod", spec.sessionTitle);
        assertEquals(
            Arrays.asList("-o", "StrictHostKeyChecking=accept-new", "deploy@web.example.com"),
            spec.arguments
        );
    }

    @Test
    public void buildsFlagsForPortKeyKeepAliveAndProxyJumpAndOmitsKeyFlagsForPasswordAuth() throws Exception {
        File prefixDir = tempFolder.newFolder("prefix-flags");

        SshProfile keyProfile = SshProfile.builder()
            .displayName("Bastion")
            .host("10.0.0.10")
            .port(2222)
            .username("admin")
            .authType(SshAuthType.SSH_KEY)
            .privateKeyPath("/home/.ssh/id_ed25519")
            .keepAliveIntervalSeconds(30)
            .proxyJump("jump@edge.example.com:2200")
            .build();

        SshCommandSpec keySpec = SshCommandBuilder.build(keyProfile, prefixDir);
        assertEquals(
            Arrays.asList(
                "-o", "StrictHostKeyChecking=accept-new",
                "-p", "2222",
                "-i", "/home/.ssh/id_ed25519",
                "-o", "IdentitiesOnly=yes",
                "-o", "ServerAliveInterval=30",
                "-J", "jump@edge.example.com:2200",
                "admin@10.0.0.10"
            ),
            keySpec.arguments
        );

        SshProfile passwordWithStaleKey = SshProfile.builder()
            .host("db.internal")
            .port(22)
            .username("postgres")
            .authType(SshAuthType.PASSWORD)
            .privateKeyPath("/home/.ssh/stale_key")
            .build();

        SshCommandSpec passDefaultPort = SshCommandBuilder.build(passwordWithStaleKey, prefixDir);
        assertEquals(
            Arrays.asList("-o", "StrictHostKeyChecking=accept-new", "postgres@db.internal"),
            passDefaultPort.arguments
        );

        SshCommandSpec passForcedPort = SshCommandBuilder.build(passwordWithStaleKey, prefixDir, true);
        assertEquals(
            Arrays.asList(
                "-o", "StrictHostKeyChecking=accept-new",
                "-p", "22",
                "postgres@db.internal"
            ),
            passForcedPort.arguments
        );
    }

    @Test
    public void parsesQuotedExtraArgsAndAppendsStartupCommandWithConditionalTtyFlag() {
        SshProfile withStartup = SshProfile.builder()
            .host("dev.example.com")
            .username("alice")
            .extraArgs("-A -o 'ProxyCommand=nc -x 127.0.0.1:1080 %h %p' -o \"LocalCommand=echo \\\"hi\\\"\" -i /my\\ path/id")
            .startupCommand("tmux attach || tmux")
            .build();

        assertEquals(
            Arrays.asList(
                "-o", "StrictHostKeyChecking=accept-new",
                "-A",
                "-o", "ProxyCommand=nc -x 127.0.0.1:1080 %h %p",
                "-o", "LocalCommand=echo \"hi\"",
                "-i", "/my path/id",
                "-t",
                "alice@dev.example.com",
                "tmux attach || tmux"
            ),
            SshCommandBuilder.buildArguments(withStartup)
        );

        SshProfile userDisabledTty = withStartup.toBuilder()
            .extraArgs("-T -v")
            .startupCommand("uptime")
            .build();
        assertEquals(
            Arrays.asList(
                "-o", "StrictHostKeyChecking=accept-new",
                "-T", "-v",
                "alice@dev.example.com",
                "uptime"
            ),
            SshCommandBuilder.buildArguments(userDisabledTty)
        );

        SshProfile userForcedTty = withStartup.toBuilder()
            .extraArgs("-At")
            .build();
        assertEquals(
            Arrays.asList(
                "-o", "StrictHostKeyChecking=accept-new",
                "-At",
                "alice@dev.example.com",
                "tmux attach || tmux"
            ),
            SshCommandBuilder.buildArguments(userForcedTty)
        );

        SshProfile valueLooksLikeFlag = withStartup.toBuilder()
            .extraArgs("-i /tmp/key-T -o StrictHostKeyChecking=no")
            .build();
        assertEquals(
            Arrays.asList(
                "-i", "/tmp/key-T",
                "-o", "StrictHostKeyChecking=no",
                "-t",
                "alice@dev.example.com",
                "tmux attach || tmux"
            ),
            SshCommandBuilder.buildArguments(valueLooksLikeFlag)
        );

        SshProfile badQuotes = withStartup.toBuilder()
            .extraArgs("-o 'unclosed")
            .build();
        try {
            SshCommandBuilder.buildArguments(badQuotes);
            org.junit.Assert.fail("Expected IllegalArgumentException for unclosed quote");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("quote"));
        }
    }

    @Test
    public void buildsInstallAndConnectShellCommandAndHandlesPkgSuccessAndFailure() throws Exception {
        File shBin = new File("/usr/bin/sh");
        org.junit.Assume.assumeTrue("Requires /usr/bin/sh", shBin.canExecute());

        File prefixDir = tempFolder.newFolder("prefix-install");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File tmpDir = new File(prefixDir, "tmp");

        SshProfile profile = SshProfile.builder()
            .displayName("Prod Box")
            .host("prod.internal")
            .username("ops")
            .port(2222)
            .startupCommand("tmux attach || tmux")
            .build();
        SshCommandSpec spec = SshCommandBuilder.build(profile, prefixDir);

        String simpleCmd = SshCommandBuilder.buildInstallOpenSshAndConnectCommand(spec, null);
        assertTrue(simpleCmd.startsWith("pkg install -y openssh && exec "));
        assertTrue(simpleCmd.contains("'ops@prod.internal'"));
        assertTrue(simpleCmd.contains("'tmux attach || tmux'"));

        SshAskPassManager askPassManager = new SshAskPassManager(tmpDir);
        SshAskPassManager.PreparedAskPass failAskPass = askPassManager.createSingleUseHelper("s3cr3t");
        String failShellCmd = SshCommandBuilder.buildInstallOpenSshAndConnectCommand(spec, failAskPass);

        File pkgBin = new File(binDir, "pkg");
        java.nio.file.Files.write(
            pkgBin.toPath(),
            "#!/usr/bin/sh\nexit 42\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        assertTrue(pkgBin.setExecutable(true, true));

        ProcessBuilder failPb = new ProcessBuilder("/usr/bin/sh", "-c", failShellCmd);
        failPb.environment().put("PATH", binDir.getAbsolutePath() + ":/usr/bin:/bin");
        failPb.environment().putAll(failAskPass.toEnvironment());
        assertEquals(42, failPb.start().waitFor());
        assertFalse(failAskPass.secretFile.exists());
        assertFalse(failAskPass.scriptFile.exists());

        File sshOutLog = tempFolder.newFile("ssh-invoked.log");
        File sshBin = new File(binDir, "ssh");
        java.nio.file.Files.write(
            sshBin.toPath(),
            ("#!/usr/bin/sh\nprintf '%s\\n' \"$@\" > '" + sshOutLog.getAbsolutePath() + "'\nexit 0\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        assertTrue(sshBin.setExecutable(true, true));

        java.nio.file.Files.write(
            pkgBin.toPath(),
            "#!/usr/bin/sh\n[ \"$1\" = \"install\" ] && [ \"$2\" = \"-y\" ] && [ \"$3\" = \"openssh\" ] && exit 0\nexit 99\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        SshAskPassManager.PreparedAskPass okAskPass = askPassManager.createSingleUseHelper("pass");
        String okShellCmd = SshCommandBuilder.buildInstallOpenSshAndConnectCommand(spec, okAskPass);
        ProcessBuilder okPb = new ProcessBuilder("/usr/bin/sh", "-c", okShellCmd);
        okPb.environment().put("PATH", binDir.getAbsolutePath() + ":/usr/bin:/bin");
        okPb.environment().putAll(okAskPass.toEnvironment());
        assertEquals(0, okPb.start().waitFor());

        String loggedArgs = new String(
            java.nio.file.Files.readAllBytes(sshOutLog.toPath()),
            java.nio.charset.StandardCharsets.UTF_8
        );
        assertTrue(loggedArgs.contains("ops@prod.internal"));
        assertTrue(loggedArgs.contains("tmux attach || tmux"));
    }
}
