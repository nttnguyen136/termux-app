package com.termux.app.shell;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages user shell dotfiles ({@code ~/.zshrc}, marker-delimited Bash fallback prompt in {@code ~/.bashrc},
 * {@code ~/.termux/shell} symlink, and {@code ~/.oh-my-zsh} status/apply/reset hooks) with strict
 * non-destructive upgrade safety.
 */
public final class ShellConfigManager {

    private static final String LOG_TAG = "ShellConfigManager";

    public static final String BASH_PROMPT_START_MARKER = "# >>> termux-modern-bash-prompt >>>";
    public static final String BASH_PROMPT_END_MARKER = "# <<< termux-modern-bash-prompt <<<";

    public static final String DEFAULT_ZSHRC_CONTENT =
        "# Termux Modern Shell default configuration\n" +
        "# Edit freely — upgrades will never overwrite your custom ~/.zshrc.\n" +
        "\n" +
        "export ZSH=\"$HOME/.oh-my-zsh\"\n" +
        "\n" +
        "# Compact mobile-friendly Nerd Font prompt\n" +
        "ZSH_THEME=\"termux-lean\"\n" +
        "\n" +
        "# Offline-safe Oh My Zsh settings\n" +
        "zstyle ':omz:update' mode disabled\n" +
        "DISABLE_AUTO_UPDATE=\"true\"\n" +
        "DISABLE_UPDATE_PROMPT=\"true\"\n" +
        "ZSH_DISABLE_COMPFIX=\"true\"\n" +
        "\n" +
        "# Bundled plugins (zsh-syntax-highlighting must remain last)\n" +
        "plugins=(\n" +
        "  zsh-autosuggestions\n" +
        "  zsh-syntax-highlighting\n" +
        ")\n" +
        "\n" +
        "if [[ -r \"$ZSH/oh-my-zsh.sh\" ]]; then\n" +
        "  source \"$ZSH/oh-my-zsh.sh\"\n" +
        "fi\n";

    public static final String DEFAULT_TERMUX_LEAN_THEME_CONTENT =
        "# Termux Lean — minimal mobile-friendly Nerd Font prompt for Oh My Zsh\n" +
        "# Uses standard ANSI colors so it adapts cleanly across all 7 built-in palettes\n" +
        "# (including Catppuccin Latte light theme and OLED Pitch Black).\n" +
        "\n" +
        "setopt prompt_subst\n" +
        "\n" +
        "_termux_lean_git_prompt() {\n" +
        "  command -v git >/dev/null 2>&1 || return 0\n" +
        "\n" +
        "  local in_work_tree=\"\"\n" +
        "  in_work_tree=$(GIT_OPTIONAL_LOCKS=0 command git rev-parse --is-inside-work-tree 2>/dev/null) || return 0\n" +
        "  [[ \"$in_work_tree\" == \"true\" ]] || return 0\n" +
        "\n" +
        "  # 1. symbolic-ref handles both normal branches and unborn HEAD (0 commits)\n" +
        "  # 2. exact tag match for detached HEAD on a tag\n" +
        "  # 3. short commit SHA for detached HEAD\n" +
        "  local ref=\"\"\n" +
        "  ref=$(GIT_OPTIONAL_LOCKS=0 command git symbolic-ref --short HEAD 2>/dev/null) \\\n" +
        "    || ref=$(GIT_OPTIONAL_LOCKS=0 command git describe --tags --exact-match HEAD 2>/dev/null) \\\n" +
        "    || ref=$(GIT_OPTIONAL_LOCKS=0 command git rev-parse --short HEAD 2>/dev/null) \\\n" +
        "    || return 0\n" +
        "\n" +
        "  # Truncate long branch names BEFORE escaping '%' so '%%' is never split\n" +
        "  if (( ${#ref} > 20 )); then\n" +
        "    ref=\"${ref[1,19]}…\"\n" +
        "  fi\n" +
        "  ref=${ref//\\%/%%}\n" +
        "\n" +
        "  local dirty=\"\" git_status=\"\"\n" +
        "  git_status=$(GIT_OPTIONAL_LOCKS=0 command git status --porcelain --ignore-submodules=dirty 2>/dev/null) || git_status=\"\"\n" +
        "  [[ -n \"$git_status\" ]] && dirty=\"%F{yellow}*%f\"\n" +
        "\n" +
        "  # U+E0A0 (nf-pl-branch)\n" +
        "  print -rn -- \" %F{blue}\ue0a0 ${ref}%f${dirty}\"\n" +
        "}\n" +
        "\n" +
        "# U+F07C (nf-fa-folder_open), U+2026 (…), U+276F (❯)\n" +
        "PROMPT='%B%F{cyan}\uf07c %(3~|…/%2~|%~)%f%b$(_termux_lean_git_prompt) %(?.%B%F{green}\u276f%f%b.%B%F{red}\u276f%f%b) '\n";

