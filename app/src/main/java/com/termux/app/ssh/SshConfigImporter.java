package com.termux.app.ssh;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Strictly one-way, read-only parser for OpenSSH {@code ~/.ssh/config} files.
 * Extracts concrete {@code Host} entries into preview {@link ImportCandidate} records
 * without ever writing to or modifying {@code ~/.ssh/config}.
 */
public final class SshConfigImporter {

    public static final class ImportCandidate {
        @NonNull private final String alias;
        @NonNull private final SshProfile profile;
        private final boolean duplicate;
        @Nullable private final SshProfile matchingExistingProfile;

        public ImportCandidate(
                @NonNull String alias,
                @NonNull SshProfile profile,
                boolean duplicate,
                @Nullable SshProfile matchingExistingProfile) {
            this.alias = alias;
            this.profile = profile;
            this.duplicate = duplicate;
            this.matchingExistingProfile = matchingExistingProfile;
        }

        @NonNull
        public String getAlias() {
            return alias;
        }

        @NonNull
        public SshProfile getProfile() {
            return profile;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        @Nullable
        public SshProfile getMatchingExistingProfile() {
            return matchingExistingProfile;
        }

        public boolean hasUsername() {
            return !profile.username.trim().isEmpty();
        }
    }

    private enum SectionKind {
        NONE,
        HOST,
        SKIP
    }

    private static final class HostBlockBuilder {
        final List<String> rawPatterns;
        final List<String> concreteAliases;
        final boolean isUniversalWildcard;
        @Nullable String hostName;
        @Nullable String user;
        int port = SshProfile.DEFAULT_PORT;
        boolean hasPort = false;
        @Nullable String identityFile;
        @Nullable String proxyJump;
        int serverAliveIntervalSeconds = 0;
        boolean hasServerAliveInterval = false;
        @Nullable Boolean pubkeyAuthentication;

        HostBlockBuilder(@NonNull List<String> rawPatterns, @NonNull List<String> concreteAliases) {
            this.rawPatterns = rawPatterns;
            this.concreteAliases = concreteAliases;
            this.isUniversalWildcard = isUniversalHostStar(rawPatterns);
        }
    }

    private SshConfigImporter() {
    }

    @NonNull
    public static List<ImportCandidate> parse(
            @NonNull File configFile,
            @NonNull File homeDir,
            @Nullable List<SshProfile> existingProfiles) throws IOException {
        if (!configFile.isFile()) {
            return Collections.emptyList();
        }
        String raw = SshFsUtils.readUtf8(configFile);
        return parseContent(raw, homeDir, existingProfiles);
    }

