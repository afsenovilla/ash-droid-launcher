#!/usr/bin/env python3
"""Step 2: decide what to do with each analysed track and write the engine's table.

  build_table.py analysis.jsonl <folder with the game's Audio/BGM> <out header>

Reads the lines analyse.py printed, keeps the loops it can trust, falls back to trimming the
silence at the end, and writes app/jni/mkxp-z/src/audio/ashdroid-loops.h. Also prints a report.
"""
import ctypes, json, os, subprocess, sys
import numpy as np

vf = ctypes.CDLL("libvorbisfile.so.3")


class OggVorbisFile(ctypes.Structure):
    _fields_ = [("pad", ctypes.c_char * 2048)]


vf.ov_fopen.argtypes = [ctypes.c_char_p, ctypes.POINTER(OggVorbisFile)]
vf.ov_pcm_total.restype = ctypes.c_int64
vf.ov_pcm_total.argtypes = [ctypes.POINTER(OggVorbisFile), ctypes.c_int]


def pcm_total(path):
    """The length the engine sees (libvorbisfile); ffprobe's can be off by a block."""
    f = OggVorbisFile()
    if vf.ov_fopen(path.encode(), ctypes.byref(f)) != 0:
        raise IOError(path)
    t = vf.ov_pcm_total(ctypes.byref(f), -1)
    vf.ov_clear(ctypes.byref(f))
    return int(t)


def decode(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-f", "f32le", "-"],
                         capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def click(path, start, length, rate):
    """How sharp the jump is against the sharpest moments around it (below 1 = hidden)."""
    y = decode(path)
    end = start + length
    j = 2 * rate
    joined = np.concatenate([y[end - j: end], y[start: start + j]])
    d2 = np.abs(np.diff(joined, 2))
    return float(d2[j - 3: j + 3].max() / (np.percentile(d2[j - rate: j + rate], 99.5) + 1e-9))


def needs_fix(r):
    """Fades out or ends in silence: heard to stop and start again when replayed whole."""
    return r["tail_silence"] > 0.25 or r["fade_s"] < r["dur"] - 2.0


def main():
    analysis, folder, out = sys.argv[1:4]
    paths = {}
    for root, _, files in os.walk(folder):
        for f in files:
            if f.lower().endswith(".ogg"):
                paths[f] = os.path.join(root, f)
    rows, report = [], {"loop": [], "trim": [], "left": []}
    for line in open(analysis):
        r = json.loads(line)
        if "error" in r:
            continue
        name, lp, need = r["name"], r.get("loop"), needs_fix(r)
        ok = False
        if lp:
            ratio = lp["len_s"] / max(r["fade_s"], 1)          # a whole loop, not a repeated phrase
            reach = lp["zone_end_s"] >= r["fade_s"] - 3        # the repeat goes on to the track's end
            if need:
                ok = (ratio >= 0.3 and reach and lp["seam"] <= 0.1 and abs(lp["level_db"]) <= 1.5
                      and (lp["wave"] >= 0.3 or r["sim"] >= 0.95))
            else:
                # ends at full level: only when the repeat is the same recording, note for note
                ok = ratio >= 0.3 and reach and lp["wave"] >= 0.9 and lp["seam"] <= 0.02
            ok = ok and click(paths[name], lp["start"], lp["length"], r["rate"]) <= 1.5
        if ok:
            rows.append((pcm_total(paths[name]), lp["start"], lp["length"], name))
            report["loop"].append(name)
        elif r["tail_silence"] > 0.25:
            rows.append((pcm_total(paths[name]), 0, int(r["sound_end"] * r["rate"]), name))
            report["trim"].append(name)
        elif need:
            report["left"].append(name)
    rows.sort()
    assert len({t for t, *_ in rows}) == len(rows), "two tracks with the same length"
    for t, s, l, name in rows:
        assert 0 <= s and l > 0 and s + l <= t, name
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "header.tmpl")) as f:
        tmpl = f.read()
    body = "".join("\t{ %du, %du, %du }, /* %s */\n" % (t, s, l, n[:-4]) for t, s, l, n in rows)
    with open(out, "w") as f:
        f.write(tmpl.replace("@ROWS@", body))
    print("%d loops, %d silence trims, %d fade out with nothing to loop" %
          (len(report["loop"]), len(report["trim"]), len(report["left"])))
    for k in ("trim", "left"):
        print(k + ": " + ", ".join(n[:-4] for n in report[k]))


if __name__ == "__main__":
    main()