    public static final String MANAGED_BASH_PROMPT_BLOCK =
        BASH_PROMPT_START_MARKER + "\n" +
        "__termux_modern_prompt() {\n" +
        "  local last_exit=$?\n" +
        "  local dir_str=\"$PWD\"\n" +
        "  if [[ \"$dir_str\" == \"$HOME\" ]]; then\n" +
        "    dir_str=\"~\"\n" +
        "  elif [[ \"$dir_str\" == \"$HOME/\"* ]]; then\n" +
        "    dir_str=\"~/${dir_str#\"$HOME/\"}\"\n" +
        "  fi\n" +
        "  local trimmed=\"${dir_str#/}\"\n" +
        "  local slash_check=\"${trimmed#*/}\"\n" +
        "  if [[ \"$dir_str\" != \"~\" && \"$dir_str\" != \"/\" && \"$slash_check\" != \"$trimmed\" ]]; then\n" +
        "    local rest=\"${slash_check#*/}\"\n" +
        "    if [[ \"$rest\" != \"$slash_check\" ]]; then\n" +
        "      local base=\"${dir_str##*/}\"\n" +
        "      local parent_path=\"${dir_str%/*}\"\n" +
        "      local parent=\"${parent_path##*/}\"\n" +
        "      dir_str=\"…/${parent}/${base}\"\n" +
        "    fi\n" +
        "  fi\n" +
        "  dir_str=\"${dir_str//\\\\/\\\\\\\\}\"\n" +
        "  dir_str=\"${dir_str//$/\\\\$}\"\n" +
        "  dir_str=\"${dir_str//\\`/\\\\\\`}\"\n" +
        "\n" +
        "  local git_seg=\"\"\n" +
        "  if command -v git >/dev/null 2>&1; then\n" +
        "    local in_wt=\"\"\n" +
        "    in_wt=$(GIT_OPTIONAL_LOCKS=0 command git rev-parse --is-inside-work-tree 2>/dev/null) || in_wt=\"\"\n" +
        "    if [[ \"$in_wt\" == \"true\" ]]; then\n" +
        "      local branch=\"\"\n" +
        "      branch=$(GIT_OPTIONAL_LOCKS=0 command git symbolic-ref --short HEAD 2>/dev/null) \\\n" +
        "        || branch=$(GIT_OPTIONAL_LOCKS=0 command git describe --tags --exact-match HEAD 2>/dev/null) \\\n" +
        "        || branch=$(GIT_OPTIONAL_LOCKS=0 command git rev-parse --short HEAD 2>/dev/null) \\\n" +
        "        || branch=\"\"\n" +
        "      if [[ -n \"$branch\" ]]; then\n" +
        "        if (( ${#branch} > 20 )); then\n" +
        "          branch=\"${branch:0:19}…\"\n" +
        "        fi\n" +
        "        branch=\"${branch//\\\\/\\\\\\\\}\"\n" +
        "        branch=\"${branch//$/\\\\$}\"\n" +
        "        branch=\"${branch//\\`/\\\\\\`}\"\n" +
        "        local dirty=\"\" git_st=\"\"\n" +
        "        git_st=$(GIT_OPTIONAL_LOCKS=0 command git status --porcelain --ignore-submodules=dirty 2>/dev/null) || git_st=\"\"\n" +
        "        if [[ -n \"$git_st\" ]]; then\n" +
        "          dirty='\\[\\033[0;33m\\]*\\[\\033[0m\\]'\n" +
        "        fi\n" +
        "        git_seg=\" \\[\\033[0;34m\\]\ue0a0 ${branch}\\[\\033[0m\\]${dirty}\"\n" +
        "      fi\n" +
        "    fi\n" +
        "  fi\n" +
        "\n" +
        "  local arrow\n" +
        "  if (( last_exit == 0 )); then\n" +
        "    arrow='\\[\\033[1;32m\\]\u276f\\[\\033[0m\\]'\n" +
        "  else\n" +
        "    arrow='\\[\\033[1;31m\\]\u276f\\[\\033[0m\\]'\n" +
        "  fi\n" +
        "\n" +
        "  PS1=\"\\[\\033[1;36m\\]\uf07c ${dir_str}\\[\\033[0m\\]${git_seg} ${arrow} \"\n" +
        "  return \"$last_exit\"\n" +
        "}\n" +
        "if [[ \"$(declare -p PROMPT_COMMAND 2>/dev/null)\" == \"declare -a \"* ]]; then\n" +
        "  case \" ${PROMPT_COMMAND[*]} \" in\n" +
        "    *\" __termux_modern_prompt \"*) ;;\n" +
        "    *) PROMPT_COMMAND=(__termux_modern_prompt \"${PROMPT_COMMAND[@]}\") ;;\n" +
        "  esac\n" +
        "elif [[ \";${PROMPT_COMMAND:-};\" != *\";__termux_modern_prompt;\"* ]]; then\n" +
        "  PROMPT_COMMAND=\"__termux_modern_prompt${PROMPT_COMMAND:+;${PROMPT_COMMAND#;}}\"\n" +
        "fi\n" +
        BASH_PROMPT_END_MARKER;

