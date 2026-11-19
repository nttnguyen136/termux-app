package com.termux.app.shell;

import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Cross-environment filesystem helpers for POSIX symlinks, permissions, and UTF-8 file I/O
 * that work on Android API 21–36 via {@link Os} and fall back cleanly to {@code java.nio.file.Files}
 * via reflection when running in host JVM / Robolectric unit tests.
 */
final class ShellFsUtils {

    private ShellFsUtils() {
    }

    static boolean ensureDir0700(@NonNull File dir) {
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            return false;
        }
        chmod0700(dir);
        return dir.isDirectory();
    }

    static void chmod0700(@NonNull File file) {
        try {
            //noinspection OctalInteger
            Os.chmod(file.getAbsolutePath(), 0700);
        } catch (Throwable ignored) {
            // Host JVM or Robolectric fallback
        }
        // Always also set Java File flags so File.canExecute() works in JVM unit tests
        try {
            file.setReadable(true, true);
            file.setWritable(true, true);
            file.setExecutable(true, true);
        } catch (Throwable ignored) {
        }
    }

    static void chmod0600(@NonNull File file) {
        try {
            //noinspection OctalInteger
            Os.chmod(file.getAbsolutePath(), 0600);
        } catch (Throwable ignored) {
        }
        try {
            file.setReadable(true, true);
            file.setWritable(true, true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Returns true if {@code file} exists on disk OR is a symbolic link (even a dangling one).
     */
    static boolean existsOrIsSymlink(@NonNull File file) {
        if (file.exists()) {
            return true;
        }
        try {
            android.system.StructStat stat = Os.lstat(file.getAbsolutePath());
            if (stat != null && stat.st_mode != 0) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> pathsClass = Class.forName("java.nio.file.Paths");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Method getPath = pathsClass.getMethod("get", String.class, String[].class);
            Object pathObj = getPath.invoke(null, file.getAbsolutePath(), new String[0]);
            Method isSymbolicLink = filesClass.getMethod("isSymbolicLink", pathClass);
            Boolean isLink = (Boolean) isSymbolicLink.invoke(null, pathObj);
            return Boolean.TRUE.equals(isLink);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean createSymlink(@NonNull String targetPath, @NonNull File linkFile, boolean overwrite) {
        File parent = linkFile.getParentFile();
        if (parent != null && !parent.exists()) {
            ensureDir0700(parent);
        }
        if (existsOrIsSymlink(linkFile)) {
            if (!overwrite) {
                return false;
            }
            deleteLinkOrFile(linkFile);
        }
        try {
            Os.symlink(targetPath, linkFile.getAbsolutePath());
            if (existsOrIsSymlink(linkFile)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall back to java.nio.file.Files on host JVM / Robolectric
        }
        try {
            Class<?> pathsClass = Class.forName("java.nio.file.Paths");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Class<?> fileAttrArrayClass = Class.forName("[Ljava.nio.file.attribute.FileAttribute;");
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Method getPath = pathsClass.getMethod("get", String.class, String[].class);
            Object linkPathObj = getPath.invoke(null, linkFile.getAbsolutePath(), new String[0]);
            Object targetPathObj = getPath.invoke(null, targetPath, new String[0]);
            Object emptyAttrs = java.lang.reflect.Array.newInstance(
                Class.forName("java.nio.file.attribute.FileAttribute"), 0);
            Method createSymbolicLink = filesClass.getMethod(
                "createSymbolicLink", pathClass, pathClass, fileAttrArrayClass);
            createSymbolicLink.invoke(null, linkPathObj, targetPathObj, emptyAttrs);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    static String readSymlinkTarget(@NonNull File linkFile) {
        try {
            String target = Os.readlink(linkFile.getAbsolutePath());
            if (target != null && !target.isEmpty()) {
                return target;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> pathsClass = Class.forName("java.nio.file.Paths");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Method getPath = pathsClass.getMethod("get", String.class, String[].class);
            Object linkPathObj = getPath.invoke(null, linkFile.getAbsolutePath(), new String[0]);
            Method isSymbolicLink = filesClass.getMethod("isSymbolicLink", pathClass);
            if (Boolean.TRUE.equals(isSymbolicLink.invoke(null, linkPathObj))) {
                Method readSymbolicLink = filesClass.getMethod("readSymbolicLink", pathClass);
                Object targetPathObj = readSymbolicLink.invoke(null, linkPathObj);
                if (targetPathObj != null) {
                    return targetPathObj.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static boolean deleteLinkOrFile(@NonNull File file) {
        try {
            Class<?> pathsClass = Class.forName("java.nio.file.Paths");
            Class<?> pathClass = Class.forName("java.nio.file.Path");
            Class<?> filesClass = Class.forName("java.nio.file.Files");
            Method getPath = pathsClass.getMethod("get", String.class, String[].class);
            Object pathObj = getPath.invoke(null, file.getAbsolutePath(), new String[0]);
            Method deleteIfExists = filesClass.getMethod("deleteIfExists", pathClass);
            Boolean deleted = (Boolean) deleteIfExists.invoke(null, pathObj);
            if (Boolean.TRUE.equals(deleted)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return !file.exists() || file.delete();
    }

    static void deleteRecursively(@Nullable File file) {
        if (file == null) {
            return;
        }
        String symlinkTarget = readSymlinkTarget(file);
        if (symlinkTarget == null && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        deleteLinkOrFile(file);
    }

    @NonNull
    static String readUtf8(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readUtf8(in);
        }
    }

    @NonNull
    static String readUtf8(@NonNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static void writeUtf8(@NonNull File file, @NonNull String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            ensureDir0700(parent);
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
