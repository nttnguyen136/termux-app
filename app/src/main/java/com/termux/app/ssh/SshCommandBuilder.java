package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pre-flight binary checker and OpenSSH {@code argv} builder for {@link SshProfile}.
 */
public final class SshCommandBuilder {

    private static final String OPTIONS_WITH_ARGUMENT = "BbcDeEFIiJLlmOopQRSWw";

    private SshCommandBuilder() {
    }

    @NonNull
    public static File getSshBinary(@NonNull File prefixDir) {
        return new File(new File(prefixDir, "bin"), "ssh");
    }

    @NonNull
    public static File getShBinary(@NonNull File prefixDir) {
        return new File(new File(prefixDir, "bin"), "sh");
    }

    public static boolean isSshAvailable(@NonNull File prefixDir) {
        File ssh = getSshBinary(prefixDir);
        return ssh.isFile() && ssh.canExecute();
    }

    @NonNull
    public static String shellQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    @NonNull
    public static String buildInstallOpenSshAndConnectCommand(
            @NonNull SshCommandSpec spec,
            @Nullable SshAskPassManager.PreparedAskPass askPass) {
        String baseCommand = "pkg install -y openssh && exec " + spec.toShellCommandLine();
        if (askPass == null) {
            return baseCommand;
        }
        return baseCommand
            + " || { rc=$?; rm -f -- "
            + shellQuote(askPass.secretFile.getAbsolutePath())
            + " "
            + shellQuote(askPass.scriptFile.getAbsolutePath())
            + "; exit \"$rc\"; }";
    }

    @NonNull
    public static SshCommandSpec build(@NonNull SshProfile profile, @NonNull File prefixDir) {
        return build(profile, prefixDir, false);
    }

    @NonNull
    public static SshCommandSpec build(
            @NonNull SshProfile profile,
            @NonNull File prefixDir,
            boolean forcePortFlag) {
        SshProfileValidator.requireValid(profile);
        List<String> args = buildArguments(profile, forcePortFlag);
        return new SshCommandSpec(
            getSshBinary(prefixDir).getAbsolutePath(),
            args,
            profile.getEffectiveDisplayName()
        );
    }

    @NonNull
    public static List<String> buildArguments(@NonNull SshProfile profile) {
        return buildArguments(profile, false);
    }

    @NonNull
    public static List<String> buildArguments(@NonNull SshProfile profile, boolean forcePortFlag) {
        SshProfileValidator.requireValid(profile);
        List<String> parsedExtraArgs = parseExtraArgs(profile.extraArgs);
        ExtraArgsInspection inspection = inspectExtraArgs(parsedExtraArgs);

        List<String> args = new ArrayList<>();
        if (!inspection.setsStrictHostKeyChecking) {
            args.add("-o");
            args.add("StrictHostKeyChecking=accept-new");
        }

        if (forcePortFlag || profile.port != SshProfile.DEFAULT_PORT) {
            args.add("-p");
            args.add(String.valueOf(profile.port));
        }

        if (profile.authType == SshAuthType.SSH_KEY && !profile.privateKeyPath.isEmpty()) {
            args.add("-i");
            args.add(profile.privateKeyPath);
            args.add("-o");
            args.add("IdentitiesOnly=yes");
        }

        if (profile.keepAliveIntervalSeconds > 0) {
            args.add("-o");
            args.add("ServerAliveInterval=" + profile.keepAliveIntervalSeconds);
        }

        if (!profile.proxyJump.isEmpty()) {
            args.add("-J");
            args.add(profile.proxyJump);
        }

        boolean needTtyFlag = !profile.startupCommand.isEmpty() && !inspection.setsTtyPreference;
        if (needTtyFlag && inspection.doubleDashIndex >= 0) {
            for (int i = 0; i < parsedExtraArgs.size(); i++) {
                if (i == inspection.doubleDashIndex) {
                    args.add("-t");
                }
                args.add(parsedExtraArgs.get(i));
            }
        } else {
            args.addAll(parsedExtraArgs);
            if (needTtyFlag) {
                args.add("-t");
            }
        }

        args.add(profile.username + "@" + profile.host);
        if (!profile.startupCommand.isEmpty()) {
            args.add(profile.startupCommand);
        }
        return args;
    }

