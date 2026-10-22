#!/usr/bin/env python3
"""Usage: validate.py chunks/map_00.json out/map_00.es.json
Checks every source string has a translation and that control codes are preserved."""
import json, re, sys
src = json.load(open(sys.argv[1], encoding="utf-8"))
out = json.load(open(sys.argv[2], encoding="utf-8"))
CODE = re.compile(r"\\[a-zA-Z]+\[[^\]]*\]|\\(?:PN|PM|PG|TP|TE|CN)|\\[a-zA-Z]|\{\d+\}|<[^>]+>|\\\\")
errs = 0
for s in src:
    if s not in out:
        print("MISSING:", repr(s)[:120]); errs += 1; continue
    t = out[s]
    if not isinstance(t, str) or not t.strip():
        print("EMPTY:", repr(s)[:120]); errs += 1; continue
    a = sorted(CODE.findall(s)); b = sorted(CODE.findall(t))
    if a != b:
        print("CODES DIFFER:", repr(s)[:100], "| src:", a, "| es:", b); errs += 1
extra = [k for k in out if k not in set(src)]
if extra: print("EXTRA KEYS (not in source):", len(extra)); errs += len(extra)
print("OK" if errs == 0 else f"{errs} problems", f"({len(out)}/{len(src)} translated)")
sys.exit(1 if errs else 0)
