package com.termux.app.shell;

import android.content.Context;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class TarGzExtractorTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testExtractUstarAndExecutableBit() throws Exception {
        ByteArrayOutputStream rawTar = new ByteArrayOutputStream();
        writeUstarEntry(rawTar, "dir/", new byte[0], 0755, (byte) '5');
        writeUstarEntry(rawTar, "dir/hello.txt", "hello world\n".getBytes(StandardCharsets.UTF_8), 0644, (byte) '0');
        writeUstarEntry(rawTar, "dir/run.sh", "#!/bin/sh\necho ok\n".getBytes(StandardCharsets.UTF_8), 0755, (byte) '0');
        String longPrefix = "nested/a/very/deep/directory/structure/for/ustar/prefix/testing";
        writeUstarEntry(rawTar, longPrefix + "/file.zsh", "PROMPT='> '\n".getBytes(StandardCharsets.UTF_8), 0644, (byte) '0');
        rawTar.write(new byte[1024]);

        byte[] gzBytes = gzip(rawTar.toByteArray());
        File destDir = tempFolder.newFolder("ustar-out");
        TarGzExtractor.extract(new ByteArrayInputStream(gzBytes), destDir);

        File hello = new File(destDir, "dir/hello.txt");
        assertTrue(hello.isFile());
        assertEquals("hello world\n", ShellFsUtils.readUtf8(hello));

        File runSh = new File(destDir, "dir/run.sh");
        assertTrue(runSh.isFile());
        assertTrue(runSh.canExecute());

        File deepFile = new File(destDir, longPrefix + "/file.zsh");
        assertTrue(deepFile.isFile());
        assertEquals("PROMPT='> '\n", ShellFsUtils.readUtf8(deepFile));
    }

    @Test
    public void testExtractGnuLongNameAndPaxExtendedHeaders() throws Exception {
        ByteArrayOutputStream rawTar = new ByteArrayOutputStream();

        // 1. PAX global header ('g') should be skipped
        byte[] globalPax = "52 comment=c0140703a53eb0d22be44f7bac318d0baacc927c\n".getBytes(StandardCharsets.UTF_8);
        writeUstarEntry(rawTar, "pax_global_header", globalPax, 0644, (byte) 'g');

        // 2. GNU LongName ('L')
        String gnuLongPath = "custom/plugins/zsh-syntax-highlighting/highlighters/brackets/brackets-highlighter-very-long-name.zsh";
        byte[] longNamePayload = (gnuLongPath + "\0").getBytes(StandardCharsets.UTF_8);
        writeUstarEntry(rawTar, "././@LongLink", longNamePayload, 0644, (byte) 'L');
        writeUstarEntry(rawTar, "truncated-name", "gnu-long-content".getBytes(StandardCharsets.UTF_8), 0644, (byte) '0');

        // 3. PAX extended header ('x')
        String paxTarget = "custom/themes/pax-override-theme.zsh-theme";
        String recordBody = " path=" + paxTarget + "\n";
        int totalLen = recordBody.length() + 2;
        if (String.valueOf(totalLen).length() + recordBody.length() != totalLen) {
            totalLen = recordBody.length() + String.valueOf(totalLen).length();
        }
        String paxRecord = totalLen + recordBody;
        writeUstarEntry(rawTar, "PaxHeader/ignored", paxRecord.getBytes(StandardCharsets.UTF_8), 0644, (byte) 'x');
        writeUstarEntry(rawTar, "ignored-short-name", "pax-theme-content".getBytes(StandardCharsets.UTF_8), 0644, (byte) '0');

        rawTar.write(new byte[1024]);

        File destDir = tempFolder.newFolder("gnu-pax-out");
        TarGzExtractor.extract(new ByteArrayInputStream(gzip(rawTar.toByteArray())), destDir);

        assertFalse(new File(destDir, "pax_global_header").exists());

        File gnuFile = new File(destDir, gnuLongPath);
        assertTrue(gnuFile.isFile());
        assertEquals("gnu-long-content", ShellFsUtils.readUtf8(gnuFile));

        File paxFile = new File(destDir, paxTarget);
        assertTrue(paxFile.isFile());
        assertEquals("pax-theme-content", ShellFsUtils.readUtf8(paxFile));
    }

    @Test
    public void testRejectsTarSlipPathTraversal() throws Exception {
        String[] maliciousPaths = new String[] {
            "../evil.sh",
            "a/../../evil.sh",
            "/etc/passwd",
            "custom/../../../outside.txt"
        };

        for (String badPath : maliciousPaths) {
            ByteArrayOutputStream rawTar = new ByteArrayOutputStream();
            writeUstarEntry(rawTar, badPath, "pwned".getBytes(StandardCharsets.UTF_8), 0644, (byte) '0');
            rawTar.write(new byte[1024]);

            File destDir = tempFolder.newFolder();
            try {
                TarGzExtractor.extract(new ByteArrayInputStream(gzip(rawTar.toByteArray())), destDir);
                fail("Expected IOException for malicious tar entry path: " + badPath);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("Blocked tar path traversal"));
            }
        }
    }

    @Test
    public void testExtractRealBuildGeneratedOmzBundleFromAssets() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File omzDir = tempFolder.newFolder("extracted-omz");

        try (InputStream assetIn = ZshBundleInstaller.openOmzArchiveAssetStream(context.getAssets())) {
            TarGzExtractor.extract(assetIn, omzDir);
        }

        assertTrue(new File(omzDir, "oh-my-zsh.sh").isFile());
        assertTrue(new File(omzDir, "tools/check_for_upgrade.sh").isFile());
        assertTrue(new File(omzDir, "lib/git.zsh").isFile());
        assertTrue(new File(omzDir, "lib/completion.zsh").isFile());
        assertTrue(new File(omzDir, "custom/plugins/zsh-autosuggestions/zsh-autosuggestions.plugin.zsh").isFile());
        assertTrue(new File(omzDir, "custom/plugins/zsh-autosuggestions/zsh-autosuggestions.zsh").isFile());
        assertTrue(new File(omzDir, "custom/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.plugin.zsh").isFile());
        assertTrue(new File(omzDir, "custom/plugins/zsh-syntax-highlighting/.version").isFile());
        assertTrue(new File(omzDir, "custom/plugins/zsh-syntax-highlighting/.revision-hash").isFile());
        assertTrue(new File(omzDir, "custom/plugins/zsh-syntax-highlighting/highlighters/main/main-highlighter.zsh").isFile());

        File themeFile = new File(omzDir, "custom/themes/termux-lean.zsh-theme");
        assertTrue(themeFile.isFile());
        String themeContent = ShellFsUtils.readUtf8(themeFile);
        assertTrue(themeContent.contains("_termux_lean_git_prompt"));
        assertTrue(themeContent.contains("GIT_OPTIONAL_LOCKS=0"));
    }

    static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    static void writeUstarEntry(ByteArrayOutputStream out, String entryPath, byte[] data, int mode, byte typeFlag) throws IOException {
        byte[] header = new byte[512];
        byte[] nameBytes = entryPath.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length <= 100) {
            System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        } else {
            int splitSlash = entryPath.lastIndexOf('/');
            byte[] prefixBytes = entryPath.substring(0, splitSlash).getBytes(StandardCharsets.UTF_8);
            byte[] shortNameBytes = entryPath.substring(splitSlash + 1).getBytes(StandardCharsets.UTF_8);
            System.arraycopy(shortNameBytes, 0, header, 0, shortNameBytes.length);
            System.arraycopy(prefixBytes, 0, header, 345, prefixBytes.length);
        }
        writeOctal(header, 100, 8, mode);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, data.length);
        writeOctal(header, 136, 12, 0);
        for (int i = 148; i < 156; i++) {
            header[i] = (byte) ' ';
        }
        header[156] = typeFlag;
        byte[] magic = "ustar".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        header[262] = 0;
        header[263] = (byte) '0';
        header[264] = (byte) '0';

        long sum = 0;
        for (int i = 0; i < 512; i++) {
            sum += (header[i] & 0xff);
        }
        String oct = Long.toOctalString(sum);
        while (oct.length() < 6) {
            oct = "0" + oct;
        }
        byte[] csum = oct.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(csum, 0, header, 148, 6);
        header[154] = 0;
        header[155] = (byte) ' ';

        out.write(header);
        if (data.length > 0) {
            out.write(data);
            int pad = (512 - (data.length % 512)) % 512;
            if (pad > 0) {
                out.write(new byte[pad]);
            }
        }
    }

    private static void writeOctal(byte[] buf, int offset, int length, long value) {
        String oct = Long.toOctalString(value);
        int digits = length - 1;
        while (oct.length() < digits) {
            oct = "0" + oct;
        }
        byte[] bytes = oct.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, buf, offset, Math.min(bytes.length, digits));
        buf[offset + length - 1] = 0;
    }
}
