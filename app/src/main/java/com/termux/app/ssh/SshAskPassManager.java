package com.termux.app.ssh;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

/**
 * Generates short-lived, single-use {@code SSH_ASKPASS} helper scripts ({@code 0700}) and
 * companion secret files ({@code 0600}) inside Termux's private temp directory.
 * <p>
 * The helper script uses the full Termux shebang ({@code #!/data/data/com.termux/files/usr/bin/sh})
 * and POSIX shell syntax to print the secret once and immediately remove both files.
 */
public final class SshAskPassManager {

    public static final String TERMUX_SH_SHEBANG = "#!/data/data/com.termux/files/usr/bin/sh";
    public static final String ENV_SSH_ASKPASS = "SSH_ASKPASS";
    public static final String ENV_SSH_ASKPASS_REQUIRE = "SSH_ASKPASS_REQUIRE";
    public static final String ENV_DISPLAY = "DISPLAY";

    @NonNull
    private final File tempDir;

    public SshAskPassManager(@NonNull File tempDir) {
        this.tempDir = tempDir;
    }

    @NonNull
    public static SshAskPassManager createDefault() {
        return new SshAskPassManager(TermuxConstants.TERMUX_TMP_PREFIX_DIR);
    }

    @NonNull
    public File getTempDir() {
        return tempDir;
    }

    @NonNull
    public PreparedAskPass createSingleUseHelper(@NonNull String secret) throws IOException {
        if (secret.isEmpty()) {
            throw new IllegalArgumentException("SSH secret must not be empty");
        }
        if (!SshFsUtils.ensureDir0700(tempDir)) {
            throw new IOException("Unable to create private SSH temp directory: " + tempDir.getAbsolutePath());
        }

        String token = UUID.randomUUID().toString();
        File secretFile = new File(tempDir, "termux-ssh-askpass-" + token + ".secret");
        File scriptFile = new File(tempDir, "termux-ssh-askpass-" + token + ".sh");

        try {
            SshFsUtils.writeBytesAtomic0600(secretFile, secret.getBytes(StandardCharsets.UTF_8));
            String scriptContent = buildHelperScript(secretFile, scriptFile);
            SshFsUtils.writeUtf8Atomic0600(scriptFile, scriptContent);
            SshFsUtils.chmod0700(scriptFile);
            return new PreparedAskPass(secretFile, scriptFile);
        } catch (IOException | RuntimeException e) {
            deleteQuietly(secretFile);
            deleteQuietly(scriptFile);
            throw e;
        }
    }

    @NonNull
    private static String buildHelperScript(@NonNull File secretFile, @NonNull File scriptFile) {
        String quotedSecret = shellSingleQuote(secretFile.getAbsolutePath());
        String quotedScript = shellSingleQuote(scriptFile.getAbsolutePath());
        return TERMUX_SH_SHEBANG + "\n"
            + "secret_file=" + quotedSecret + "\n"
            + "script_file=" + quotedScript + "\n"
            + "status=1\n"
            + "if [ -f \"$secret_file\" ]; then\n"
            + "    cat -- \"$secret_file\"\n"
            + "    status=$?\n"
            + "fi\n"
            + "rm -f -- \"$secret_file\" \"$script_file\"\n"
            + "exit \"$status\"\n";
    }

    @NonNull
    static String shellSingleQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void deleteQuietly(@NonNull File file) {
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public static final class PreparedAskPass {
        @NonNull public final File secretFile;
        @NonNull public final File scriptFile;

        PreparedAskPass(@NonNull File secretFile, @NonNull File scriptFile) {
            this.secretFile = secretFile;
            this.scriptFile = scriptFile;
        }

        @NonNull
        public HashMap<String, String> toEnvironment() {
            HashMap<String, String> env = new HashMap<>(4);
            env.put(ENV_SSH_ASKPASS, scriptFile.getAbsolutePath());
            env.put(ENV_SSH_ASKPASS_REQUIRE, "force");
            env.put(ENV_DISPLAY, ":0");
            return env;
        }

        public void cleanup() {
            deleteQuietly(secretFile);
            deleteQuietly(scriptFile);
        }
    }
}
