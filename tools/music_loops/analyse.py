#!/usr/bin/env python3
"""Step 1: analyse each track. Prints one JSON line per file (see README.md)."""
import subprocess, sys, os, json
import numpy as np
from scipy.signal import fftconvolve, stft

CR = 11025
HOP = 0.05


def decode(path, rate=None):
    cmd = ["ffmpeg", "-v", "error", "-i", path, "-ac", "1"]
    if rate:
        cmd += ["-ar", str(rate)]
    cmd += ["-f", "f32le", "-"]
    return np.frombuffer(subprocess.run(cmd, capture_output=True).stdout, dtype=np.float32).astype(np.float64)


def info(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries",
                          "stream=sample_rate,duration_ts", "-of", "json", path], capture_output=True).stdout
    s = json.loads(out)["streams"][0]
    return int(s["sample_rate"]), int(s["duration_ts"])


def ncc_slide(x, t):
    n = len(t)
    t0 = t - t.mean()
    num = fftconvolve(x, t0[::-1], mode="valid")
    c = np.cumsum(np.concatenate([[0.0], x]))
    c2 = np.cumsum(np.concatenate([[0.0], x * x]))
    s = c[n:] - c[:-n]
    s2 = c2[n:] - c2[:-n]
    var = np.maximum(s2 - s * s / n, 1e-12)
    return num / np.sqrt(var * (t0 @ t0 + 1e-12))


def ncc(a, b):
    m = min(len(a), len(b))
    if m < 64:
        return 0.0
    a = a[:m] - a[:m].mean(); b = b[:m] - b[:m].mean()
    d = np.sqrt((a @ a) * (b @ b))
    return float(a @ b / d) if d > 0 else 0.0


