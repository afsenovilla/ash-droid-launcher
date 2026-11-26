# Music loop points

Some of the game's music is a recording of "intro, loop, loop again, fade out", or ends with seconds of
silence, and has no `LOOPSTART` / `LOOPLENGTH` tags. The engine replays such a file from its start when it
ends, so the fade and the silence are heard every time. These tools find where each track repeats and
write a table the engine uses to loop it properly. The table holds numbers only: no audio is shipped and
the player's files are not modified.

Requirements: Python 3 with numpy and scipy, `ffmpeg` / `ffprobe`, and `libvorbisfile`.

```
# 1. analyse every track (about one second per minute of music)
find "<game>/Audio/BGM" -name "*.ogg" -print0 | xargs -0 -n 6 python3 analyse.py > analysis.jsonl
# 2. decide and write the engine's table
python3 build_table.py analysis.jsonl "<game>/Audio/BGM" ../../app/jni/mkxp-z/src/audio/ashdroid-loops.h
```

## How a loop is found

1. The track is compared with itself moved in time (spectrogram frames, so loudness and a fade do not
   matter). A lag at which a long stretch matches is a repeat; of the lags that match about as well, the
   longest is taken, because shorter ones are phrases repeated inside the loop.
2. The lag is refined to the sample by correlating the waveforms around the end of the loop.
3. The jump is moved, within the last second and a half before the fade, to the spot where the two
   waveforms agree best over a few milliseconds, so it cannot click.

## What gets a loop

- Tracks that fade out or end in silence: the loop is kept when it is long (at least 30 % of the track),
  the repeat runs on to the end of the track, the waveforms correlate (or the spectral match is very
  high), levels on both sides of the jump are within 1.5 dB and the jump does not click.
- Tracks that end at full level (already cut to be replayed whole): only when the repeat is the same
  recording note for note (waveform correlation of 0.9 or more), which is the case of the short tracks
  with a fanfare before the loop.
- Tracks with silence at the end but no trustworthy loop: replayed whole without the silence.
- Everything else is left as it is.

## How the engine applies it

`VorbisSource` (app/jni/mkxp-z/src/audio/vorbissource.cpp) looks a track up by its exact length in PCM
frames when it has no loop tags of its own. Lengths come from libvorbisfile, as in the engine (ffprobe's
can differ by a block). A file of any other length is never touched, so another version of the game
simply falls back to the old behaviour for the tracks that changed.