    @NonNull
    public static List<ImportCandidate> parseContent(
            @NonNull String configContent,
            @NonNull File homeDir,
            @Nullable List<SshProfile> existingProfiles) {
        List<HostBlockBuilder> allHostBlocks = new ArrayList<>();
        SectionKind sectionKind = SectionKind.NONE;
        HostBlockBuilder currentBlock = null;

        try (BufferedReader reader = new BufferedReader(new StringReader(configContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String cleaned = stripComment(line).trim();
                if (cleaned.isEmpty()) {
                    continue;
                }
                Directive directive = parseDirective(cleaned);
                if (directive == null) {
                    continue;
                }
                String key = directive.keyword;
                if ("host".equals(key)) {
                    if (currentBlock != null) {
                        allHostBlocks.add(currentBlock);
                        currentBlock = null;
                    }
                    List<String> patterns = tokenizeRespectingQuotes(directive.rawValue);
                    List<String> concreteAliases = extractConcreteAliases(patterns);
                    if (patterns.isEmpty()) {
                        sectionKind = SectionKind.SKIP;
                    } else {
                        sectionKind = SectionKind.HOST;
                        currentBlock = new HostBlockBuilder(patterns, concreteAliases);
                    }
                } else if ("match".equals(key)) {
                    if (currentBlock != null) {
                        allHostBlocks.add(currentBlock);
                        currentBlock = null;
                    }
                    sectionKind = SectionKind.SKIP;
                } else if (sectionKind == SectionKind.HOST && currentBlock != null) {
                    applyDirective(currentBlock, key, directive.rawValue, homeDir);
                }
            }
        } catch (IOException ignored) {
            // StringReader does not throw IOException during readLine
        }

        if (currentBlock != null) {
            allHostBlocks.add(currentBlock);
        }

        List<HostBlockBuilder> specificWildcardBlocks = new ArrayList<>();
        List<HostBlockBuilder> universalWildcardBlocks = new ArrayList<>();
        for (HostBlockBuilder block : allHostBlocks) {
            if (block.concreteAliases.isEmpty()) {
                if (block.isUniversalWildcard) {
                    universalWildcardBlocks.add(block);
                } else {
                    specificWildcardBlocks.add(block);
                }
            }
        }

        List<ImportCandidate> candidates = new ArrayList<>();
        for (HostBlockBuilder block : allHostBlocks) {
            if (!block.concreteAliases.isEmpty()) {
                emitCandidatesForBlock(
                    block,
                    specificWildcardBlocks,
                    universalWildcardBlocks,
                    existingProfiles,
                    candidates
                );
            }
        }
        return Collections.unmodifiableList(candidates);
    }

    @Nullable
    public static SshProfile findMatchingProfile(
            @NonNull SshProfile candidate,
            @Nullable List<SshProfile> existingProfiles) {
        if (existingProfiles == null || existingProfiles.isEmpty()) {
            return null;
        }
        for (SshProfile existing : existingProfiles) {
            if (existing != null && isDuplicate(candidate, existing)) {
                return existing;
            }
        }
        return null;
    }

    public static boolean isDuplicate(@NonNull SshProfile candidate, @NonNull SshProfile existing) {
        if (!candidate.host.isEmpty()
            && candidate.host.equalsIgnoreCase(existing.host)
            && candidate.port == existing.port
            && candidate.username.equals(existing.username)) {
            return true;
        }
        String candidateName = candidate.displayName.trim();
        if (!candidateName.isEmpty()) {
            String existingDisplayName = existing.displayName.trim();
            if (!existingDisplayName.isEmpty() && candidateName.equalsIgnoreCase(existingDisplayName)) {
                return true;
            }
            String existingEffectiveName = existing.getEffectiveDisplayName().trim();
            if (!existingEffectiveName.isEmpty() && candidateName.equalsIgnoreCase(existingEffectiveName)) {
                return true;
            }
        }
        return false;
    }

    private static void emitCandidatesForBlock(
            @NonNull HostBlockBuilder block,
            @NonNull List<HostBlockBuilder> specificWildcardBlocks,
            @NonNull List<HostBlockBuilder> universalWildcardBlocks,
            @Nullable List<SshProfile> existingProfiles,
            @NonNull List<ImportCandidate> out) {
        if (block.concreteAliases.isEmpty()) {
            return;
        }
        boolean hasExplicitHostName = block.hostName != null && !block.hostName.isEmpty();
        boolean expandAllAliases = !hasExplicitHostName || block.hostName.contains("%h");
        int count = expandAllAliases ? block.concreteAliases.size() : 1;
        for (int i = 0; i < count; i++) {
            String alias = block.concreteAliases.get(i);
            List<HostBlockBuilder> fallbackChain = collectMatchingFallbacks(
                alias, block, specificWildcardBlocks, universalWildcardBlocks);

            String rawHostName = firstNonEmptyHostName(fallbackChain);
            String resolvedHost = rawHostName != null
                ? rawHostName.replace("%h", alias)
                : alias;
            if (resolvedHost.isEmpty() || resolvedHost.startsWith("-") || containsWhitespace(resolvedHost)) {
                continue;
            }

            String user = firstNonNullUser(fallbackChain);
            int port = firstSetPort(fallbackChain);
            String identityFile = firstNonNullIdentityFile(fallbackChain);
            String proxyJump = firstNonNullProxyJump(fallbackChain);
            int keepAlive = firstSetKeepAlive(fallbackChain);
            Boolean pubkeyAuth = firstSetPubkeyAuth(fallbackChain);

            SshAuthType authType = Boolean.FALSE.equals(pubkeyAuth)
                ? SshAuthType.PASSWORD
                : SshAuthType.SSH_KEY;

            SshProfile profile = SshProfile.builder()
                .displayName(alias)
                .host(resolvedHost)
                .port(port)
                .username(user != null ? user : "")
                .authType(authType)
                .privateKeyPath(authType == SshAuthType.SSH_KEY && identityFile != null ? identityFile : "")
                .proxyJump(proxyJump != null ? proxyJump : "")
                .keepAliveIntervalSeconds(keepAlive)
                .build();
            SshProfile matching = findMatchingProfile(profile, existingProfiles);
            out.add(new ImportCandidate(alias, profile, matching != null, matching));
        }
    }

    @NonNull
    private static List<HostBlockBuilder> collectMatchingFallbacks(
            @NonNull String alias,
            @NonNull HostBlockBuilder primaryBlock,
            @NonNull List<HostBlockBuilder> specificWildcardBlocks,
            @NonNull List<HostBlockBuilder> universalWildcardBlocks) {
        List<HostBlockBuilder> chain = new ArrayList<>();
        chain.add(primaryBlock);
        for (HostBlockBuilder block : specificWildcardBlocks) {
            if (matchesHostPatterns(alias, block.rawPatterns)) {
                chain.add(block);
            }
        }
        for (HostBlockBuilder block : universalWildcardBlocks) {
            if (matchesHostPatterns(alias, block.rawPatterns)) {
                chain.add(block);
            }
        }
        return chain;
    }

    @Nullable
    private static String firstNonEmptyHostName(@NonNull List<HostBlockBuilder> chain) {
        for (HostBlockBuilder block : chain) {
            if (block.hostName != null && !block.hostName.isEmpty()) {
                return block.hostName;
            }
        }
        return null;
    }

    @Nullable
    private static String firstNonNullUser(@NonNull List<HostBlockBuilder> chain) {
        for (HostBlockBuilder block : chain) {
            if (block.user != null) {
                return block.user;
            }
        }
        return null;
    }

    private static int firstSetPort(@NonNull List<HostBlockBuilder> chain) {
        for (HostBlockBuilder block : chain) {
            if (block.hasPort) {
                return block.port;
            }
        }
        return SshProfile.DEFAULT_PORT;
    }

    @Nullable
    private static String firstNonNullIdentityFile(@NonNull List<HostBlockBuilder> chain) {
        for (HostBlockBuilder block : chain) {
            if (block.identityFile != null) {
                return block.identityFile;
            }
        }
        return null;
    }

    @Nullable
    private static String firstNonNullProxyJump(@NonNull List<HostBlockBuilder> chain) {
        for (HostBlockBuilder block : chain) {
            if (block.proxyJump != null) {
                return block.proxyJump;
            }
        }
        return null;
    }

    private static int firstSetKeepAlive(@NonNull List<HostBlockBuilder> chain) {
        for (HostBlockBuilder block : chain) {
            if (block.hasServerAliveInterval) {
                return block.serverAliveIntervalSeconds;
            }
        }
        return 0;
    }

    @Nullable
    private static Boolean firstSetPubkeyAuth(@NonNull List<HostBlockBuilder> chain) {
        for (HostBlockBuilder block : chain) {
            if (block.pubkeyAuthentication != null) {
                return block.pubkeyAuthentication;
            }
        }
        return null;
    }

    private static boolean isUniversalHostStar(@NonNull List<String> patterns) {
        if (patterns.isEmpty()) {
            return false;
        }
        boolean sawStar = false;
        for (String p : patterns) {
            if ("*".equals(p)) {
                sawStar = true;
            } else {
                return false;
            }
        }
        return sawStar;
    }

    static boolean matchesHostPatterns(@NonNull String alias, @NonNull List<String> patterns) {
        boolean matchedPositive = false;
        for (String pattern : patterns) {
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.startsWith("!")) {
                String negated = pattern.substring(1);
                if (!negated.isEmpty() && globMatches(alias, negated)) {
                    return false;
                }
            } else if (globMatches(alias, pattern)) {
                matchedPositive = true;
            }
        }
        return matchedPositive;
    }