    private ShellConfigManager() {
    }

    /**
     * Value object representing the current state of the user's shell, Oh My Zsh installation,
     * dotfiles customization, and welcome banner.
     */
    public static final class ShellStatus {
        public final String defaultShellPath;
        public final boolean isZshInstalled;
        public final boolean isZshDefault;
        public final boolean isOhMyZshInstalled;
        public final boolean arePluginsInstalled;
        public final boolean hasCustomZshrc;
        public final boolean hasCustomBashrc;
        public final boolean welcomeBannerEnabled;

        public ShellStatus(
                @NonNull String defaultShellPath,
                boolean isZshInstalled,
                boolean isZshDefault,
                boolean isOhMyZshInstalled,
                boolean arePluginsInstalled,
                boolean hasCustomZshrc,
                boolean hasCustomBashrc,
                boolean welcomeBannerEnabled) {
            this.defaultShellPath = defaultShellPath;
            this.isZshInstalled = isZshInstalled;
            this.isZshDefault = isZshDefault;
            this.isOhMyZshInstalled = isOhMyZshInstalled;
            this.arePluginsInstalled = arePluginsInstalled;
            this.hasCustomZshrc = hasCustomZshrc;
            this.hasCustomBashrc = hasCustomBashrc;
            this.welcomeBannerEnabled = welcomeBannerEnabled;
        }

        @NonNull
        public String getDefaultShellPath() {
            return defaultShellPath;
        }

        public boolean isZshInstalled() {
            return isZshInstalled;
        }

        public boolean isZshDefault() {
            return isZshDefault;
        }

        public boolean isOhMyZshInstalled() {
            return isOhMyZshInstalled;
        }

        public boolean arePluginsInstalled() {
            return arePluginsInstalled;
        }

        public boolean hasCustomZshrc() {
            return hasCustomZshrc;
        }

        public boolean hasCustomBashrc() {
            return hasCustomBashrc;
        }

        public boolean isWelcomeBannerEnabled() {
            return welcomeBannerEnabled;
        }
    }

    private static final class BlockRange {
        final int startOffset;
        final int endOffsetWithNewline;

