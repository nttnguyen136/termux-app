package com.termux.app.ssh;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.result.ResultData;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.TermuxBootstrap;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Discovers, imports (via SAF streams), and generates SSH keys inside {@code ~/.ssh}
 * with strict {@code 0700}/{@code 0600} permissions.
 */
public final class SshKeyManager {

    private static final String LOG_TAG = "SshKeyManager";
    private static final int MAX_IMPORTED_KEY_BYTES = 1024 * 1024;
    private static final String DEFAULT_IMPORTED_KEY_NAME = "imported_key";
    private static final String DEFAULT_GENERATED_KEY_NAME = "id_ed25519";
    private static final Object KEY_FILE_LOCK = new Object();

    private SshKeyManager() {
    }

    @NonNull
    public static File getDefaultPrefixDir() {
        return new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
    }

    @NonNull
    public static File getDefaultHomeDir() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
    }

    @NonNull
    public static File getDefaultSshDir() {
        return new File(getDefaultHomeDir(), ".ssh");
    }

    @NonNull
    public static File getSshKeygenBinary(@NonNull File prefixDir) {
        return new File(new File(prefixDir, "bin"), "ssh-keygen");
    }

    public static boolean isSshKeygenAvailable() {
        return isSshKeygenAvailable(getDefaultPrefixDir());
    }

    public static boolean isSshKeygenAvailable(@NonNull File prefixDir) {
        File sshKeygen = getSshKeygenBinary(prefixDir);
        return sshKeygen.isFile() && sshKeygen.canExecute();
    }

    @NonNull
    public static File getPublicKeyFile(@NonNull File privateKeyFile) {
        return new File(privateKeyFile.getAbsolutePath() + ".pub");
    }

    @Nullable
    public static String readPublicKey(@NonNull File privateKeyFile) throws IOException {
        File pubFile = getPublicKeyFile(privateKeyFile);
        if (!pubFile.isFile()) {
            return null;
        }
        return SshFsUtils.readUtf8(pubFile).trim();
    }

    @NonNull
    public static List<File> scanPrivateKeys() {
        return scanPrivateKeys(getDefaultSshDir());
    }

    @NonNull
    public static List<File> scanPrivateKeys(@NonNull File sshDir) {
        if (!sshDir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] entries = sshDir.listFiles();
        if (entries == null || entries.length == 0) {
            return Collections.emptyList();
        }
        List<File> keys = new ArrayList<>();
        for (File entry : entries) {
            if (isCandidatePrivateKey(entry)) {
                keys.add(entry);
            }
        }
        Collections.sort(keys, (a, b) -> {
            int ignoreCase = a.getName().compareToIgnoreCase(b.getName());
            if (ignoreCase != 0) {
                return ignoreCase;
            }
            return a.getName().compareTo(b.getName());
        });
        return Collections.unmodifiableList(keys);
    }

    @NonNull
    public static File importPrivateKey(
            @NonNull Context context,
            @NonNull Uri sourceUri) throws IOException {
        return importPrivateKey(context, sourceUri, getDefaultSshDir());
    }

    @NonNull
    public static File importPrivateKey(
            @NonNull Context context,
            @NonNull Uri sourceUri,
            @NonNull File sshDir) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String desiredFilename = resolveDisplayName(resolver, sourceUri);
        try (InputStream in = openUriStream(resolver, sourceUri)) {
            return importPrivateKey(sshDir, desiredFilename, in);
        } catch (UnsupportedOperationException e) {
            throw new IOException("Unable to read selected SSH key URI: " + sourceUri, e);
        }
    }

    @NonNull
    private static InputStream openUriStream(
            @NonNull ContentResolver resolver,
            @NonNull Uri sourceUri) throws IOException {
        InputStream stream;
        try {
            stream = resolver.openInputStream(sourceUri);
        } catch (SecurityException | UnsupportedOperationException e) {
            throw new IOException("Unable to open selected SSH key URI: " + sourceUri, e);
        }
        if (stream == null) {
            throw new IOException("Unable to open selected SSH key URI: " + sourceUri);
        }
        return stream;
    }

    @Nullable
    private static String resolveDisplayName(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        try (Cursor cursor = resolver.query(
            uri,
            new String[]{OpenableColumns.DISPLAY_NAME},
            null,
            null,
            null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Some content providers do not support DISPLAY_NAME projection; fall back to URI path.
        }
        String lastSegment = uri.getLastPathSegment();
        return (lastSegment != null && !lastSegment.trim().isEmpty()) ? lastSegment.trim() : null;
    }

    @NonNull
    public static File importPrivateKey(
            @NonNull File sshDir,
            @Nullable String desiredFilename,
            @NonNull InputStream inputStream) throws IOException {
        byte[] content = readBoundedBytes(inputStream, MAX_IMPORTED_KEY_BYTES);
        if (content.length == 0) {
            throw new IOException("Selected SSH key file is empty");
        }

        String safeFilename = sanitizeKeyFilename(desiredFilename, DEFAULT_IMPORTED_KEY_NAME);
        synchronized (KEY_FILE_LOCK) {
            if (!SshFsUtils.ensureDir0700(sshDir)) {
                throw new IOException("Unable to create SSH directory: " + sshDir.getAbsolutePath());
            }
            File targetFile = resolveCollisionFreeFile(sshDir, safeFilename, false);
            writeNewFile0600(targetFile, content);
            return targetFile;
        }
    }

    @NonNull
    public static SshKeygenRunner createDefaultRunner(@NonNull Context context) {
        return new AppShellSshKeygenRunner(context);
    }

    @NonNull
    public static SshKeyGenerationResult generateEd25519Key(
            @NonNull Context context,
            @Nullable String keyName,
            @Nullable String passphrase,
            @Nullable String comment) {
        return generateEd25519Key(
            getDefaultPrefixDir(),
            getDefaultHomeDir(),
            keyName,
            passphrase,
            comment,
            createDefaultRunner(context)
        );
    }

    @NonNull
    public static SshKeyGenerationResult generateEd25519Key(
            @NonNull File prefixDir,
            @NonNull File homeDir,
            @Nullable String keyName,
            @Nullable String passphrase,
            @Nullable String comment,
            @NonNull SshKeygenRunner runner) {
        File sshKeygenBinary = getSshKeygenBinary(prefixDir);
        if (!isSshKeygenAvailable(prefixDir)) {
            return SshKeyGenerationResult.missingOpenssh();
        }

        String normalizedInput = keyName;
        if (normalizedInput != null && normalizedInput.trim().toLowerCase(Locale.ROOT).endsWith(".pub")) {
            String trimmed = normalizedInput.trim();
            normalizedInput = trimmed.substring(0, trimmed.length() - 4);
        }
        String safeKeyName = sanitizeKeyFilename(normalizedInput, DEFAULT_GENERATED_KEY_NAME);

        File sshDir = new File(homeDir, ".ssh");
        File targetKeyFile;
        synchronized (KEY_FILE_LOCK) {
            if (!SshFsUtils.ensureDir0700(sshDir)) {
                return SshKeyGenerationResult.failed("Unable to create SSH directory: " + sshDir.getAbsolutePath());
            }
            targetKeyFile = resolveCollisionFreeFile(sshDir, safeKeyName, true);
        }

        List<String> args = new ArrayList<>();
        args.add("-t");
        args.add("ed25519");
        args.add("-f");
        args.add(targetKeyFile.getAbsolutePath());
        args.add("-N");
        args.add(passphrase != null ? passphrase : "");
        if (comment != null && !comment.trim().isEmpty()) {
            args.add("-C");
            args.add(comment.trim());
        }

        SshKeygenRunner.ExecutionResult execResult;
        try {
            execResult = runner.runSshKeygen(prefixDir, homeDir, sshKeygenBinary, Collections.unmodifiableList(args));
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "ssh-keygen execution threw unexpected exception", e);
            return SshKeyGenerationResult.failed(e.getMessage());
        }

        if (execResult == null || !execResult.isSuccess()) {
            String msg = execResult != null ? execResult.getEffectiveErrorMessage() : "ssh-keygen returned no result";
            return SshKeyGenerationResult.failed(msg);
        }

        File pubKeyFile = getPublicKeyFile(targetKeyFile);
        if (!targetKeyFile.isFile() || !pubKeyFile.isFile()) {
            return SshKeyGenerationResult.failed("Generated key files were not created on disk");
        }

        SshFsUtils.ensureDir0700(sshDir);
        SshFsUtils.chmod0600(targetKeyFile);
        chmod0644(pubKeyFile);

        try {
            String pubContent = SshFsUtils.readUtf8(pubKeyFile).trim();
            if (pubContent.isEmpty()) {
                return SshKeyGenerationResult.failed("Generated public key file is empty");
            }
            return SshKeyGenerationResult.success(targetKeyFile, pubKeyFile, pubContent);
        } catch (IOException e) {
            return SshKeyGenerationResult.failed("Failed to read generated public key: " + e.getMessage());
        }
    }

    private static void chmod0644(@NonNull File file) {
        try {
            //noinspection OctalInteger
            Os.chmod(file.getAbsolutePath(), 0644);
        } catch (Throwable ignored) {
            // Host JVM fallback
        }
        try {
            file.setReadable(false, false);
            file.setWritable(false, false);
            file.setExecutable(false, false);
            file.setReadable(true, false);
            file.setWritable(true, true);
        } catch (Throwable ignored) {
        }
    }

    private static void writeNewFile0600(@NonNull File targetFile, @NonNull byte[] content) throws IOException {
        File parent = targetFile.getParentFile();
        File tempFile = new File(parent, "." + targetFile.getName() + "." + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            if (!tempFile.createNewFile()) {
                throw new IOException("Unable to create temp key file: " + tempFile.getAbsolutePath());
            }
            SshFsUtils.chmod0600(tempFile);
            try (FileOutputStream out = new FileOutputStream(tempFile, false)) {
                out.write(content);
                out.getFD().sync();
            }
            SshFsUtils.chmod0600(tempFile);
            if (targetFile.exists()) {
                throw new IOException("Target SSH key already exists: " + targetFile.getAbsolutePath());
            }
            if (!tempFile.renameTo(targetFile)) {
                throw new IOException("Unable to move imported SSH key to: " + targetFile.getAbsolutePath());
            }
            SshFsUtils.chmod0600(targetFile);
            moved = true;
        } finally {
            if (!moved && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    @NonNull
    private static byte[] readBoundedBytes(@NonNull InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Selected SSH key file exceeds maximum allowed size");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @NonNull
    static String sanitizeKeyFilename(@Nullable String rawFilename, @NonNull String fallbackName) {
        if (rawFilename == null) {
            return fallbackName;
        }
        String trimmed = rawFilename.trim();
        int lastSlash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        String base = lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
        StringBuilder cleaned = new StringBuilder(base.length());
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c >= 32 && c != '/' && c != '\\' && c != '\0') {
                cleaned.append(c);
            }
        }
        String name = cleaned.toString().trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return fallbackName;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if ("config".equals(lower)
            || ".ds_store".equals(lower)
            || lower.startsWith("known_hosts")
            || lower.startsWith("authorized_keys")
            || lower.endsWith(".tmp")
            || lower.endsWith(".lock")) {
            return "imported_" + name;
        }
        return name;
    }

    @NonNull
    static File resolveCollisionFreeFile(
            @NonNull File sshDir,
            @NonNull String filename,
            boolean checkCompanionPub) {
        File candidate = new File(sshDir, filename);
        if (!hasCollision(sshDir, candidate, checkCompanionPub)) {
            return candidate;
        }
        int dotIndex = filename.lastIndexOf('.');
        String stem = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String ext = dotIndex > 0 ? filename.substring(dotIndex) : "";
        for (int suffix = 1; suffix < 10_000; suffix++) {
            File next = new File(sshDir, stem + "_" + suffix + ext);
            if (!hasCollision(sshDir, next, checkCompanionPub)) {
                return next;
            }
        }
        return new File(sshDir, stem + "_" + UUID.randomUUID() + ext);
    }

    private static boolean hasCollision(
            @NonNull File sshDir,
            @NonNull File candidate,
            boolean checkCompanionPub) {
        if (candidate.exists()) {
            return true;
        }
        if (checkCompanionPub) {
            File pub = new File(sshDir, candidate.getName() + ".pub");
            return pub.exists();
        }
        return false;
    }

    private static boolean isCandidatePrivateKey(@NonNull File file) {
        if (!file.isFile()) {
            return false;
        }
        String name = file.getName();
        if (name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pub")) {
            return false;
        }
        if ("config".equals(lower)) {
            return false;
        }
        if (lower.startsWith("known_hosts") || lower.startsWith("authorized_keys")) {
            return false;
        }
        if (".ds_store".equals(lower) || lower.endsWith(".tmp") || lower.endsWith(".lock")) {
            return false;
        }
        return true;
    }

    private static final class AppShellSshKeygenRunner implements SshKeygenRunner {
        @NonNull private final Context appContext;

        private AppShellSshKeygenRunner(@NonNull Context context) {
            Context app = context.getApplicationContext();
            this.appContext = app != null ? app : context;
        }

        @NonNull
        @Override
        public ExecutionResult runSshKeygen(
                @NonNull File prefixDir,
                @NonNull File homeDir,
                @NonNull File sshKeygenBinary,
                @NonNull List<String> arguments) {
            ExecutionCommand executionCommand = new ExecutionCommand(
                -1,
                sshKeygenBinary.getAbsolutePath(),
                arguments.toArray(new String[0]),
                null,
                homeDir.getAbsolutePath(),
                ExecutionCommand.Runner.APP_SHELL.getName(),
                false
            );
            executionCommand.commandLabel = "SSH Ed25519 Key Generation";
            executionCommand.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF;

            HashMap<String, String> additionalEnvironment = new HashMap<>();
            additionalEnvironment.put(TermuxShellEnvironment.ENV_PREFIX, prefixDir.getAbsolutePath());
            additionalEnvironment.put(TermuxShellEnvironment.ENV_HOME, homeDir.getAbsolutePath());
            additionalEnvironment.put(TermuxShellEnvironment.ENV_TMPDIR, new File(prefixDir, "tmp").getAbsolutePath());
            if (!TermuxBootstrap.isAppPackageVariantAPTAndroid5()) {
                additionalEnvironment.put(TermuxShellEnvironment.ENV_PATH, new File(prefixDir, "bin").getAbsolutePath());
            }

            AppShell appShell = AppShell.execute(
                appContext,
                executionCommand,
                null,
                new TermuxShellEnvironment(),
                additionalEnvironment,
                true
            );

            Integer exitCode = executionCommand.resultData.exitCode;
            String stdout = executionCommand.resultData.stdout.toString();
            String stderr = executionCommand.resultData.stderr.toString();
            String internalErrors = ResultData.getErrorsListMinimalString(executionCommand.resultData);

            if (appShell != null && executionCommand.isSuccessful() && exitCode != null && exitCode == 0) {
                return ExecutionResult.success(stdout, stderr);
            }

            int code = exitCode != null ? exitCode : 1;
            String errMsg = !stderr.trim().isEmpty()
                ? stderr.trim()
                : (!internalErrors.trim().isEmpty() ? internalErrors.trim() : stdout.trim());
            return ExecutionResult.failure(code, stdout, stderr, errMsg);
        }
    }
}