    private static boolean globMatches(@NonNull String text, @NonNull String pattern) {
        return globMatchesAt(text, 0, pattern, 0);
    }

    private static boolean globMatchesAt(
            @NonNull String text,
            int ti,
            @NonNull String pattern,
            int pi) {
        while (pi < pattern.length()) {
            char pc = pattern.charAt(pi);
            if (pc == '*') {
                while (pi < pattern.length() && pattern.charAt(pi) == '*') {
                    pi++;
                }
                if (pi == pattern.length()) {
                    return true;
                }
                for (int k = ti; k <= text.length(); k++) {
                    if (globMatchesAt(text, k, pattern, pi)) {
                        return true;
                    }
                }
                return false;
            } else if (pc == '?') {
                if (ti >= text.length()) {
                    return false;
                }
                ti++;
                pi++;
            } else {
                if (ti >= text.length() || text.charAt(ti) != pc) {
                    return false;
                }
                ti++;
                pi++;
            }
        }
        return ti == text.length();
    }

    private static void applyDirective(
            @NonNull HostBlockBuilder block,
            @NonNull String keyword,
            @NonNull String rawValue,
            @NonNull File homeDir) {
        String value = extractFirstValue(rawValue);
        if (value.isEmpty()) {
            return;
        }
        switch (keyword) {
            case "hostname":
                if (block.hostName == null) {
                    block.hostName = value;
                }
                break;
            case "user":
                if (block.user == null && !value.startsWith("-") && !containsWhitespace(value)) {
                    block.user = value;
                }
                break;
            case "port":
                if (!block.hasPort) {
                    Integer parsedPort = parsePositivePort(value);
                    if (parsedPort != null) {
                        block.port = parsedPort;
                        block.hasPort = true;
                    }
                }
                break;
            case "identityfile":
                if (block.identityFile == null) {
                    block.identityFile = "none".equalsIgnoreCase(value) ? "" : expandTildePath(value, homeDir);
                }
                break;
            case "proxyjump":
                if (block.proxyJump == null) {
                    block.proxyJump = "none".equalsIgnoreCase(value) ? "" : value;
                }
                break;
            case "serveraliveinterval":
                if (!block.hasServerAliveInterval) {
                    Integer interval = parseNonNegativeInt(value);
                    if (interval != null) {
                        block.serverAliveIntervalSeconds = interval;
                        block.hasServerAliveInterval = true;
                    }
                }
                break;
            case "pubkeyauthentication":
                if (block.pubkeyAuthentication == null) {
                    if ("no".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                        block.pubkeyAuthentication = Boolean.FALSE;
                    } else if ("yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
                        block.pubkeyAuthentication = Boolean.TRUE;
                    }
                }
                break;
            default:
                break;
        }
    }

