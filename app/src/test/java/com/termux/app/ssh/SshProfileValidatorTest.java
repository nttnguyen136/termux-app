package com.termux.app.ssh;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SshProfileValidatorTest {

    @Test
    public void rejectsMissingHostAndUsernameInvalidPortAndNegativeKeepAlive() {
        SshProfile validDefaultKey = SshProfile.builder()
            .host("192.168.1.50")
            .username("ubuntu")
            .port(1)
            .keepAliveIntervalSeconds(0)
            .build();
        SshValidationResult validResult = SshProfileValidator.validate(validDefaultKey);
        assertTrue(validResult.isValid());
        assertTrue(validResult.getErrors().isEmpty());
        assertNull(validResult.getFirstErrorMessage());

        SshProfile validMaxPort = SshProfile.builder()
            .host("edge.example.com")
            .username("root")
            .port(65535)
            .keepAliveIntervalSeconds(60)
            .build();
        assertTrue(SshProfileValidator.validate(validMaxPort).isValid());

        SshProfile missingHost = SshProfile.builder()
            .host("   ")
            .username("ubuntu")
            .build();
        SshValidationResult missingHostRes = SshProfileValidator.validate(missingHost);
        assertFalse(missingHostRes.isValid());
        assertNotNull(missingHostRes.getFieldError(SshValidationResult.Field.HOST));
        assertTrue(missingHostRes.getFieldError(SshValidationResult.Field.HOST).contains("Host"));

        SshProfile missingUser = SshProfile.builder()
            .host("example.com")
            .username("")
            .build();
        SshValidationResult missingUserRes = SshProfileValidator.validate(missingUser);
        assertFalse(missingUserRes.isValid());
        assertNotNull(missingUserRes.getFieldError(SshValidationResult.Field.USERNAME));
        assertTrue(missingUserRes.getFieldError(SshValidationResult.Field.USERNAME).contains("Username"));

        SshProfile zeroPort = SshProfile.builder()
            .host("example.com")
            .username("alice")
            .port(0)
            .build();
        SshValidationResult zeroPortRes = SshProfileValidator.validate(zeroPort);
        assertFalse(zeroPortRes.isValid());
        assertNotNull(zeroPortRes.getFieldError(SshValidationResult.Field.PORT));
        assertTrue(zeroPortRes.getFieldError(SshValidationResult.Field.PORT).contains("1"));
        assertTrue(zeroPortRes.getFieldError(SshValidationResult.Field.PORT).contains("65535"));

        SshProfile highPort = SshProfile.builder()
            .host("example.com")
            .username("alice")
            .port(65536)
            .build();
        assertFalse(SshProfileValidator.validate(highPort).isValid());
        assertNotNull(SshProfileValidator.validate(highPort).getFieldError(SshValidationResult.Field.PORT));

        SshProfile negativeKeepAlive = SshProfile.builder()
            .host("example.com")
            .username("alice")
            .keepAliveIntervalSeconds(-5)
            .build();
        SshValidationResult keepAliveRes = SshProfileValidator.validate(negativeKeepAlive);
        assertFalse(keepAliveRes.isValid());
        assertNotNull(keepAliveRes.getFieldError(SshValidationResult.Field.KEEP_ALIVE_INTERVAL));

        SshProfile multiInvalid = SshProfile.builder()
            .host("")
            .username("bad user")
            .port(-1)
            .keepAliveIntervalSeconds(-1)
            .build();
        SshValidationResult multiRes = SshProfileValidator.validate(multiInvalid);
        assertFalse(multiRes.isValid());
        assertEquals(4, multiRes.getErrors().size());
    }

    @Test
    public void rejectsOptionInjectionLeadingDashAndMultilineFields() {
        SshProfile dashHost = SshProfile.builder()
            .host("-oProxyCommand=evil")
            .username("ubuntu")
            .build();
        SshValidationResult dashHostRes = SshProfileValidator.validate(dashHost);
        assertFalse(dashHostRes.isValid());
        assertNotNull(dashHostRes.getFieldError(SshValidationResult.Field.HOST));

        SshProfile dashUser = SshProfile.builder()
            .host("example.com")
            .username("-admin")
            .build();
        SshValidationResult dashUserRes = SshProfileValidator.validate(dashUser);
        assertFalse(dashUserRes.isValid());
        assertNotNull(dashUserRes.getFieldError(SshValidationResult.Field.USERNAME));

        SshProfile multilineJump = SshProfile.builder()
            .host("example.com")
            .username("ubuntu")
            .proxyJump("jump1\njump2")
            .privateKeyPath("/tmp/key\r\nline2")
            .build();
        SshValidationResult multilineRes = SshProfileValidator.validate(multilineJump);
        assertFalse(multilineRes.isValid());
        assertNotNull(multilineRes.getFieldError(SshValidationResult.Field.PROXY_JUMP));
        assertNotNull(multilineRes.getFieldError(SshValidationResult.Field.PRIVATE_KEY_PATH));
    }
}