def analyse(path):
    rate, total = info(path)
    x = decode(path, CR)
    n = len(x)
    dur = n / CR
    hop = int(CR * HOP)
    # spectrogram frames, log-compressed and normalised: what the music is, not how loud
    _, _, Z = stft(x, fs=CR, nperseg=1024, noverlap=1024 - hop, boundary=None, padded=False)
    M = np.log1p(40 * np.abs(Z[4:300]))             # ~40 Hz .. 3.2 kHz
    F = M.shape[1]
    lvl = np.sqrt(np.mean(np.abs(Z) ** 2, axis=0) + 1e-12)
    body = np.median(lvl[F // 5: F * 3 // 5])
    loud = np.where(lvl > body * 0.5)[0]
    # the fade: the last stretch where the level only goes down from body level
    sm = np.convolve(lvl, np.ones(40) / 40, mode="same")   # 2 s average
    fade = F
    for i in range(F - 1, 0, -1):
        if sm[i] >= body * 0.7:
            fade = i
            break
    quiet = np.where(np.abs(x) > 0.003)[0]
    sound_end = (quiet[-1] + 1) / CR if len(quiet) else dur
    res = {"name": os.path.basename(path), "rate": rate, "total": total, "dur": round(dur, 2),
           "fade_s": round(fade * HOP, 1), "sound_end": round(sound_end, 2),
           "tail_silence": round(dur - sound_end, 2), "loop": None}
    N = M / (np.linalg.norm(M, axis=0, keepdims=True) + 1e-9)
    usable = min(fade, F)
    min_lag = int(8 / HOP)
    if usable < 3 * min_lag:
        return res
    # similarity of the track with itself moved by each lag, over the part before the fade
    lags = np.arange(min_lag, usable - int(6 / HOP))
    sims = np.empty(len(lags))
    for i, L in enumerate(lags):
        a = N[:, : usable - L]
        b = N[:, L: usable]
        d = np.sum(a * b, axis=0)
        # the best 12 s run shared by both copies (the intro does not repeat)
        w = int(12 / HOP)
        if len(d) < w:
            sims[i] = d.mean()
        else:
            c = np.cumsum(np.concatenate([[0.0], d]))
            sims[i] = ((c[w:] - c[:-w]) / w).max()
    base = np.median(sims)
    top = sims.max()
    res["sim"] = round(float(top), 3)
    res["sim_base"] = round(float(base), 3)
    if top - base < float(os.environ.get("MINGAP", "9")) and (top < 0.9 or top - base < 0.04):
        return res
    # peaks about as good as the best: the longest lag is the whole loop, shorter ones are repeated phrases
    thr = top - (top - base) * 0.12
    idx = np.where(sims >= thr)[0]
    L = int(lags[idx[-1]])
    j = idx[-1]
    while j > 0 and sims[j - 1] >= sims[j]:
        j -= 1
    lo = max(0, j - 3); hi = min(len(sims), idx[-1] + 4)
    L = int(lags[lo + int(np.argmax(sims[lo:hi]))])
    # where the two copies overlap best: frames t (first copy) and t+L (second copy)
    d = np.sum(N[:, : usable - L] * N[:, L: usable], axis=0)
    okf = np.where(d > 0.93)[0]
    if len(okf) < int(4 / HOP):
        return res
    # loop end: late in the matching zone but clear of the fade
    y = decode(path)
    k = rate
    lag0 = int(L * HOP * rate)
    e_frame = min(int(okf[-1]) + L, fade - int(1.0 / HOP))
    e0 = int(e_frame * HOP * rate)
    # refine the lag by the waveform around the loop end
    Wf = int(3 * rate)
    if e0 - Wf - lag0 < 0:
        return res
    t = y[e0 - Wf: e0]
    span = int(rate * 0.12)
    lo = max(0, e0 - Wf - lag0 - span)
    seg = y[lo: e0 - lag0 + span]
    c = ncc_slide(seg, t)
    p = lo + int(np.argmax(c))
    lag = (e0 - Wf) - p
    wave = float(c.max())
    # slide the jump (up to 1.5 s back) to the spot where both waves agree best over a few ms
    best = None
    win = int(rate * 0.004)
    lo_e = e0 - int(1.5 * rate)
    a = y[lo_e - lag - win: e0 - lag + win]
    b = y[lo_e - win: e0 + win]
    diff = (a - b) ** 2
    cs = np.cumsum(np.concatenate([[0.0], diff]))
    err = cs[2 * win:] - cs[:-2 * win]                 # mismatch energy around each candidate
    en = np.cumsum(np.concatenate([[0.0], a * a + b * b]))
    den = en[2 * win:] - en[:-2 * win] + 1e-9
    rel = err / den
    q = int(np.argmin(rel))
    end = lo_e + q
    start = end - lag
    if start <= 0:
        return res
    pre = ncc(y[start - min(2 * rate, start): start], y[end - min(2 * rate, start): end])
    post_n = min(2 * rate, len(y) - end)
    post = ncc(y[start: start + post_n], y[end: end + post_n])
    la = np.sqrt(np.mean(y[end - rate: end] ** 2) + 1e-12)
    lb = np.sqrt(np.mean(y[max(0, start - rate): start] ** 2) + 1e-12)
    res["loop"] = {"start": int(start), "length": int(lag), "wave": round(wave, 3), "seam": round(float(rel[q]), 4),
                   "zone_end_s": round(float((int(okf[-1]) + L) * HOP), 1), "zone_start_s": round(float(int(okf[0]) * HOP), 1),
                   "pre": round(pre, 3), "post": round(post, 3), "level_db": round(float(20 * np.log10(la / lb)), 1),
                   "start_s": round(start / rate, 2), "len_s": round(lag / rate, 2), "end_s": round(end / rate, 2)}
    return res


if __name__ == "__main__":
    for f in sys.argv[1:]:
        try:
            print(json.dumps(analyse(f), ensure_ascii=False), flush=True)
        except Exception as ex:  # keep going over a folder
            print(json.dumps({"name": os.path.basename(f), "error": repr(ex)}), flush=True)