        BlockRange(int startOffset, int endOffsetWithNewline) {
            this.startOffset = startOffset;
            this.endOffsetWithNewline = endOffsetWithNewline;
        }
    }

    private static final class ParseResult {
        final boolean malformed;
        final List<BlockRange> blocks;

        ParseResult(boolean malformed, List<BlockRange> blocks) {
            this.malformed = malformed;
            this.blocks = blocks;
        }
    }

    @NonNull
    private static ParseResult parseBashMarkerBlocks(@NonNull String content) {
        List<BlockRange> blocks = new ArrayList<>();
        int len = content.length();
        int idx = 0;
        int currentBlockStart = -1;

        while (idx < len) {
            int lineStart = idx;
            int lineEnd = idx;
            while (lineEnd < len && content.charAt(lineEnd) != '\n' && content.charAt(lineEnd) != '\r') {
                lineEnd++;
            }
            int nextIdx = lineEnd;
            if (nextIdx < len) {
                if (content.charAt(nextIdx) == '\r' && nextIdx + 1 < len && content.charAt(nextIdx + 1) == '\n') {
                    nextIdx += 2;
                } else {
                    nextIdx += 1;
                }
            }
            String trimmedLine = content.substring(lineStart, lineEnd).trim();
            if (BASH_PROMPT_START_MARKER.equals(trimmedLine)) {
                if (currentBlockStart != -1) {
                    // Nested start marker without closing previous block -> malformed
                    return new ParseResult(true, blocks);
                }
                currentBlockStart = lineStart;
            } else if (BASH_PROMPT_END_MARKER.equals(trimmedLine)) {
                if (currentBlockStart == -1) {
                    // Orphaned end marker without start marker -> malformed
                    return new ParseResult(true, blocks);
                }
                blocks.add(new BlockRange(currentBlockStart, nextIdx));
                currentBlockStart = -1;
            }
            idx = nextIdx;
        }

        if (currentBlockStart != -1) {
            // Unclosed start marker -> malformed
            return new ParseResult(true, blocks);
        }
        return new ParseResult(false, blocks);
    }

