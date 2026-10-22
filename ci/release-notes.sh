#!/usr/bin/env bash
# Prints release notes in Markdown.
# Usage: ci/release-notes.sh <version>   (e.g. 0.1.0)  -> that CHANGELOG section + commits since previous tag
#        ci/release-notes.sh dev                         -> [Unreleased] section + commits since last tag
set -euo pipefail
ver="$1"
section() {
  awk -v h="$1" '
    $0 ~ "^## \\[" h "\\]" { on=1; next }
    on && /^## \[/ { exit }
    on { print }
  ' CHANGELOG.md | sed -e '/./,$!d'
}
if [ "$ver" = "dev" ]; then
  last=$(git describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)
  echo "Automatic build of the latest commit on \`main\` (\`$(git rev-parse --short HEAD)\`)."
  echo "Untested development build — for stable versions see the \`v*\` releases."
  echo
  body=$(section "Unreleased")
  if [ -n "$(echo "$body" | tr -d '[:space:]')" ]; then
    echo "## Changes not yet released"; echo; echo "$body"; echo
  fi
  range=${last:+$last..HEAD}
  echo "## Commits${last:+ since $last}"
  echo
  git log --no-merges --format='- %s (%h)' ${range:-HEAD} | grep -v '\[skip ci\]' | head -60
else
  prev=$(git describe --tags --abbrev=0 --match 'v[0-9]*' "v$ver^" 2>/dev/null || true)
  body=$(section "$ver")
  if [ -n "$body" ]; then echo "$body"; else echo "_No changelog entry for $ver._"; fi
  echo
  echo "<details><summary>Commits${prev:+ since $prev}</summary>"
  echo
  git log --no-merges --format='- %s (%h)' ${prev:+$prev..}"v$ver" | grep -v '\[skip ci\]'
  echo
  echo "</details>"
  echo
  echo "**Install:** download the APK below and install it over the previous version (your imported game and saves are kept)."
fi
