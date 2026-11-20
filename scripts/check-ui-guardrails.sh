#!/usr/bin/env bash
set -euo pipefail

# Mechanical UI & Material 3 migration guardrails enforced in CI.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

check_absent() {
    local description="$1"
    local pattern="$2"
    shift 2
    local matches
    matches="$(grep -rnE "$pattern" "$@" || true)"
    if [[ -n "$matches" ]]; then
        echo "FAIL: $description"
        echo "$matches"
        failures=$((failures + 1))
    fi
}

check_absent \
    "Legacy android.app.AlertDialog import is banned (use MaterialAlertDialogBuilder)" \
    '^import android\.app\.AlertDialog;' \
    app/src/main termux-shared/src/main terminal-view/src/main

check_absent \
    "Legacy android.app.ProgressDialog is banned (use Material 3 BootstrapProgressDialog)" \
    '(^import android\.app\.ProgressDialog;|\bProgressDialog\.show\(|new (android\.app\.)?ProgressDialog\b)' \
    app/src/main termux-shared/src/main

check_absent \
    "Legacy Material 2 red accents (red_400/red_800) are banned in layouts, styles, and themes" \
    '@color/red_(400|800)' \
    app/src/main/res termux-shared/src/main/res

if (( failures > 0 )); then
    echo "UI guardrails failed ($failures violation group(s))."
    exit 1
fi

echo "PASS: UI & Material 3 guardrails clean."