    public static boolean hasBashPromptBlock(@NonNull File bashrcFile) {
        if (!bashrcFile.isFile()) {
            return false;
        }
        try {
            String content = ShellFsUtils.readUtf8(bashrcFile);
            ParseResult parsed = parseBashMarkerBlocks(content);
            return !parsed.malformed && !parsed.blocks.isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Inserts or updates the marker-delimited Bash fallback prompt block in {@code bashrcFile}
     * without modifying or deleting any user content outside the marker block.
     * If the file contains malformed (unclosed or orphaned) markers, refuses to modify the file
     * and returns {@code false} to guarantee zero user data loss.
     */
    public static boolean insertOrUpdateBashPromptBlock(@NonNull File bashrcFile) {
        try {
            if (!bashrcFile.exists()) {
                ShellFsUtils.writeUtf8(bashrcFile, MANAGED_BASH_PROMPT_BLOCK + "\n");
                return true;
            }
            if (!bashrcFile.isFile()) {
                return false;
            }
            String content = ShellFsUtils.readUtf8(bashrcFile);
            if (content.isEmpty()) {
                ShellFsUtils.writeUtf8(bashrcFile, MANAGED_BASH_PROMPT_BLOCK + "\n");
                return true;
            }

            ParseResult parsed = parseBashMarkerBlocks(content);
            if (parsed.malformed) {
                Logger.logWarn(LOG_TAG, "Refusing to modify " + bashrcFile.getAbsolutePath() + " due to malformed prompt markers.");
                return false;
            }

            if (parsed.blocks.isEmpty()) {
                StringBuilder sb = new StringBuilder(content.length() + MANAGED_BASH_PROMPT_BLOCK.length() + 4);
                sb.append(content);
                if (!content.endsWith("\n") && !content.endsWith("\r")) {
                    sb.append("\n");
                }
                String current = sb.toString();
                if (!current.endsWith("\n\n") && !current.endsWith("\r\n\r\n")) {
                    sb.append("\n");
                }
                sb.append(MANAGED_BASH_PROMPT_BLOCK).append("\n");
                ShellFsUtils.writeUtf8(bashrcFile, sb.toString());
                return true;
            }

            // Replace first valid block in-place and strip any duplicate valid blocks
            StringBuilder updated = new StringBuilder(content.length() + MANAGED_BASH_PROMPT_BLOCK.length());
            int cursor = 0;
            for (int i = 0; i < parsed.blocks.size(); i++) {
                BlockRange range = parsed.blocks.get(i);
                updated.append(content, cursor, range.startOffset);
                if (i == 0) {
                    updated.append(MANAGED_BASH_PROMPT_BLOCK).append("\n");
                }
                cursor = range.endOffsetWithNewline;
            }
            updated.append(content, cursor, content.length());
            String newContent = updated.toString();
            if (!newContent.equals(content)) {
                ShellFsUtils.writeUtf8(bashrcFile, newContent);
            }
            return true;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to update " + bashrcFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Removes the marker-delimited Bash fallback prompt block from {@code bashrcFile},
     * preserving all user lines outside the block. Returns {@code false} if the file or
     * valid marker block is not present, or if markers are malformed.
     */
    public static boolean removeBashPromptBlock(@NonNull File bashrcFile) {
        if (!bashrcFile.isFile()) {
            return false;
        }
        try {
            String content = ShellFsUtils.readUtf8(bashrcFile);
            ParseResult parsed = parseBashMarkerBlocks(content);
            if (parsed.malformed || parsed.blocks.isEmpty()) {
                return false;
            }

            String current = content;
            // Remove blocks from last to first so indices remain valid
            for (int i = parsed.blocks.size() - 1; i >= 0; i--) {
                BlockRange range = parsed.blocks.get(i);
                String before = current.substring(0, range.startOffset);
                String after = current.substring(range.endOffsetWithNewline);

                if (before.trim().isEmpty() && after.trim().isEmpty()) {
                    current = "";
                } else if (before.isEmpty()) {
                    after = stripOneLeadingBlankLine(after);
                    current = after;
                } else if (after.isEmpty()) {
                    before = stripOneTrailingBlankLine(before);
                    current = before;
                } else {
                    if (endsWithBlankLine(before)) {
                        after = stripOneLeadingBlankLine(after);
                    }
                    current = before + after;
                }
            }

            ShellFsUtils.writeUtf8(bashrcFile, current);
            return true;
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to remove prompt block from " + bashrcFile.getAbsolutePath(), e);
            return false;
        }
    }

    private static boolean endsWithBlankLine(@NonNull String s) {
        return s.endsWith("\n\n") || s.endsWith("\r\n\r\n");
    }

    @NonNull
    private static String stripOneLeadingBlankLine(@NonNull String s) {
        if (s.startsWith("\r\n")) {
            return s.substring(2);
        }
        if (s.startsWith("\n") || s.startsWith("\r")) {
            return s.substring(1);
        }
        return s;
    }

    @NonNull
    private static String stripOneTrailingBlankLine(@NonNull String s) {
        if (s.endsWith("\r\n\r\n")) {
            return s.substring(0, s.length() - 2);
        }
        if (s.endsWith("\n\n")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static boolean hasCustomZshrc(@NonNull File zshrcFile) {
        if (!zshrcFile.isFile()) {
            return false;
        }
        try {
            String raw = ShellFsUtils.readUtf8(zshrcFile);
            String normalized = normalizeLineEndings(raw).trim();
            if (normalized.isEmpty()) {
                return false;
            }
            String expected = normalizeLineEndings(DEFAULT_ZSHRC_CONTENT).trim();
            return !normalized.equals(expected);
        } catch (IOException e) {
            return true;
        }
    }

    public static boolean hasCustomBashrc(@NonNull File bashrcFile) {
        if (!bashrcFile.isFile()) {
            return false;
        }
        try {
            String content = ShellFsUtils.readUtf8(bashrcFile);
            if (content.trim().isEmpty()) {
                return false;
            }
            ParseResult parsed = parseBashMarkerBlocks(content);
            if (parsed.malformed) {
                return true;
            }
            if (parsed.blocks.isEmpty()) {
                return true;
            }
            StringBuilder outside = new StringBuilder();
            int cursor = 0;
            for (BlockRange range : parsed.blocks) {
                outside.append(content, cursor, range.startOffset);
                cursor = range.endOffsetWithNewline;
            }
            outside.append(content, cursor, content.length());
            return !outside.toString().trim().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }

    @NonNull
    private static String normalizeLineEndings(@NonNull String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    @NonNull
    public static ShellStatus getShellStatus(@NonNull Context context) {
        return getShellStatus(context, TermuxConstants.TERMUX_PREFIX_DIR, TermuxConstants.TERMUX_HOME_DIR);
    }

    @NonNull
    public static ShellStatus getShellStatus(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir) {
        File zshBin = new File(prefixDir, "bin/zsh");
        boolean isZshInstalled = zshBin.isFile() && zshBin.canExecute();

        String defaultShellPath = resolveDefaultShellPath(prefixDir, homeDir);
        boolean isZshDefault = isZshInstalled && "zsh".equals(new File(defaultShellPath).getName());

        File omzDir = new File(homeDir, ".oh-my-zsh");
        boolean isOhMyZshInstalled = new File(omzDir, "oh-my-zsh.sh").isFile();

        boolean hasAutosuggestions =
            new File(omzDir, "custom/plugins/zsh-autosuggestions/zsh-autosuggestions.plugin.zsh").isFile() ||
            new File(omzDir, "plugins/zsh-autosuggestions/zsh-autosuggestions.plugin.zsh").isFile();
        boolean hasSyntaxHighlighting =
            new File(omzDir, "custom/plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.plugin.zsh").isFile() ||
            new File(omzDir, "plugins/zsh-syntax-highlighting/zsh-syntax-highlighting.plugin.zsh").isFile();
        boolean arePluginsInstalled = hasAutosuggestions && hasSyntaxHighlighting;

        boolean customZshrc = hasCustomZshrc(new File(homeDir, ".zshrc"));
        boolean customBashrc = hasCustomBashrc(new File(homeDir, ".bashrc"));
        boolean welcomeBannerEnabled = WelcomeBannerManager.isWelcomeBannerEnabled(context, homeDir);

        return new ShellStatus(
            defaultShellPath,
            isZshInstalled,
            isZshDefault,
            isOhMyZshInstalled,
            arePluginsInstalled,
            customZshrc,
            customBashrc,
            welcomeBannerEnabled
        );
    }

    @NonNull
    private static String resolveDefaultShellPath(@NonNull File prefixDir, @NonNull File homeDir) {
        File shellLink = new File(new File(homeDir, ".termux"), "shell");
        if (shellLink.exists()) {
            try {
                File canonical = shellLink.getCanonicalFile();
                if (canonical.isFile() && canonical.canExecute()) {
                    return canonical.getAbsolutePath();
                }
            } catch (IOException ignored) {
            }
            String rawTarget = ShellFsUtils.readSymlinkTarget(shellLink);
            if (rawTarget != null) {
                File targetFile = new File(rawTarget);
                if (!targetFile.isAbsolute()) {
                    targetFile = new File(shellLink.getParentFile(), rawTarget);
                }
                if (targetFile.isFile() && targetFile.canExecute()) {
                    return targetFile.getAbsolutePath();
                }
            }
        }

        File bashBin = new File(prefixDir, "bin/bash");
        if (bashBin.isFile() && bashBin.canExecute()) {
            return bashBin.getAbsolutePath();
        }
        File shBin = new File(prefixDir, "bin/sh");
        if (shBin.isFile() && shBin.canExecute()) {
            return shBin.getAbsolutePath();
        }
        return bashBin.getAbsolutePath();
    }

    /**
     * Applies or re-applies the modern shell defaults.
     * When {@code forceOverwriteCustomDotfiles == false}, existing custom user {@code ~/.zshrc}
     * and custom lines in {@code ~/.bashrc} are strictly preserved.
     */
    @NonNull
    public static ShellStatus applyDefaults(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            boolean forceOverwriteCustomDotfiles) {
        ShellFsUtils.ensureDir0700(homeDir);
        File termuxDir = new File(homeDir, ".termux");
        ShellFsUtils.ensureDir0700(termuxDir);

        ZshBundleInstaller.ensureBundledAssetsExtractedIfNeeded(context, prefixDir, homeDir, false);

        File zshBin = new File(prefixDir, "bin/zsh");
        File shellLink = new File(termuxDir, "shell");
        if (zshBin.isFile() && zshBin.canExecute()) {
            if (!ShellFsUtils.existsOrIsSymlink(shellLink) || !shellLink.exists() || forceOverwriteCustomDotfiles) {
                ShellFsUtils.createSymlink(zshBin.getAbsolutePath(), shellLink, true);
            }
        }

        File zshrcFile = new File(homeDir, ".zshrc");
        if (!zshrcFile.exists() || !hasCustomZshrc(zshrcFile) || forceOverwriteCustomDotfiles) {
            try {
                ShellFsUtils.writeUtf8(zshrcFile, DEFAULT_ZSHRC_CONTENT);
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write " + zshrcFile.getAbsolutePath(), e);
            }
        }

        File bashrcFile = new File(homeDir, ".bashrc");
        insertOrUpdateBashPromptBlock(bashrcFile);

        WelcomeBannerManager.installDefaultBannerIfMissing(context, homeDir);

        return getShellStatus(context, prefixDir, homeDir);
    }

    /**
     * Resets the shell configuration to factory defaults.
     * When {@code forceOverwriteCustomDotfiles == true}, overwrites {@code ~/.zshrc} with the default
     * template, resets {@code ~/.bashrc} to the clean managed prompt block, re-extracts Oh My Zsh
     * theme/plugins, and restores the default 2-line welcome banner.
     * When {@code forceOverwriteCustomDotfiles == false}, preserves custom user {@code ~/.zshrc}
     * and user lines in {@code ~/.bashrc} as a defense-in-depth safety guard.
     */
    @NonNull
    public static ShellStatus resetToDefaults(
            @NonNull Context context,
            @NonNull File prefixDir,
            @NonNull File homeDir,
            boolean forceOverwriteCustomDotfiles) {
        ShellFsUtils.ensureDir0700(homeDir);
        File termuxDir = new File(homeDir, ".termux");
        ShellFsUtils.ensureDir0700(termuxDir);

        ZshBundleInstaller.ensureBundledAssetsExtractedIfNeeded(context, prefixDir, homeDir, true);

        File zshBin = new File(prefixDir, "bin/zsh");
        File shellLink = new File(termuxDir, "shell");
        if (zshBin.isFile() && zshBin.canExecute()) {
            ShellFsUtils.createSymlink(zshBin.getAbsolutePath(), shellLink, true);
        }

        File zshrcFile = new File(homeDir, ".zshrc");
        if (forceOverwriteCustomDotfiles || !zshrcFile.exists() || !hasCustomZshrc(zshrcFile)) {
            try {
                ShellFsUtils.writeUtf8(zshrcFile, DEFAULT_ZSHRC_CONTENT);
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to reset " + zshrcFile.getAbsolutePath(), e);
            }
        }

        File bashrcFile = new File(homeDir, ".bashrc");
        if (forceOverwriteCustomDotfiles) {
            try {
                ShellFsUtils.writeUtf8(bashrcFile, MANAGED_BASH_PROMPT_BLOCK + "\n");
            } catch (IOException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to reset " + bashrcFile.getAbsolutePath(), e);
            }
        } else {
            insertOrUpdateBashPromptBlock(bashrcFile);
        }

        WelcomeBannerManager.ensureWelcomeBannerScript(homeDir, true);
        WelcomeBannerManager.setWelcomeBannerEnabled(context, homeDir, true);

        return getShellStatus(context, prefixDir, homeDir);
    }
}
