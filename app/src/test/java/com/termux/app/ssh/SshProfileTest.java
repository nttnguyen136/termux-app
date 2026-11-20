package com.termux.app.ssh;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SshProfileTest {

    @Test
    public void newProfileDefaultsToSshKeyPort22AndGeneratesUniqueIds() {
        SshProfile first = SshProfile.builder()
            .host("prod.example.com")
            .username("deploy")
            .build();
        SshProfile second = SshProfile.builder()
            .host("prod.example.com")
            .username("deploy")
            .build();

        assertNotNull(first.id);
        assertFalse(first.id.trim().isEmpty());
        assertNotEquals(first.id, second.id);

        assertEquals(SshAuthType.SSH_KEY, first.authType);
        assertEquals(22, first.port);
        assertEquals("prod.example.com", first.host);
        assertEquals("deploy", first.username);
        assertEquals("", first.displayName);
        assertEquals("", first.privateKeyPath);
        assertFalse(first.pinned);
        assertEquals(0L, first.lastUsedAtMillis);
        assertEquals("", first.startupCommand);
        assertEquals("", first.proxyJump);
        assertEquals(0, first.keepAliveIntervalSeconds);
        assertEquals("", first.extraArgs);
        assertEquals("deploy@prod.example.com", first.getEffectiveDisplayName());

        SshProfile named = first.toBuilder()
            .displayName("Production Web")
            .pinned(true)
            .build();
        assertEquals(first.id, named.id);
        assertEquals("Production Web", named.displayName);
        assertEquals("Production Web", named.getEffectiveDisplayName());
        assertTrue(named.pinned);
    }
}
