#!/usr/bin/env bash
# Runs a build command, keeps the full log as an artifact and, on failure,
# reports the tail of the log as a GitHub annotation (visible in the run summary).
# Usage: ci/run.sh <name> <command...>
set -o pipefail
name="$1"; shift
mkdir -p "$GITHUB_WORKSPACE/ci-logs"
log="$GITHUB_WORKSPACE/ci-logs/$name.log"
"$@" 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
if [ "$rc" -ne 0 ]; then
  esc() { sed -e 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}'; }
  # Most useful lines: error lines first, then the raw tail
  errs=$(grep -nE "error:|Error |ERROR|FAILED|What went wrong|undefined reference|No such file|not found|fatal|\*\*\*|No rule|Android NDK:" "$log" | tail -40)
  first=$(grep -nE '\*\*\* \[' "$log" | head -1 | cut -d: -f1)
  if [ -n "$first" ]; then
    ctx=$(sed -n "$((first>50 ? first-80 : 1)),$((first))p" "$log" | grep -vE "^(make|  CC|compiling|installing)" | tail -60)
    echo "::error title=$name (context before first make error)::$(printf '%s' "$ctx" | cut -c1-400 | esc)"
  fi
  wrong=$(grep -A 25 "What went wrong" "$log" | grep -v "^\s*at " | head -40)
  tail_=$(grep -v "^\s*at " "$log" | tail -60)
  key=$(grep -E '\*\*\*|No rule|Android NDK:|: error:|undefined (reference|symbol)|cannot find' "$log" | grep -vE '^\s*/usr' | tail -15 | cut -c1-250)
  [ -n "$key" ] && echo "::error title=$name (key errors)::$(printf '%s' "$key" | esc)"
  echo "::error title=$name (errors)::$(printf '%s' "$errs" | cut -c1-300 | esc)"
  [ -n "$wrong" ] && echo "::error title=$name (what went wrong)::$(printf '%s' "$wrong" | cut -c1-300 | esc)"
  echo "::error title=$name (tail)::$(printf '%s' "$tail_" | cut -c1-300 | esc)"
fi
exit "$rc"