    @NonNull
    private static String expandTildePath(@NonNull String path, @NonNull File homeDir) {
        if ("~".equals(path)) {
            return homeDir.getAbsolutePath();
        }
        if (path.startsWith("~/")) {
            return new File(homeDir, path.substring(2)).getAbsolutePath();
        }
        return path;
    }

    @Nullable
    private static Integer parsePositivePort(@NonNull String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port >= SshProfileValidator.MIN_PORT && port <= SshProfileValidator.MAX_PORT) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    @Nullable
    private static Integer parseNonNegativeInt(@NonNull String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    @NonNull
    private static List<String> extractConcreteAliases(@NonNull List<String> tokens) {
        List<String> concrete = new ArrayList<>();
        for (String token : tokens) {
            if (token.isEmpty()
                || token.contains("*")
                || token.contains("?")
                || token.startsWith("!")
                || token.startsWith("-")
                || containsWhitespace(token)) {
                continue;
            }
            concrete.add(token);
        }
        return concrete;
    }

    @NonNull
    private static String extractFirstValue(@NonNull String rawValue) {
        List<String> tokens = tokenizeRespectingQuotes(rawValue);
        return tokens.isEmpty() ? "" : tokens.get(0).trim();
    }

    @NonNull
    private static List<String> tokenizeRespectingQuotes(@NonNull String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasTokenContent = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                hasTokenContent = true;
            } else if (!inQuotes && Character.isWhitespace(c)) {
                if (hasTokenContent) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasTokenContent = false;
                }
            } else {
                current.append(c);
                hasTokenContent = true;
            }
        }
        if (hasTokenContent) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    @NonNull
    private static String stripComment(@NonNull String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '#' && !inQuotes && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    @Nullable
    private static Directive parseDirective(@NonNull String line) {
        int sep = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c) || c == '=') {
                sep = i;
                break;
            }
        }
        if (sep <= 0) {
            return null;
        }
        String keyword = line.substring(0, sep).toLowerCase(Locale.ROOT);
        int pos = sep;
        while (pos < line.length() && Character.isWhitespace(line.charAt(pos))) {
            pos++;
        }
        if (pos < line.length() && line.charAt(pos) == '=') {
            pos++;
            while (pos < line.length() && Character.isWhitespace(line.charAt(pos))) {
                pos++;
            }
        }
        if (pos >= line.length()) {
            return null;
        }
        return new Directive(keyword, line.substring(pos).trim());
    }

    private static boolean containsWhitespace(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static final class Directive {
        @NonNull final String keyword;
        @NonNull final String rawValue;

        Directive(@NonNull String keyword, @NonNull String rawValue) {
            this.keyword = keyword;
            this.rawValue = rawValue;
        }
    }
}
