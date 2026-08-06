# B.A.D. — Beat Accuracy Detector

![B.A.D. — Beat Accuracy Detector](docs/assets/bad-banner.png)

> With B.A.D., the beat approves you.

B.A.D. is an Android drum-practice application that will listen to drum or
practice-pad hits, compare them with a rhythmic exercise, and judge whether
each hit was early, on time, late, or missed.

The project is currently an early version 1 prototype. It can select, load,
edit, save, and run a single-lane exercise with a generated metronome and a
scrolling Compose timeline. Practice sessions are captured as WAV audio;
automatic hit detection and its pure-Kotlin note-matching core are now available
for developer inspection.

## Current features

- Versioned, data-driven JSON exercise format
- Separate persisted editable and compiled runtime exercise models
- Validated exercise-library loading and unloading for practice
- Automatic `Download/B.A.D/assets` exercise folder initialization
- Exercise validation with explicit error reporting
- Musical tick-to-time conversion
- Monotonic practice-session clock
- Mandatory one-measure count-in, running, stopped, and completed states
- Generated 48 kHz mono PCM metronome
- Distinct quarter-note count-in and note-driven exercise clicks
- Accented first beat of each measure
- Streaming `AudioTrack` output
- Practice-session microphone capture to mono PCM WAV, preferring 48 kHz with
  a 44.1 kHz fallback
- Offline WAV validation and transient-envelope preprocessing after completed
  practice sessions
- Temporary debug-only in-app recording playback, position, and deletion
- Temporary debug CSV export and synchronized envelope/noise-floor graph
- User-triggered universal speaker-to-microphone timing calibration
- Single-lane scrolling rhythm timeline
- Static first-measure preview while an exercise is idle
- BPM-scaled beat highlights at the judgement line
- Full-screen practice playback with pause, resume, repeat, and stop controls
- Branded adaptive launcher icon and orientation-aware startup screen
- Exercise creation and modification with JSON file creation and overwriting
- Editable rhythmic measure patterns with persisted subdivisions and multipliers
- Purpose-aware exercise library with tap-to-load or tap-to-edit and
  long-press deletion
- Home screen sections for creating or modifying exercises and starting practice
- Collapsible per-exercise playback controls for tempo, measure
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

Exercises are versioned JSON documents that can be stored anywhere exposed by
Android's system file picker. A bundled example remains under:

```text
app/src/main/assets/exercises/
```

On Android 10 or newer, the first app launch creates
`Download/B.A.D/assets/` in shared storage and copies
`basic-quarter-notes-v2.json` there when that sample is absent. This seeding runs
only once per installation, so a sample deliberately deleted later is not
recreated. Existing folders and files are never erased or overwritten. Create
and the Exercise Library's Browse file pickers open in this directory by
default while still allowing navigation elsewhere.

Android 8 and 9 use an app-private external-storage fallback because creating a
public Downloads folder would require a runtime storage permission.

Example:

```json
{
  "fileType": "bad-exercise",
  "formatVersion": 2,
  "id": "basic-quarter-notes",
  "name": "Quarter Note Inspection",
  "description": "Four measures of quarter notes.",
  "tempoBpm": 100.0,
  "timeSignature": {
    "numerator": 4,
    "denominator": 4
  },
  "measureCount": 4,
  "ticksPerQuarterNote": 480,
  "measureSubdivisions": [
    "quarter",
    "eighth",
    "eighth_triplet",
    "sixteenth"
  ],
  "measureMultipliers": [
    1,
    1,
    1,
    1
  ],
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

`fileType` must be `bad-exercise`. It identifies the document before the
remaining exercise structure and musical values are validated.

`ticksPerQuarterNote` defines the musical resolution. At a resolution of 480:

- Quarter note: 480 ticks
- Eighth note: 240 ticks
- Sixteenth note: 120 ticks
- Eighth-note triplet: 160 ticks

`positionTicks` is a musical position measured from the beginning of the
exercise. It is not a timer callback, audio sample index, or stored wall-clock
timestamp.

`measureSubdivisions` stores one display/editing grid per measure. Supported
values are `quarter`, `eighth`, `eighth_triplet`, and `sixteenth`. Older
exercise files without this field remain valid and use `quarter` for every
measure. Changing a subdivision explicitly resets that measure with every slot
in the new grid enabled.

`measureCount` is the number of editable measure patterns.
`measureMultipliers` stores how many consecutive measures each pattern
represents. Existing files without this field default every pattern to `1`.
Each multiplier must be between `1` and `99`; the expanded exercise length is
the sum of all multipliers.

## Project structure

```text
app/src/main/java/com/titaniumharmonics/bad/
├── audio/       Metronome, WAV recording/playback, and offline analysis
├── exercise/    Exercise model, JSON codec, validation, and asset loading
├── timing/      Monotonic clock, musical timing, and session progression
└── ui/
    ├── practice/ Practice ViewModel and Compose UI
    └── theme/    Material theme
