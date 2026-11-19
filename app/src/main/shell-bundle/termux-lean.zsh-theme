# Termux Lean — minimal mobile-friendly Nerd Font prompt for Oh My Zsh
# Uses standard ANSI colors so it adapts cleanly across all 7 built-in palettes
# (including Catppuccin Latte light theme and OLED Pitch Black).

setopt prompt_subst

_termux_lean_git_prompt() {
  command -v git >/dev/null 2>&1 || return 0

  local in_work_tree=""
  in_work_tree=$(GIT_OPTIONAL_LOCKS=0 command git rev-parse --is-inside-work-tree 2>/dev/null) || return 0
  [[ "$in_work_tree" == "true" ]] || return 0

  # 1. symbolic-ref handles both normal branches and unborn HEAD (0 commits)
  # 2. exact tag match for detached HEAD on a tag
  # 3. short commit SHA for detached HEAD
  local ref=""
  ref=$(GIT_OPTIONAL_LOCKS=0 command git symbolic-ref --short HEAD 2>/dev/null) \
    || ref=$(GIT_OPTIONAL_LOCKS=0 command git describe --tags --exact-match HEAD 2>/dev/null) \
    || ref=$(GIT_OPTIONAL_LOCKS=0 command git rev-parse --short HEAD 2>/dev/null) \
    || return 0

  # Truncate long branch names BEFORE escaping '%' so '%%' is never split
  if (( ${#ref} > 20 )); then
    ref="${ref[1,19]}…"
  fi
  ref=${ref//\%/%%}

  local dirty="" git_status=""
  git_status=$(GIT_OPTIONAL_LOCKS=0 command git status --porcelain --ignore-submodules=dirty 2>/dev/null) || git_status=""
  [[ -n "$git_status" ]] && dirty="%F{yellow}*%f"

  # U+E0A0 (nf-pl-branch)
  print -rn -- " %F{blue} ${ref}%f${dirty}"
}

# U+F07C (nf-fa-folder_open), U+2026 (…), U+276F (❯)
PROMPT='%B%F{cyan} %(3~|…/%2~|%~)%f%b$(_termux_lean_git_prompt) %(?.%B%F{green}❯%f%b.%B%F{red}❯%f%b) '