    /**
     * Parses shell-like {@code extraArgs} respecting single quotes, double quotes, and backslash escapes.
     */
    @NonNull
    public static List<String> parseExtraArgs(@Nullable String rawExtraArgs) {
        if (rawExtraArgs == null || rawExtraArgs.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        int len = rawExtraArgs.length();
        for (int i = 0; i < len; i++) {
            char c = rawExtraArgs.charAt(i);
            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                }
            } else if (inDoubleQuote) {
                if (c == '"') {
                    inDoubleQuote = false;
                } else if (c == '\\' && i + 1 < len) {
                    char next = rawExtraArgs.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`') {
                        current.append(next);
                        i++;
                    } else if (next == '\n') {
                        i++;
                    } else {
                        current.append(c);
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (Character.isWhitespace(c)) {
                    if (inToken) {
                        tokens.add(current.toString());
                        current.setLength(0);
                        inToken = false;
                    }
                } else if (c == '\'') {
                    inSingleQuote = true;
                    inToken = true;
                } else if (c == '"') {
                    inDoubleQuote = true;
                    inToken = true;
                } else if (c == '\\') {
                    inToken = true;
                    if (i + 1 < len) {
                        char next = rawExtraArgs.charAt(++i);
                        if (next != '\n') {
                            current.append(next);
                        }
                    } else {
                        current.append(c);
                    }
                } else {
                    current.append(c);
                    inToken = true;
                }
            }
        }

        if (inSingleQuote || inDoubleQuote) {
            throw new IllegalArgumentException("Unclosed quote in SSH extraArgs");
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    @NonNull
    private static ExtraArgsInspection inspectExtraArgs(@NonNull List<String> tokens) {
        boolean setsStrictHostKeyChecking = false;
        boolean setsTtyPreference = false;
        int doubleDashIndex = -1;

        boolean expectingOptionArg = false;
        char optionExpectingArg = '\0';

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (expectingOptionArg) {
                if (optionExpectingArg == 'o') {
                    String lower = token.toLowerCase(Locale.ROOT);
                    if (lower.startsWith("stricthostkeychecking=") || lower.startsWith("stricthostkeychecking ")) {
                        setsStrictHostKeyChecking = true;
                    } else if (lower.startsWith("requesttty=") || lower.startsWith("requesttty ")) {
                        setsTtyPreference = true;
                    }
                }
                expectingOptionArg = false;
                optionExpectingArg = '\0';
                continue;
            }

            if ("--".equals(token)) {
                doubleDashIndex = i;
                break;
            }

            if (token.startsWith("-") && token.length() > 1) {
                char firstFlag = token.charAt(1);
                if (firstFlag == 'o') {
                    if (token.length() == 2) {
                        expectingOptionArg = true;
                        optionExpectingArg = 'o';
                    } else {
                        String lower = token.substring(2).toLowerCase(Locale.ROOT);
                        if (lower.startsWith("stricthostkeychecking=") || lower.startsWith("stricthostkeychecking ")) {
                            setsStrictHostKeyChecking = true;
                        } else if (lower.startsWith("requesttty=") || lower.startsWith("requesttty ")) {
                            setsTtyPreference = true;
                        }
                    }
                } else if (OPTIONS_WITH_ARGUMENT.indexOf(firstFlag) >= 0) {
                    if (token.length() == 2) {
                        expectingOptionArg = true;
                        optionExpectingArg = firstFlag;
                    }
                } else {
                    if (token.indexOf('t', 1) >= 0 || token.indexOf('T', 1) >= 0 || token.indexOf('N', 1) >= 0) {
                        setsTtyPreference = true;
                    }
                }
            }
        }

        return new ExtraArgsInspection(setsStrictHostKeyChecking, setsTtyPreference, doubleDashIndex);
    }

    private static final class ExtraArgsInspection {
        final boolean setsStrictHostKeyChecking;
        final boolean setsTtyPreference;
        final int doubleDashIndex;

        ExtraArgsInspection(boolean setsStrictHostKeyChecking, boolean setsTtyPreference, int doubleDashIndex) {
            this.setsStrictHostKeyChecking = setsStrictHostKeyChecking;
            this.setsTtyPreference = setsTtyPreference;
            this.doubleDashIndex = doubleDashIndex;
        }
    }
}