```

The exercise, timing, and click-generation logic is kept separate from
Compose and can be tested on the JVM. Validated JSON is decoded into the
persisted `EditableExercise` source of truth. Practice mode compiles it in
memory into an immutable `RuntimeExercise` containing sequential measures and
expected notes with measure-local and absolute tick positions. Timing,
timeline, and metronome code use only this runtime representation; the editor
and storage layers continue to use the persisted representation. Exercise
files are opened and created through Android's Storage Access Framework, so
broad storage permission is not required.

The editor changes the exercise name, BPM, pattern count, each pattern's
persisted multiplier and subdivision, and its note slots. It shows both the
editable pattern count and expanded measure count. Pattern labels use the
expanded indexes, so multipliers `4`, `2`, and `1` produce ranges `1–4`, `5–6`,
and `7`. Each pattern's compact overflow menu supports duplicate, clear, move
up, and move down actions; swipe left continues to expose deletion. Duplicate
copies the complete pattern and multiplier, while Clear explicitly creates a
silent pattern without changing its subdivision or multiplier. New patterns
start at `×1` with all four Quarter slots enabled, and the editor scrolls to
the new pattern after Add Pattern is pressed.
Selecting another subdivision resets that pattern with every new slot enabled;
tapping a slot then enables or disables its expected note. Notes outside the
selected grid remain preserved and produce a warning until an explicit
subdivision reset replaces the pattern. When an existing file is overwritten,
its identifier, description, time signature, and timing resolution
are preserved. Pattern duration comes from the exercise time signature.

When an exercise is loaded for practice, each compact pattern is expanded in
memory into its configured number of flat, sequential runtime measures. The
persisted JSON remains compact. Every repeated note keeps its exact
measure-local tick and receives a new absolute position from its runtime
measure offset, so Quarter, Eighth, Eighth-triplet, and Sixteenth timing remain
exact.

Opening Create or Modify clears the currently loaded in-memory practice
exercise before navigating. Saved exercise files are not changed.

The editor's Play exercise action always saves the current edits first. New
exercises request a destination file, while existing exercises overwrite their
source file. After a successful save, the saved JSON is loaded into Practice
and playback starts with the normal startup delay and count-in. A cancelled or
failed save remains in the editor and does not start playback.

During playback, exercise metronome clicks occur only at exact expected-note
ticks. Disabled notes and empty measures are silent. Every session and resume
uses exactly one measure of Quarter-note count-in clicks with a distinct
higher-pitched sound. Count-in is a playback invariant and is therefore not
stored in exercise JSON. The timeline and visual beat highlights continue
independently so musical time stays visible.

Swiping a measure to the left reveals a red Delete action. Deletion occurs only
after that action is pressed. Notes inside a deleted imported measure are
removed, while notes in later measures shift left to preserve their position
within the remaining measure sequence.

Load and Modify open the same Exercise Library containing only files from the
default folder that pass B.A.D. format and exercise validation. Tapping loads
the selected exercise for Practice or opens it in the editor, according to the
entry point. Pressing and holding requests permanent deletion with
confirmation; the file is revalidated immediately before removal. Browse other
folders keeps the Android document picker available for exercises stored
elsewhere. Each library card shows BPM, compact pattern count, and expanded
measure count.

## Timing model

The exercise stores musical positions rather than elapsed timestamps. During
compilation, a runtime measure starts at
`runtimeMeasureIndex × measureDurationTicks`, and each expected note is placed
at `runtimeMeasureStartTick + positionInMeasureTicks`. At runtime, musical
ticks are converted to nanoseconds from the exercise tempo.

The practice session uses a monotonic clock. The scrolling timeline follows
that clock; Compose animation is not the authoritative timing source.

Metronome tones are sample-aligned relative to one another. Physical output
latency still depends on the Android device and audio route; the universal
timing calibration described below measures the phone-speaker/built-in-
microphone path.

Playback settings can be adjusted while an exercise is idle. They remain in
effect for repeated runs of that exercise and reset when another exercise is
loaded. Longer sessions repeat the exercise pattern; shorter sessions truncate
it at the selected measure boundary. Settings are locked while playback is
preparing, counting in, or running.

Playback settings are collapsed by default. Tapping the exercise information
card toggles them without changing the configured values.

The **First note only** option shows its enabled or disabled state beneath the
label. When enabled, it keeps the full count-in audible, then plays the
metronome only when an expected note is enabled at the beginning of an exercise
measure.

## Metronome and rejection filter

The metronome uses a deterministic Hann-windowed sine burst instead of a
broadband click. Defaults are 6000 Hz, 10 ms, 55% normal volume, and 85% accent
volume. Normal and accent beats use the same frequency and differ only in
amplitude. Settings exposes every control directly: tone frequency
(3000–9000 Hz), duration (5–30 ms), independent normal/accent volume
(0–100%), notch enablement, notch center (3000–9000 Hz), and Q (2–30, default
10). Normal and accent test-tone actions play four beats at 500 ms intervals
over two seconds without starting recording or practice.

The notch center follows tone frequency by default. Editing the notch center
creates a persistent custom override; **Relink to tone frequency** restores
automatic following. Invalid or corrupted stored values fall back to the
documented defaults. Settings changes affect future sessions only.

Each practice run freezes a public immutable `SessionMetronomeSnapshot` with
the exact `MetronomeConfiguration` and downbeats-only state used for playback.
`RecordedSession` retains that snapshot in memory, so later global changes do
not reinterpret an older WAV. Sessions constructed without a snapshot use the
documented default configuration as a compatibility fallback. These domain
types are independent from Compose and are available to future audio and
grading modules.

During the exercise, every beat produces a brief green outline ring as it
crosses the judgement line, including beats muted by downbeat-only mode. The
ring follows that beat to the left and lasts for one quarter of the current
beat duration, so it scales with tempo.

The judgement line is centered on the rhythm lane and is ten times the diameter
of the largest note circle rather than spanning the full player height.

While an exercise is idle, the timeline centers the notes from its first
measure as a static musical preview while preserving their relative tick
spacing. The judgement line is hidden in preview mode.

Starting an inspection opens a dedicated full-screen player containing the
timeline, current status, and playback controls. Player text adapts to the
active theme, using white in dark themes and black in light themes. Initial
playback waits two seconds after opening the player before starting the
mandatory one-measure count-in, giving the device time to prepare the screen
and audio path. Pause freezes both metronome audio and monotonic timeline
progress. Resume plays one measure of count-in while the timeline remains
frozen at the paused position, then continues from there. During an initial or
Repeat count-in, the playback timeline waits at one quarter note before
exercise time zero. It begins scrolling during the final quarter note of the
count-in, reaches time zero with the exercise audio, and continues without a
visual jump. Once exercise playback begins, the Player shows the current
one-based measure and total measure count beneath the status. It preserves that
progress while paused or during Resume count-in and shows the final measure on
completion. Stop returns to the exercise settings.

Microphone permission is requested when the app opens if it has not already
been granted, and is checked again before every session. A declined permission
is requested again on the next app launch. Recording starts immediately before
the mandatory initial count-in and pauses with practice playback. It remains
paused throughout every resume count-in, then resumes in the same logical
transition as exercise playback. Natural completion finalizes
`Music/B.A.D/recordings/debug-recording.wav` in app-specific external storage;
stopped, cancelled, or failed sessions discard their partial recording.

A successfully finalized WAV produces an immutable in-memory
`RecordedSession`. The recorder's successfully written sample-frame count is
authoritative: `exerciseStartSampleFrame` marks the first graded exercise
sample, while earlier samples belong to the initial count-in. Paused time and
resume count-ins append no frames, so offline grading can subtract this start
index to obtain continuous exercise-relative sample positions. No session
metadata or timeline sidecar is written to storage.

Natural completion also starts offline preprocessing away from the UI thread.
The WAV reader validates RIFF/WAVE chunks instead of assuming a fixed 44-byte
header and accepts the recorder's mono signed 16-bit PCM at either 48 kHz or
44.1 kHz. Session metadata must match the WAV exactly. Analysis begins at
`exerciseStartSampleFrame`, so the initial count-in is excluded; paused time
and resume count-ins are already absent from the recorded sample stream.

The first analysis pass normalizes PCM, removes the graded signal's mean DC
offset, applies a configurable first-order 80 Hz high-pass filter, then applies
the session snapshot's second-order biquad notch before measuring overlapping
5 ms frames at 2 ms hops. The notch is enabled by default at 6000 Hz with Q 10.
Each frame uses its actual center sample
as its exercise timestamp, including the final partial frame without zero
padding. Post-notch frame peak and RMS level feed a transient envelope with 2 ms attack
and 12 ms release, followed by a slowly adapting noise-floor estimate. These
defaults are centralized in `AudioAnalysisConfig`; onset detection is not yet
implemented by this preprocessing stage itself.

After preprocessing succeeds, a second background stage performs offline
drum-hit detection from the post-notch analysis. It uses an adaptive threshold
equal to the larger of the configured absolute minimum and noise floor
multiplier, then applies hysteresis so a ringing decay normally creates only
one candidate. A configurable look-back locates the earliest meaningful
attack frame; the peak remains a separate measurement found in a forward
search window. Candidate timestamps use frame-center exercise samples.

Only candidate-local PCM windows are transformed by a pure Kotlin radix-2 FFT
with a Hann window. The default 1024-point, 16 ms analysis compares energy in a
600 Hz band around the frozen session metronome frequency with broadband
residual energy and spectral spread. Public classification is strictly
`DRUM` or `METRONOME`. Proximity to a scheduled click is never sufficient for
rejection: narrow-band concentration, weak broadband residual, and spectral
confidence must also agree. The safe default retains uncertain candidates as
drum hits with reduced confidence. A 35 ms retrigger interval keeps the
stronger peak, with equal peaks retaining the earlier candidate.

Every threshold, onset, spacing, confidence, FFT, and rejection parameter is
available directly in Settings with validation, units, persistence, and reset.
The app freezes those settings and the active timing calibration in an
immutable `SessionDetectionSnapshot` when practice starts. Detection never
reads newer global settings while processing that session. Calibration keeps
both raw and corrected samples and applies
`calibratedHitSample = rawHitSample - convertedCalibrationOffset`; a corrected
sample before exercise zero remains negative instead of being silently
clamped.

Debug builds show a temporary **Debug: Recorded Audio** card after natural
completion. It uses Android `MediaPlayer` to play, pause, stop, replay, or
delete the WAV and shows its current position, duration, and file path. A new
practice session stops debug playback and replaces the previous recording. It
also shows the actual sample rate, total and graded frame counts, the exercise
start frame, and recording and graded durations.
After preprocessing, the card adds peak-preserving pre-notch and post-notch
envelopes, the post-notch noise floor, expected metronome markers, and the WAV
playback cursor. It also displays the adaptive threshold, alternating
red/blue expected exercise markers, raw and calibrated onsets, measured peaks,
and rejected metronome candidates. The graph keeps the count-in region visually separate from
exercise time zero and bounds rendered data to roughly 1,500 points. A
debug-only Storage Access Framework action exports one locale-independent CSV
row per analysis frame with pre/post notch values, adaptive threshold, candidate
spectral metrics, classification, raw/calibrated samples, and confidence. A
second compact CSV contains one row per accepted or rejected candidate.
Analysis, detection, or export failure
does not remove the playable WAV.
Leaving the Practice screen releases both capture and playback resources. The
recording pipeline remains present in release builds, but this playback card is
compiled behind `BuildConfig.DEBUG` and is not shown there.

Detected drum hits can now be matched deterministically to the exact expected
notes in the immutable runtime-exercise snapshot. Matching uses calibrated hit
samples and a dynamic-programming sequence alignment: a match costs the absolute
timing error, a missed note costs the maximum-late window, and an extra hit costs
the maximum-early window. Exact ties prefer the lower accumulated absolute
timing error, then the earliest chronological operation and stable input order.
Negative errors are early and positive errors are late. Hits below the configured
minimum confidence are excluded before matching and retained separately for
diagnostics.

The default On-Time window is 40 ms before and after a note, with maximum Early
and Late matching windows of 120 ms. These four boundaries are directly editable
and resettable in Settings alongside minimum hit confidence and extra-hit
handling. The values persist locally with safe fallback for corrupt data. The
On-Time boundaries cannot exceed their corresponding positive maximum matching
windows.

The result domain can assemble matching and detection output into an immutable
`PracticeResult`. Each `JudgedNote` preserves expected timing, measure and beat
position, matched raw and calibrated hit timing, judgement, confidence, and
relative intensity; unmatched accepted detections become independent
`ExtraHit` entries. The exact runtime exercise, metronome, detection, judgement,
and calibration context are retained so a later persisted format can explain a
run without consulting newer global settings.

Accuracy is the On-Time note count divided by expected notes, while hit rate is
all matched notes divided by expected notes. Missed rate uses expected notes as
its denominator; extra-hit rate uses all accepted detections. A zero denominator
returns `0.0`. Mean absolute, signed mean, median absolute, and population
standard deviation use matched-note timing errors only and remain absent when
there are no matches.

Relative intensity is derived from detected peak amplitude across matched and
extra accepted hits together. The weakest maps to `0.0` and strongest to `1.0`;
a single hit or equal-amplitude set maps to `1.0`. This is run-relative strength,
not decibels, MIDI velocity, or absolute force. Rejected low-confidence hits and
missed notes have no intensity.

`SessionJudgementSnapshot` freezes the immutable configuration supplied at
session start, and completed recordings retain it. Later settings edits cannot
mutate that snapshot or an assembled result. PR 6.3 will load the latest saved
configuration in the production practice pipeline and wire result assembly into
session completion and the UI.

Current practice recordings retain structural software sample alignment. The
calibration below measures the remaining fixed phone audio-path offset and is
now applied to offline detected hits. Result UI remains intentionally deferred.

## Timing calibration

The app opens the dedicated Timing Calibration flow automatically at launch
when no valid calibration is stored. After that, a small gear icon opens
Settings, where calibration can be viewed, reset, or run again manually.
Version 1 stores one universal calibration value rather than separate device
or route profiles.
Calibration must use the phone speaker and built-in microphone with wired,
Bluetooth, USB, HDMI, and other external audio devices disconnected. Android
route APIs block known external routes; uncertain routing requires explicit
user confirmation, and a harmful route change during capture cancels the run.

Calibration records a deterministic five-second sequence containing eight
synthetic clicks spaced 500 ms apart, with leading and trailing silence. The
sequence is streamed through a bounded `AudioTrack` buffer for device
compatibility. The click waveform is the same generator used by practice playback. Normalized
cross-correlation locates each known waveform in the PCM recording. Matches
must pass correlation, spacing, count, and offset-consistency checks; robust
outliers are removed and the median remaining offset becomes the calibration.
A measurable result is shown as a pending value and is not stored until the
user explicitly accepts it. The user may also reject it. Measurable results
that miss automatic consistency thresholds remain reviewable, while a run with
no usable measurement fails without creating a candidate.

The authoritative sign convention is:

```text
calibrationOffsetSamples = recordedClickSample - expectedClickSample
```

A positive value means the speaker click arrived later in the recorded PCM
than its scheduled reference. Offline onset processing corrects a raw hit
with `rawExerciseRelativeHitSample - calibrationOffsetSamples`. When sample
rates differ, the stored offset is converted by duration and rounded to the
nearest sample, with exact half-sample ties rounded away from zero.

An accepted offset, capture sample rate, confidence, match counts, spread,
timestamp, and algorithm version are stored locally in app preferences.
Rejecting or failing a recalibration never replaces the previous valid value;
reset removes it. Debug builds retain the temporary WAV and show playback, the recorded
waveform, expected and detected click markers, individual correlations, and
offsets. Release builds delete the temporary recording.

This universal value corrects the tested phone-speaker/built-in-microphone
pipeline only. Bluetooth and other routes can introduce different additional
latency during practice. A 6000 Hz tone is a starting compromise and audibility
and acoustic suppression still vary with phone speakers, headphones, Bluetooth
codecs, microphone response, room acoustics, and playback volume. Offline onset
detection and note matching cannot yet compensate for changing acoustic paths or
classify drum types.

When production judgement feedback is added, timing feedback will use
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

1. Wire current-run result assembly into practice completion
2. Add immediate visual feedback
3. Add the practice-session results screen
4. Persist completed runs and history

Version 1 intentionally uses one generic rhythmic lane. Separate kick, snare,
hi-hat, and tom lanes are future extensions.
