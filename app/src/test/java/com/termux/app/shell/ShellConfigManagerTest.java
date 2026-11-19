package com.termux.app.shell;

import android.content.Context;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ShellConfigManagerTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testInsertIntoMissingAndEmptyBashrc() throws Exception {
        File missingFile = new File(tempFolder.newFolder("missing-parent"), ".bashrc");
        assertFalse(ShellConfigManager.hasBashPromptBlock(missingFile));
        assertTrue(ShellConfigManager.insertOrUpdateBashPromptBlock(missingFile));
        assertTrue(ShellConfigManager.hasBashPromptBlock(missingFile));
        assertEquals(ShellConfigManager.MANAGED_BASH_PROMPT_BLOCK + "\n", ShellFsUtils.readUtf8(missingFile));
        assertFalse(ShellConfigManager.hasCustomBashrc(missingFile));

        File emptyFile = tempFolder.newFile("empty.bashrc");
        ShellFsUtils.writeUtf8(emptyFile, "");
        assertTrue(ShellConfigManager.insertOrUpdateBashPromptBlock(emptyFile));
        assertTrue(ShellConfigManager.hasBashPromptBlock(emptyFile));
        assertEquals(ShellConfigManager.MANAGED_BASH_PROMPT_BLOCK + "\n", ShellFsUtils.readUtf8(emptyFile));
    }

    @Test
    public void testInsertPreservesUserContentAndIsIdempotent() throws Exception {
        File bashrc = tempFolder.newFile("user.bashrc");
        String userContent = "# User custom aliases\nalias ll='ls -la'\nexport EDITOR=vim";
        ShellFsUtils.writeUtf8(bashrc, userContent);
        assertTrue(ShellConfigManager.hasCustomBashrc(bashrc));

        assertTrue(ShellConfigManager.insertOrUpdateBashPromptBlock(bashrc));
        assertTrue(ShellConfigManager.hasBashPromptBlock(bashrc));
        assertTrue(ShellConfigManager.hasCustomBashrc(bashrc));

        String afterFirstInsert = ShellFsUtils.readUtf8(bashrc);
        assertTrue(afterFirstInsert.startsWith(userContent + "\n\n" + ShellConfigManager.BASH_PROMPT_START_MARKER));

        // Second insert must be byte-for-byte idempotent
        assertTrue(ShellConfigManager.insertOrUpdateBashPromptBlock(bashrc));
        assertEquals(afterFirstInsert, ShellFsUtils.readUtf8(bashrc));

        // Removing the block restores exact user content (with trailing newline)
        assertTrue(ShellConfigManager.removeBashPromptBlock(bashrc));
        assertFalse(ShellConfigManager.hasBashPromptBlock(bashrc));
        assertEquals(userContent + "\n", ShellFsUtils.readUtf8(bashrc));
    }

    @Test
    public void testUpdateBlockBetweenUserLinesAndPreserveCrlfOutsideBlock() throws Exception {
        File bashrc = tempFolder.newFile("crlf.bashrc");
        String before = "export FOO=1\r\nalias gs='git status'\r\n";
        String oldBlock =
            ShellConfigManager.BASH_PROMPT_START_MARKER + "\n" +
            "PS1='old> '\n" +
            ShellConfigManager.BASH_PROMPT_END_MARKER + "\n";
        String after = "export BAR=2\r\n";

        ShellFsUtils.writeUtf8(bashrc, before + oldBlock + after);
        assertTrue(ShellConfigManager.hasBashPromptBlock(bashrc));
        assertTrue(ShellConfigManager.hasCustomBashrc(bashrc));

        assertTrue(ShellConfigManager.insertOrUpdateBashPromptBlock(bashrc));
        String updated = ShellFsUtils.readUtf8(bashrc);
        assertEquals(before + ShellConfigManager.MANAGED_BASH_PROMPT_BLOCK + "\n" + after, updated);

        // Removing the block from the middle leaves before + after intact
        assertTrue(ShellConfigManager.removeBashPromptBlock(bashrc));
        assertEquals(before + after, ShellFsUtils.readUtf8(bashrc));
    }

    @Test
    public void testMalformedMarkersRefuseDestructiveEdits() throws Exception {
        // 1. Unclosed start marker
        File unclosed = tempFolder.newFile("unclosed.bashrc");
        String unclosedContent = "alias a='b'\n" + ShellConfigManager.BASH_PROMPT_START_MARKER + "\nexport KEEP_ME=1\n";
        ShellFsUtils.writeUtf8(unclosed, unclosedContent);

        assertFalse(ShellConfigManager.hasBashPromptBlock(unclosed));
        assertTrue(ShellConfigManager.hasCustomBashrc(unclosed));
        assertFalse(ShellConfigManager.insertOrUpdateBashPromptBlock(unclosed));
        assertEquals(unclosedContent, ShellFsUtils.readUtf8(unclosed));
        assertFalse(ShellConfigManager.removeBashPromptBlock(unclosed));
        assertEquals(unclosedContent, ShellFsUtils.readUtf8(unclosed));

        // 2. Orphaned end marker
        File orphaned = tempFolder.newFile("orphaned.bashrc");
        String orphanedContent = "alias a='b'\n" + ShellConfigManager.BASH_PROMPT_END_MARKER + "\nexport KEEP_ME=2\n";
        ShellFsUtils.writeUtf8(orphaned, orphanedContent);

        assertFalse(ShellConfigManager.hasBashPromptBlock(orphaned));
        assertTrue(ShellConfigManager.hasCustomBashrc(orphaned));
        assertFalse(ShellConfigManager.insertOrUpdateBashPromptBlock(orphaned));
        assertEquals(orphanedContent, ShellFsUtils.readUtf8(orphaned));
        assertFalse(ShellConfigManager.removeBashPromptBlock(orphaned));
        assertEquals(orphanedContent, ShellFsUtils.readUtf8(orphaned));
    }

    @Test
    public void testRemoveBlockOnlyFileProducesEmptyFile() throws Exception {
        File bashrc = tempFolder.newFile("block-only.bashrc");
        assertTrue(ShellConfigManager.insertOrUpdateBashPromptBlock(bashrc));
        assertFalse(ShellConfigManager.hasCustomBashrc(bashrc));

        assertTrue(ShellConfigManager.removeBashPromptBlock(bashrc));
        assertEquals("", ShellFsUtils.readUtf8(bashrc));
        assertFalse(ShellConfigManager.hasBashPromptBlock(bashrc));
        assertFalse(ShellConfigManager.removeBashPromptBlock(bashrc));
    }

    @Test
    public void testHasCustomZshrcDetection() throws Exception {
        File zshrc = new File(tempFolder.newFolder("zsh-home"), ".zshrc");
        assertFalse(ShellConfigManager.hasCustomZshrc(zshrc));

        ShellFsUtils.writeUtf8(zshrc, ShellConfigManager.DEFAULT_ZSHRC_CONTENT);
        assertFalse(ShellConfigManager.hasCustomZshrc(zshrc));

        // CRLF variant of default template should also be recognized as non-custom
        ShellFsUtils.writeUtf8(zshrc, ShellConfigManager.DEFAULT_ZSHRC_CONTENT.replace("\n", "\r\n"));
        assertFalse(ShellConfigManager.hasCustomZshrc(zshrc));

        // User adds custom line -> custom!
        ShellFsUtils.writeUtf8(zshrc, ShellConfigManager.DEFAULT_ZSHRC_CONTENT + "\nalias k=kubectl\n");
        assertTrue(ShellConfigManager.hasCustomZshrc(zshrc));
    }

    @Test
    public void testApplyDefaultsAndResetToDefaultsRespectForceOverwriteFlag() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File prefixDir = tempFolder.newFolder("prefix");
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File zshBin = new File(binDir, "zsh");
        ShellFsUtils.writeUtf8(zshBin, "#!/bin/sh\n");
        ShellFsUtils.chmod0700(zshBin);

        File homeDir = tempFolder.newFolder("home");
        File zshrc = new File(homeDir, ".zshrc");
        File bashrc = new File(homeDir, ".bashrc");

        String customZshrc = "# My custom zshrc\nexport MY_VAR=123\n";
        String customBashrc = "# My custom bashrc\nexport MY_BASH_VAR=456\n";
        ShellFsUtils.writeUtf8(zshrc, customZshrc);
        ShellFsUtils.writeUtf8(bashrc, customBashrc);

        // 1. applyDefaults with forceOverwrite=false -> preserves custom .zshrc and user lines in .bashrc
        ShellConfigManager.ShellStatus statusAfterSafeApply =
            ShellConfigManager.applyDefaults(context, prefixDir, homeDir, false);
        assertEquals(customZshrc, ShellFsUtils.readUtf8(zshrc));
        assertTrue(ShellFsUtils.readUtf8(bashrc).contains("export MY_BASH_VAR=456"));
        assertTrue(ShellConfigManager.hasBashPromptBlock(bashrc));
        assertTrue(statusAfterSafeApply.isZshInstalled());
        assertTrue(statusAfterSafeApply.isZshDefault());
        assertTrue(statusAfterSafeApply.isOhMyZshInstalled());
        assertTrue(statusAfterSafeApply.arePluginsInstalled());
        assertTrue(statusAfterSafeApply.hasCustomZshrc());
        assertTrue(statusAfterSafeApply.hasCustomBashrc());

        // 2. resetToDefaults with forceOverwrite=false -> still refuses to clobber custom .zshrc or user lines in .bashrc
        ShellConfigManager.ShellStatus statusAfterSafeReset =
            ShellConfigManager.resetToDefaults(context, prefixDir, homeDir, false);
        assertEquals(customZshrc, ShellFsUtils.readUtf8(zshrc));
        assertTrue(ShellFsUtils.readUtf8(bashrc).contains("export MY_BASH_VAR=456"));
        assertTrue(statusAfterSafeReset.hasCustomZshrc());
        assertTrue(statusAfterSafeReset.hasCustomBashrc());

        // 3. resetToDefaults with forceOverwrite=true -> resets both .zshrc and .bashrc to clean managed defaults
        ShellConfigManager.ShellStatus statusAfterForcedReset =
            ShellConfigManager.resetToDefaults(context, prefixDir, homeDir, true);
        assertEquals(ShellConfigManager.DEFAULT_ZSHRC_CONTENT, ShellFsUtils.readUtf8(zshrc));
        assertEquals(ShellConfigManager.MANAGED_BASH_PROMPT_BLOCK + "\n", ShellFsUtils.readUtf8(bashrc));
        assertFalse(statusAfterForcedReset.hasCustomZshrc());
        assertFalse(statusAfterForcedReset.hasCustomBashrc());
        assertTrue(statusAfterForcedReset.isWelcomeBannerEnabled());
    }
}
