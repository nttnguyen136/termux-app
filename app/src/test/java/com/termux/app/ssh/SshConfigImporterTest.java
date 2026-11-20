package com.termux.app.ssh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SshConfigImporterTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void parsesHostBlocksExpandingTildeSkippingWildcardsAndMatchWithoutModifyingConfig()
            throws Exception {
        File homeDir = tempFolder.newFolder("ssh-config-home");
        File dotSsh = new File(homeDir, ".ssh");
        assertTrue(dotSsh.mkdirs());
        File configFile = new File(dotSsh, "config");

        // Non-existent config returns empty and does not create the file
        assertTrue(SshConfigImporter.parse(configFile, homeDir, Collections.emptyList()).isEmpty());
        assertFalse(configFile.exists());

        String configContent =
            "# Global user ssh config\n" +
            "Host *\n" +
            "  ServerAliveInterval 60\n" +
            "  User globalignored\n" +
            "\n" +
            "Host prod\n" +
            "  HostName = prod.example.com # primary web\n" +
            "  User deploy\n" +
            "  Port=2222\n" +
            "  IdentityFile ~/.ssh/id_ed25519\n" +
            "  ProxyJump bastion.example.com:2200\n" +
            "  ServerAliveInterval 30\n" +
            "\n" +
            "Match host prod exec \"test -f /tmp/flag\"\n" +
            "  User match_should_not_override\n" +
            "  Port 9999\n" +
            "\n" +
            "Host *.internal.net dev-?\n" +
            "  User wildcardonly\n" +
            "  Port 2201\n" +
            "\n" +
            "Host staging *.staging.local\n" +
            "  hostname 10.10.0.50\n" +
            "  user ops\n" +
            "  identityfile \"~/.ssh/my work key\"\n" +
            "\n" +
            "Host web1 web2\n" +
            "  User www\n" +
            "  Port 2202\n" +
            "\n" +
            "Host db-primary db-alias\n" +
            "  HostName 10.20.30.40\n" +
            "  User postgres\n";

        Files.write(configFile.toPath(), configContent.getBytes(StandardCharsets.UTF_8));

        List<SshConfigImporter.ImportCandidate> parsed =
            SshConfigImporter.parse(configFile, homeDir, Collections.emptyList());

        assertEquals(5, parsed.size());

        SshProfile prod = parsed.get(0).getProfile();
        assertEquals("prod", parsed.get(0).getAlias());
        assertEquals("prod", prod.displayName);
        assertEquals("prod.example.com", prod.host);
        assertEquals("deploy", prod.username);
        assertEquals(2222, prod.port);
        assertEquals(SshAuthType.SSH_KEY, prod.authType);
        assertEquals(new File(homeDir, ".ssh/id_ed25519").getAbsolutePath(), prod.privateKeyPath);
        assertEquals("bastion.example.com:2200", prod.proxyJump);
        assertEquals(30, prod.keepAliveIntervalSeconds);

        SshProfile staging = parsed.get(1).getProfile();
        assertEquals("staging", staging.displayName);
        assertEquals("10.10.0.50", staging.host);
        assertEquals("ops", staging.username);
        assertEquals(SshProfile.DEFAULT_PORT, staging.port);
        assertEquals(new File(homeDir, ".ssh/my work key").getAbsolutePath(), staging.privateKeyPath);

        SshProfile web1 = parsed.get(2).getProfile();
        assertEquals("web1", web1.displayName);
        assertEquals("web1", web1.host);
        assertEquals("www", web1.username);
        assertEquals(2202, web1.port);

        SshProfile web2 = parsed.get(3).getProfile();
        assertEquals("web2", web2.displayName);
        assertEquals("web2", web2.host);
        assertEquals("www", web2.username);
        assertEquals(2202, web2.port);

        SshProfile db = parsed.get(4).getProfile();
        assertEquals("db-primary", db.displayName);
        assertEquals("10.20.30.40", db.host);
        assertEquals("postgres", db.username);

        // Read-only invariant: ~/.ssh/config must remain byte-for-byte identical
        assertEquals(
            configContent,
            new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8)
        );
    }

    @Test
    public void flagsDuplicatesAgainstExistingProfilesAndHandlesInvalidDirectiveFallbacks()
            throws Exception {
        File homeDir = tempFolder.newFolder("dup-home");
        File configFile = new File(homeDir, "config");

        SshProfile existingEndpoint = SshProfile.builder()
            .id("existing-1")
            .displayName("Production Web")
            .host("prod.example.com")
            .port(2222)
            .username("deploy")
            .build();
        SshProfile existingNamed = SshProfile.builder()
            .id("existing-2")
            .displayName("Staging")
            .host("other-host.internal")
            .port(22)
            .username("root")
            .build();
        List<SshProfile> existingProfiles = Arrays.asList(existingEndpoint, existingNamed);

        String content =
            "Host prod-import\n" +
            "  HostName PROD.EXAMPLE.COM\n" +
            "  Port 2222\n" +
            "  User deploy\n" +
            "\n" +
            "Host staging\n" +
            "  HostName new-staging.example.com\n" +
            "  User ops\n" +
            "\n" +
            "Host prod-other-user\n" +
            "  HostName prod.example.com\n" +
            "  Port 2222\n" +
            "  User readonly\n" +
            "\n" +
            "Host prod-other-port\n" +
            "  HostName prod.example.com\n" +
            "  Port 22\n" +
            "  User deploy\n" +
            "\n" +
            "Host legacy-defaults\n" +
            "  HostName legacy.local\n" +
            "  Port 99999\n" +
            "  ServerAliveInterval -10\n" +
            "  ProxyJump none\n";

        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        List<SshConfigImporter.ImportCandidate> candidates =
            SshConfigImporter.parse(configFile, homeDir, existingProfiles);

        assertEquals(5, candidates.size());

        // 0: Matches host+port+username -> duplicate of existingEndpoint
        assertTrue(candidates.get(0).isDuplicate());
        assertEquals(existingEndpoint, candidates.get(0).getMatchingExistingProfile());
        assertTrue(candidates.get(0).hasUsername());

        // 1: Matches display name case-insensitively -> duplicate of existingNamed
        assertTrue(candidates.get(1).isDuplicate());
        assertEquals(existingNamed, candidates.get(1).getMatchingExistingProfile());

        // 2: Different username -> NOT duplicate
        assertFalse(candidates.get(2).isDuplicate());
        assertNull(candidates.get(2).getMatchingExistingProfile());

        // 3: Different port -> NOT duplicate
        assertFalse(candidates.get(3).isDuplicate());
        assertNull(candidates.get(3).getMatchingExistingProfile());

        // 4: Missing User + invalid port/keepAlive + ProxyJump none -> clean safe defaults
        SshConfigImporter.ImportCandidate legacy = candidates.get(4);
        assertFalse(legacy.isDuplicate());
        assertFalse(legacy.hasUsername());
        assertEquals("", legacy.getProfile().username);
        assertEquals(SshProfile.DEFAULT_PORT, legacy.getProfile().port);
        assertEquals(0, legacy.getProfile().keepAliveIntervalSeconds);
        assertEquals("", legacy.getProfile().proxyJump);
        assertFalse(SshConfigImporter.isDuplicate(legacy.getProfile(), existingEndpoint));
    }

    @Test
    public void expandsMultiAliasHostBlockWhenHostNameContainsLowercasePercentHToken() {
        File homeDir = tempFolder.getRoot();
        String content =
            "Host web1 *.ignored web2\n" +
            "  HostName %h.internal\n" +
            "  User www\n" +
            "  Port 2202\n" +
            "\n" +
            "Host literal1 literal2\n" +
            "  HostName %H.internal\n" +
            "  User ops\n";

        List<SshConfigImporter.ImportCandidate> candidates =
            SshConfigImporter.parseContent(content, homeDir, Collections.emptyList());

        assertEquals(3, candidates.size());

        SshProfile web1 = candidates.get(0).getProfile();
        assertEquals("web1", candidates.get(0).getAlias());
        assertEquals("web1", web1.displayName);
        assertEquals("web1.internal", web1.host);
        assertEquals("www", web1.username);
        assertEquals(2202, web1.port);

        SshProfile web2 = candidates.get(1).getProfile();
        assertEquals("web2", candidates.get(1).getAlias());
        assertEquals("web2", web2.displayName);
        assertEquals("web2.internal", web2.host);
        assertEquals("www", web2.username);
        assertEquals(2202, web2.port);

        SshProfile literal = candidates.get(2).getProfile();
        assertEquals("literal1", candidates.get(2).getAlias());
        assertEquals("%H.internal", literal.host);
    }

    @Test
    public void inheritsWildcardAndGlobalHostStarDefaultsWithoutEmittingWildcards() {
        File homeDir = tempFolder.getRoot();
        String content =
            "Host *\n" +
            "  ServerAliveInterval 45\n" +
            "  User top_default_user\n" +
            "\n" +
            "Host app-*\n" +
            "  User app_deploy\n" +
            "  ProxyJump app-bastion.internal\n" +
            "\n" +
            "Host app-1\n" +
            "  HostName 10.0.1.10\n" +
            "\n" +
            "Host app-custom\n" +
            "  HostName 10.0.1.20\n" +
            "  User override_user\n" +
            "  Port 2222\n" +
            "  ServerAliveInterval 15\n" +
            "\n" +
            "Host password-box\n" +
            "  HostName 10.0.2.30\n" +
            "  PubkeyAuthentication no\n" +
            "\n" +
            "Host * !app-1\n" +
            "  Port 2200\n" +
            "\n" +
            "Host *\n" +
            "  User bottom_default_user\n" +
            "  IdentityFile ~/.ssh/id_fallback\n";

        List<SshConfigImporter.ImportCandidate> candidates =
            SshConfigImporter.parseContent(content, homeDir, Collections.emptyList());

        // Only concrete hosts are emitted; Host *, Host app-*, and Host * !app-1 are never standalone candidates.
        assertEquals(3, candidates.size());

        // app-1: inherits User/ProxyJump from app-*, ServerAliveInterval from Host *, IdentityFile from bottom Host *,
        // and skips Port 2200 due to !app-1 negation (stays default 22).
        SshProfile app1 = candidates.get(0).getProfile();
        assertEquals("app-1", app1.displayName);
        assertEquals("10.0.1.10", app1.host);
        assertEquals("app_deploy", app1.username);
        assertEquals("app-bastion.internal", app1.proxyJump);
        assertEquals(SshProfile.DEFAULT_PORT, app1.port);
        assertEquals(45, app1.keepAliveIntervalSeconds);
        assertEquals(new File(homeDir, ".ssh/id_fallback").getAbsolutePath(), app1.privateKeyPath);
        assertEquals(SshAuthType.SSH_KEY, app1.authType);

        // app-custom: keeps explicit User/Port/ServerAliveInterval while inheriting ProxyJump & IdentityFile.
        SshProfile appCustom = candidates.get(1).getProfile();
        assertEquals("override_user", appCustom.username);
        assertEquals(2222, appCustom.port);
        assertEquals(15, appCustom.keepAliveIntervalSeconds);
        assertEquals("app-bastion.internal", appCustom.proxyJump);
        assertEquals(new File(homeDir, ".ssh/id_fallback").getAbsolutePath(), appCustom.privateKeyPath);

        // password-box: inherits User from top Host *, Port 2200 from Host * !app-1, and switches to PASSWORD on PubkeyAuthentication no.
        SshProfile passwordBox = candidates.get(2).getProfile();
        assertEquals("top_default_user", passwordBox.username);
        assertEquals(2200, passwordBox.port);
        assertEquals(45, passwordBox.keepAliveIntervalSeconds);
        assertEquals(SshAuthType.PASSWORD, passwordBox.authType);
        assertEquals("", passwordBox.privateKeyPath);
    }
}
