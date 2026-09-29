#!/usr/bin/env bash
# Prints the CHANGELOG.md section for the given version (without its heading).
# Exits 1 if the section is missing or empty.
set -euo pipefail
version="$1"
notes=$(awk -v v="$version" '
  /^## \[/ { if (found) exit; if (index($0, "## [" v "]") == 1) { found = 1; next } }
  found { print }
' "$(dirname "$0")/../CHANGELOG.md" | sed -e '/./,$!d')
[ -n "$(echo "$notes" | tr -d '[:space:]')" ] || exit 1
printf '%s\n' "$notes"
cat <<'FOOTER'

---
**Install / update:** download the APK below on your phone and open it. Updates install over the
previous version — no need to uninstall (your library, downloads and settings are kept).
FOOTER
