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
  # Most useful lines: error lines first, then the raw tail
  errs=$(grep -nE "error:|Error |ERROR|FAILED|What went wrong|undefined reference|No such file|not found|fatal" "$log" | tail -40)
  wrong=$(grep -A 25 "What went wrong" "$log" | grep -v "^\s*at " | head -40)
  tail_=$(grep -v "^\s*at " "$log" | tail -60)
  esc() { sed -e 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}'; }
  echo "::error title=$name (errors)::$(printf '%s' "$errs" | cut -c1-300 | esc)"
  [ -n "$wrong" ] && echo "::error title=$name (what went wrong)::$(printf '%s' "$wrong" | cut -c1-300 | esc)"
  echo "::error title=$name (tail)::$(printf '%s' "$tail_" | cut -c1-300 | esc)"
fi
exit "$rc"
