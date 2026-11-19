package com.termux.app.shell;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Dependency-free pure-Java 8 {@code .tar.gz} extractor supporting POSIX USTAR ({@code prefix} + {@code name}),
 * GNU LongName ({@code 'L'}), PAX extended headers ({@code 'x'}, {@code 'g'}), octal & GNU base-256 numeric fields,
 * user execute bit preservation, and strict Tar-Slip path traversal protection.
 */
public final class TarGzExtractor {

    private static final int TAR_BLOCK_SIZE = 512;

    private TarGzExtractor() {
    }

    public static void extract(@NonNull File tarGzFile, @NonNull File destinationDir) throws IOException {
        try (InputStream fileIn = new FileInputStream(tarGzFile)) {
            extract(fileIn, destinationDir);
        }
    }

    public static void extract(@NonNull InputStream tarGzStream, @NonNull File destinationDir) throws IOException {
        if (!destinationDir.exists() && !destinationDir.mkdirs() && !destinationDir.isDirectory()) {
            throw new IOException("Failed to create destination directory: " + destinationDir);
        }
        final String canonicalRoot = destinationDir.getCanonicalPath();
        final String canonicalRootPrefix = canonicalRoot.endsWith(File.separator)
            ? canonicalRoot
            : canonicalRoot + File.separator;

        try (InputStream tarIn = openTarStream(tarGzStream)) {
            byte[] header = new byte[TAR_BLOCK_SIZE];
            byte[] copyBuf = new byte[8192];
            String pendingLongName = null;
            String pendingPaxPath = null;

            while (readFully(tarIn, header, 0, TAR_BLOCK_SIZE) == TAR_BLOCK_SIZE) {
                if (isAllZero(header)) {
                    break;
                }

                byte typeFlag = header[156];
                int mode = (int) parseTarNumber(header, 100, 8);
                long size = parseTarNumber(header, 124, 12);
                if (size < 0) {
                    throw new IOException("Negative tar entry size: " + size);
                }

                String name = parseTarString(header, 0, 100);
                String magic = parseTarString(header, 257, 6);
                if (magic.startsWith("ustar")) {
                    String prefix = parseTarString(header, 345, 155);
                    if (!prefix.isEmpty()) {
                        name = prefix + "/" + name;
                    }
                }

                if (typeFlag == (byte) 'L') {
                    byte[] payload = readSpecialEntryAndPadding(tarIn, size);
                    int len = payload.length;
                    while (len > 0 && payload[len - 1] == 0) {
                        len--;
                    }
                    pendingLongName = new String(payload, 0, len, StandardCharsets.UTF_8);
                    continue;
                } else if (typeFlag == (byte) 'x') {
                    byte[] payload = readSpecialEntryAndPadding(tarIn, size);
                    String paxPath = parsePaxPath(payload);
                    if (paxPath != null) {
                        pendingPaxPath = paxPath;
                    }
                    continue;
                } else if (typeFlag == (byte) 'g') {
                    skipPayloadAndPadding(tarIn, size);
                    continue;
                }

                String entryName = pendingPaxPath != null
                    ? pendingPaxPath
                    : (pendingLongName != null ? pendingLongName : name);
                pendingLongName = null;
                pendingPaxPath = null;

                File destFile = resolveSafeDestination(destinationDir, canonicalRoot, canonicalRootPrefix, entryName);

                boolean isDirectory = (typeFlag == (byte) '5') || entryName.endsWith("/");
                boolean isRegularFile = (typeFlag == (byte) '0' || typeFlag == 0) && !isDirectory;

                if (isDirectory) {
                    if (!destFile.exists() && !destFile.mkdirs() && !destFile.isDirectory()) {
                        throw new IOException("Failed to create tar directory: " + destFile);
                    }
                    skipPayloadAndPadding(tarIn, size);
                } else if (isRegularFile) {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                        throw new IOException("Failed to create parent directory: " + parent);
                    }
                    try (FileOutputStream out = new FileOutputStream(destFile, false)) {
                        long remaining = size;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(copyBuf.length, remaining);
                            int r = tarIn.read(copyBuf, 0, toRead);
                            if (r == -1) {
                                throw new IOException("Unexpected EOF while extracting tar entry: " + entryName);
                            }
                            out.write(copyBuf, 0, r);
                            remaining -= r;
                        }
                    }
                    skipPadding(tarIn, size);
                    // Preserve user execute permission bit (0100 octal == 64 decimal)
                    if ((mode & 0100) != 0) {
                        ShellFsUtils.chmod0700(destFile);
                    }
                } else {
                    // Reject/skip symlinks, hardlinks, FIFOs, or device nodes in archive
                    skipPayloadAndPadding(tarIn, size);
                }
            }
        }
    }

    @NonNull
    private static InputStream openTarStream(@NonNull InputStream rawStream) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(rawStream);
        buffered.mark(2);
        int b0 = buffered.read();
        int b1 = buffered.read();
        buffered.reset();
        if (b0 == 0x1f && b1 == 0x8b) {
            return new GZIPInputStream(buffered);
        }
        return buffered;
    }

    @NonNull
    private static File resolveSafeDestination(
            @NonNull File destinationDir,
            @NonNull String canonicalRoot,
            @NonNull String canonicalRootPrefix,
            @Nullable String rawEntryName) throws IOException {
        if (rawEntryName == null) {
            throw new IOException("Blocked tar path traversal: null entry name");
        }
        String normalized = rawEntryName.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.isEmpty() || ".".equals(normalized)) {
            return destinationDir;
        }
        if (normalized.startsWith("/")) {
            throw new IOException("Blocked tar path traversal: " + rawEntryName);
        }
        String[] segments = normalized.split("/");
        for (String seg : segments) {
            if ("..".equals(seg)) {
                throw new IOException("Blocked tar path traversal: " + rawEntryName);
            }
        }

        File destFile = new File(destinationDir, normalized);
        String canonicalDest = destFile.getCanonicalPath();
        if (!canonicalDest.equals(canonicalRoot) && !canonicalDest.startsWith(canonicalRootPrefix)) {
            throw new IOException("Blocked tar path traversal: " + rawEntryName);
        }
        return destFile;
    }

    private static int readFully(@NonNull InputStream in, @NonNull byte[] buf, int offset, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(buf, offset + total, len - total);
            if (r == -1) {
                break;
            }
            total += r;
        }
        return total;
    }

    private static byte[] readSpecialEntryAndPadding(@NonNull InputStream in, long size) throws IOException {
        if (size < 0 || size > 1024 * 1024) {
            throw new IOException("Invalid tar extended header size: " + size);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) size);
        byte[] buf = new byte[1024];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int r = in.read(buf, 0, toRead);
            if (r == -1) {
                throw new IOException("Unexpected EOF in tar extended header");
            }
            out.write(buf, 0, r);
            remaining -= r;
        }
        skipPadding(in, size);
        return out.toByteArray();
    }

    private static void skipPayloadAndPadding(@NonNull InputStream in, long size) throws IOException {
        skipExact(in, size);
        skipPadding(in, size);
    }

    private static void skipPadding(@NonNull InputStream in, long size) throws IOException {
        long padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE;
        if (padding > 0) {
            skipExact(in, padding);
        }
    }

    private static void skipExact(@NonNull InputStream in, long bytesToSkip) throws IOException {
        byte[] skipBuf = new byte[512];
        long remaining = bytesToSkip;
        while (remaining > 0) {
            int toRead = (int) Math.min(skipBuf.length, remaining);
            int r = in.read(skipBuf, 0, toRead);
            if (r == -1) {
                throw new IOException("Unexpected EOF while skipping tar bytes");
            }
            remaining -= r;
        }
    }

    private static boolean isAllZero(@NonNull byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static String parseTarString(@NonNull byte[] buf, int offset, int maxLen) {
        int end = offset;
        int limit = offset + maxLen;
        while (end < limit && buf[end] != 0) {
            end++;
        }
        return new String(buf, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long parseTarNumber(@NonNull byte[] buf, int offset, int length) {
        if ((buf[offset] & 0x80) != 0) {
            long val = buf[offset] & 0x7f;
            for (int i = 1; i < length; i++) {
                val = (val << 8) | (buf[offset + i] & 0xff);
            }
            return val;
        }
        String str = parseTarString(buf, offset, length).trim();
        if (str.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(str, 8);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Nullable
    private static String parsePaxPath(@NonNull byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);
        int idx = 0;
        while (idx < text.length()) {
            int spaceIdx = text.indexOf(' ', idx);
            if (spaceIdx < 0) {
                break;
            }
            int recLen;
            try {
                recLen = Integer.parseInt(text.substring(idx, spaceIdx));
            } catch (NumberFormatException ignored) {
                break;
            }
            if (recLen <= 0 || idx + recLen > text.length()) {
                break;
            }
            String record = text.substring(spaceIdx + 1, idx + recLen);
            if (record.endsWith("\n")) {
                record = record.substring(0, record.length() - 1);
            }
            if (record.startsWith("path=")) {
                return record.substring("path=".length());
            }
            idx += recLen;
        }
        return null;
    }
}
