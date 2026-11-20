package com.termux.app.ssh;

import android.system.Os;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Filesystem utilities for SSH storage with strict {@code 0700}/{@code 0600} permissions
 * and atomic temp-file writes across Android API 21–36 and host JVM unit tests.
 */
final class SshFsUtils {

    private SshFsUtils() {
    }

    static boolean ensureDir0700(@NonNull File dir) {
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            return false;
        }
        chmod0700(dir);
        return dir.isDirectory();
    }

    static void chmod0700(@NonNull File file) {
        //noinspection OctalInteger
        chmod(file, 0700, true, true);
    }

    static void chmod0600(@NonNull File file) {
        //noinspection OctalInteger
        chmod(file, 0600, true, false);
    }

    static void chmod0644(@NonNull File file) {
        //noinspection OctalInteger
        chmod(file, 0644, false, false);
    }

    private static void chmod(
            @NonNull File file,
            int mode,
            boolean ownerOnlyRead,
            boolean ownerExec) {
        try {
            Os.chmod(file.getAbsolutePath(), mode);
        } catch (Throwable ignored) {
            // Host JVM fallback
        }
        try {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, ownerOnlyRead);
            file.setWritable(true, true);
            if (ownerExec) {
                file.setExecutable(true, true);
            }
        } catch (Throwable ignored) {
        }
    }

    static void writeBytesAtomic0600(@NonNull File target, @NonNull byte[] content) throws IOException {
        writeBytesAtomic0600(target, content, true);
    }

    static void writeBytesAtomic0600(
            @NonNull File target,
            @NonNull byte[] content,
            boolean allowOverwrite) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !ensureDir0700(parent)) {
            throw new IOException("Unable to create directory: " + parent.getAbsolutePath());
        }
        File tempFile = new File(parent, "." + target.getName() + "." + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            if (!tempFile.createNewFile()) {
                throw new IOException("Unable to create temp file: " + tempFile.getAbsolutePath());
            }
            chmod0600(tempFile);
            try (FileOutputStream out = new FileOutputStream(tempFile, false)) {
                out.write(content);
                out.getFD().sync();
            }
            chmod0600(tempFile);
            if (!allowOverwrite && target.exists()) {
                throw new IOException("Target file already exists: " + target.getAbsolutePath());
            }
            if (!tempFile.renameTo(target)) {
                if (!allowOverwrite) {
                    throw new IOException("Unable to rename temp file to: " + target.getAbsolutePath());
                }
                if (target.exists() && !target.delete()) {
                    throw new IOException("Unable to replace target file: " + target.getAbsolutePath());
                }
                if (!tempFile.renameTo(target)) {
                    throw new IOException("Unable to rename temp file to: " + target.getAbsolutePath());
                }
            }
            chmod0600(target);
            moved = true;
        } finally {
            if (!moved && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    static void writeUtf8Atomic0600(@NonNull File target, @NonNull String content) throws IOException {
        writeBytesAtomic0600(target, content.getBytes(StandardCharsets.UTF_8));
    }

    @NonNull
    static byte[] readBytes(@NonNull File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        }
    }

    @NonNull
    static String readUtf8(@NonNull File file) throws IOException {
        return new String(readBytes(file), StandardCharsets.UTF_8);
    }
}
