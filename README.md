# B.A.D. — Beat Accuracy Detector

> With B.A.D., the beat approves you.

B.A.D. is an Android drum-practice application that will listen to drum or
practice-pad hits, compare them with a rhythmic exercise, and judge whether
each hit was early, on time, late, or missed.

The project is currently an early version 1 prototype. It can load and run a
single-lane exercise with a generated metronome and a scrolling Compose
timeline. Microphone capture and hit detection are not implemented yet.

## Current features

- Versioned, data-driven JSON exercise format
- Bundled exercise loading and unloading
- Exercise validation with explicit error reporting
- Musical tick-to-time conversion
- Monotonic practice-session clock
- Count-in, running, stopped, and completed states
- Generated 48 kHz mono PCM metronome
- Accented first beat of each measure
- Streaming `AudioTrack` output
- Single-lane scrolling rhythm timeline
- Static first-measure preview while an exercise is idle
- BPM-scaled beat highlights at the judgement line
- Full-screen practice playback with pause, resume, repeat, and stop controls
- Collapsible per-exercise playback controls for tempo, count-in, measure
  count, and downbeat-only clicks
- Lifecycle-aware playback cleanup
- JVM tests for exercise parsing, validation, timing, and click generation
- GitHub Actions verification with downloadable debug APKs

## Running the app

### Requirements

- Android Studio
- Android SDK 36
- JDK 21, such as Android Studio's bundled runtime
- Android 8.0 (API 26) or newer device

A physical Android device is recommended for evaluating audio and visual
timing. An emulator is sufficient for checking layout and application flow.

## Exercise format

Exercises are stored as versioned JSON files under:

```text
app/src/main/assets/exercises/
```

Example:

```json
{
  "formatVersion": 1,
  "id": "basic-quarter-notes",
  "name": "Quarter Note Inspection",
  "description": "Four measures of quarter notes.",
  "tempoBpm": 100.0,
  "timeSignature": {
    "numerator": 4,
    "denominator": 4
  },
  "countInMeasures": 1,
  "measureCount": 4,
  "ticksPerQuarterNote": 480,
  "notes": [
    {
      "positionTicks": 0,
      "accent": true
    },
    {
      "positionTicks": 480
    }
  ]
}
```

`ticksPerQuarterNote` defines the musical resolution. At a resolution of 480:

- Quarter note: 480 ticks
- Eighth note: 240 ticks
- Sixteenth note: 120 ticks
- Eighth-note triplet: 160 ticks

`positionTicks` is a musical position measured from the beginning of the
exercise. It is not a timer callback, audio sample index, or stored wall-clock
timestamp.

## Project structure

```text
app/src/main/java/com/titaniumharmonics/bad/
├── audio/       PCM click generation and AudioTrack output
├── exercise/    Exercise model, JSON codec, validation, and asset loading
├── timing/      Monotonic clock, musical timing, and session progression
└── ui/
    ├── practice/ Practice ViewModel and Compose UI
    └── theme/    Material theme
```

The exercise, timing, and click-generation logic is kept separate from
Compose and can be tested on the JVM.

## Timing model

The exercise stores musical positions rather than elapsed timestamps. At
runtime, musical ticks are converted to nanoseconds from the exercise tempo.

The practice session uses a monotonic clock. The scrolling timeline follows
that clock; Compose animation is not the authoritative timing source.

Metronome clicks are sample-aligned relative to one another. Physical output
latency still depends on the Android device and audio route. Output-latency
calibration has not been implemented yet.

Playback settings can be adjusted while an exercise is idle. They remain in
effect for repeated runs of that exercise and reset when another exercise is
loaded. Longer sessions repeat the exercise pattern; shorter sessions truncate
it at the selected measure boundary. Settings are locked while playback is
preparing, counting in, or running.

Playback settings are collapsed by default. Tapping the exercise information
card toggles them without changing the configured values.

The downbeat-only option keeps the full count-in audible, then plays the
metronome only on the first beat of each exercise measure.

During the exercise, every beat produces a brief green outline ring as it
crosses the judgement line, including beats muted by downbeat-only mode. The
ring follows that beat to the left and lasts for one quarter of the current
beat duration, so it scales with tempo.

The judgement line is centered on the rhythm lane and is ten times the diameter
of the largest note circle rather than spanning the full player height.

While an exercise is idle, the timeline centers the notes from its first
measure as a static musical preview while preserving their relative tick
spacing. The judgement line is hidden in preview mode. Starting playback
restores the judgement line and switches back to elapsed-time scrolling.

Starting an inspection opens a dedicated full-screen player containing the
timeline, current status, and playback controls. Initial playback waits two
seconds after opening the player before starting the count-in or exercise,
giving the device time to prepare the screen and audio path. Pause freezes both
metronome audio and monotonic timeline progress. When count-in is enabled,
Resume plays the configured count-in while the timeline remains frozen, then
continues from the paused position. Repeat restarts with the configured
count-in, and Stop returns to the exercise settings.

When microphone detection and hit matching are added, timing feedback will use
a green ring for on-time hits, a blue ring for early hits, and a red ring for
late hits. Missed notes will show a gray `X` at the expected-note position, and
extra hits will show a red `X` at the detected-hit position. Extra hits remain
a distinct result category for accurate session statistics.

## Continuous integration

GitHub Actions runs unit tests, Android lint, and a debug build for pull
requests, pushes to `main`, and manual workflow runs. Successful runs publish
the debug APK for 14 days. Test and lint reports are uploaded even when
verification fails.

## Tests and static checks

Run JVM tests:

```shell
./gradlew testDebugUnitTest
```

Build the debug application:

```shell
./gradlew assembleDebug
```

Run Android lint:

```shell
./gradlew lintDebug
```

## Planned version 1 work

1. Output and microphone latency calibration
2. Real-time mono PCM microphone capture
3. Energy/envelope-based onset detection
4. Detected-hit to expected-note matching
5. Early, on-time, late, missed, and extra-hit judgements
6. Hit-intensity measurement
7. Immediate visual feedback
8. Practice-session results

Version 1 intentionally uses one generic rhythmic lane. Separate kick, snare,
hi-hat, and tom lanes are future extensions.
