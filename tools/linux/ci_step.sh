#!/usr/bin/env bash
# CI helper, used as a custom step shell: `shell: bash .../ci_step.sh NAME {0}`.
# Runs the step's script, shows its output and, if it fails, repeats the last lines as a
# GitHub annotation (readable from the API without access to the full logs).
set -uo pipefail
name="$1"; script="$2"
log=$(mktemp)
bash -eo pipefail "$script" 2>&1 | tee "$log"
code=${PIPESTATUS[0]}
if [ "$code" != 0 ]; then
  msg=$(tail -n 60 "$log" | cut -c1-300 | sed 's/%/%25/g' | sed ':a;N;$!ba;s/\n/%0A/g')
  echo "::error title=$name failed::$msg"
fi
exit "$code"
