# B.A.D. — Beat Accuracy Detector

![B.A.D. — Beat Accuracy Detector](docs/assets/bad-banner.png)

> With B.A.D., the beat approves you.

B.A.D. is an Android drum-practice app. It records a player performing a
rhythmic exercise, detects drum hits, compares them with the expected notes,
and reports whether the playing was early, on time, late, missing, or creative.

## Current state

B.A.D. is a functional version 1 prototype built with Kotlin and Jetpack
Compose. The complete local practice flow is implemented:

```text
Exercise → Count-in and recording → Processing verdict → Detailed results
```

Current capabilities include:

- Create, edit, validate, save, browse, and delete versioned JSON exercises.
- Configure tempo, measure count, and metronome playback for practice.
- Record mono PCM WAV audio with pause, resume, repeat, and stop handling.
- Calibrate the phone speaker-to-microphone timing offset.
- Preprocess recorded audio and reject metronome tones using localized FFT
  analysis.
- Detect drum hits, apply calibration, match hits to expected notes, and
  classify Early, On Time, Late, Missed, and Extra events.
- Show timing statistics, detailed note judgements, and an interactive
  production graph.
- Persist metronome, detection, judgement, and calibration settings locally.
- Freeze settings into each active session so later changes cannot reinterpret
  a completed result.
- Provide a Room-backed persistence foundation for immutable completed-run
  snapshots and queryable history summaries.
- Provide debug-only WAV playback, analysis graphs, and CSV export tools.

Audio analysis, matching, judgement, statistics, and graph generation run
locally on the device. No account or network service is required.

## Processing verdict

After recording finishes, a dedicated black processing screen follows the real
analysis pipeline:

1. **YOU HAVE BEEN WEIGHED**
2. **YOU HAVE BEEN MEASURED**
3. **AND YOU HAVE BEEN FOUND...**

The first two messages remain visible for at least 1.5 seconds each and may
remain longer when processing is still running. Large dots animate below the
text every 225 ms. The final verdict is shown for 900 ms before Results opens
automatically. Back navigation is disabled during processing.

`PracticeVerdictCalculator` uses the completed result and the session's frozen
judgement configuration:

```text
hit rate below minimumHitRateForVerdict → MISSING
extra-hit rate above Creative threshold → CREATIVE
mean bias before the On-Time window     → EARLY
mean bias after the On-Time window      → LATE
otherwise                               → ON TIME
```

`minimumHitRateForVerdict` and
`minimumExtraHitRateForCreativeVerdict` both default to 30% and are configurable
from 0% to 100% in Settings. `MISSING` takes priority over `CREATIVE`, preventing
misleading feedback when too few expected notes were matched. The Creative
threshold is strict: exactly 30% extra hits is not Creative. The verdict is not
a score; it is a high-level summary of the player's performance tendency. The
extra-hit rate is the existing result statistic: extra hits divided by all
accepted detected hits.

## Exercise run persistence foundation

PR 7.1 introduced the persistence foundation. PR 7.2 now automatically saves
every valid completed practice run as soon as its `PracticeResult` and compact
production graph are ready. Partial, failed, and cancelled processing states are
never saved. PR 7.3 adds per-exercise history; progress charts remain deferred.

`ExerciseRun` is an immutable historical record containing a UUID run identity,
start and completion timestamps, app and schema versions, the complete
`PracticeResult`, and the bounded `ProductionGraphModel`. The result remains the
canonical owner of the frozen runtime exercise, detection, metronome, judgement,
and calibration snapshots. Loading a run never consults the current exercise,
Settings, calibration, WAV file, or detector configuration.

Persistence uses Room with a domain/entity boundary:

```text
ExerciseRun domain model
        <-> explicit mapper and versioned JSON payload
ExerciseRunEntity / exercise_runs table
```

Frequently queried history fields are direct indexed columns, including run and
exercise IDs, exercise-name snapshot, completion time, BPM, accuracy, hit rate,
timing bias, missed count, and extra count. Detailed immutable data is held in a
strict JSON payload. Future history lists can query summary projections without
decoding that payload. Rows do not have a foreign key to exercise documents, so
deleting an exercise intentionally leaves its saved runs and runtime snapshot
intact.

The Room database and per-run payload both start at schema version 1. Room
schemas are exported under `app/schemas/` for migration tests. Production setup
does not enable destructive migration; future database changes must provide and
test an explicit migration. Payload decoding rejects unknown future versions,
invalid enum values, inconsistent summaries, corrupt details, and invalid graph
data. Collection queries decode rows independently so a damaged run does not
prevent valid runs from loading.

Production graphs retain at most 1,500 downsampled post-notch envelope points
and contain the calibrated markers needed by Results. They do not persist PCM,
WAV paths, pre-notch envelopes, thresholds, rejected candidates, FFT diagnostics,
or debug CSV layers. A representative four-note test run serializes to 12,953
bytes; a large-signal fixture serializes to 45,969 bytes after downsampling.
Actual size grows with notes and accepted hits, and persistence enforces a 1 MiB
payload ceiling per run.

Automatic saving has a dedicated state: `NotSaved`, `Saving`, `Saved`, or
`SaveFailed`. Results remain fully usable while Room writes off the main thread.
Each attempt receives one UUID before recording; recomposition, repeated state
observation, navigation, and an identical repository save cannot create another
row. A failed save keeps the same immutable run and UUID so `Retry save` can
persist exactly the result already on screen.

`AppViewModel.openSavedRun(runId)` is the backend navigation entry point for PR
7.3. It passes only the run ID, loads the historical record through
`ExerciseRunRepository`, and maps it into the same Results presentation model
used by a current in-memory run. Missing, corrupt, and unsupported records show
safe UI errors rather than persistence exceptions. The loaded result and graph
never consult the WAV, current Settings, calibration, or edited exercise.

Historical Retry resolves the stored exercise ID against the current exercise
library. If the exercise still exists, Retry loads its current definition and
captures fresh session configuration under a new run ID. If it was deleted, the
historical result stays viewable and Retry is disabled; exercise reconstruction
is not part of PR 7.2.

Temporary WAV cleanup happens only after durable persistence succeeds. Normal
builds delete the completed WAV; debug builds retain it for diagnostics. A save
failure preserves the WAV and in-memory result. A cleanup failure is logged
separately and never changes a successfully persisted run back to failed.

## Exercise history

PR 7.3 adds a History action to each exercise in the library. The dedicated
screen observes lightweight `ExerciseRunSummary` projections for that exercise
and renders them in a lazy, stable-keyed list. Each row shows the saved date and
time, BPM, accuracy, hit rate, mean absolute timing error, timing bias, missed
notes, and extra hits. The graph, judged notes, and detailed payload are not
decoded merely to render this list, so histories with hundreds of runs remain
responsive.

History defaults to newest first and supports oldest first, best accuracy, and
lowest mean timing-error sorting with deterministic timestamp/run-ID ties. Its
BPM filter is generated from the BPM values actually present in the saved
summaries; selecting All restores the complete list. Sorting and filtering are
presentation-only and never rewrite persisted results.

Tapping a row passes only its run ID through the saved-run loader introduced in
PR 7.2, then opens the same production Results screen used for a current run.
Retry uses the current exercise and fresh Settings to start a new attempt with a
new run ID. If that exercise was deleted, its historical result remains
viewable and Retry stays disabled.

An individual run can be deleted only after confirmation. The Room query updates
the visible rows and run count reactively, while the source exercise and every
other run remain untouched. Exercise deletion still never cascade-deletes saved
runs; their exercise-name snapshots and result data remain historical truth.
There is not yet a global orphan archive UI.

Roadmap boundaries:

- PR 7.1: persistence infrastructure only.
- PR 7.2: automatic save, retry-save, and saved-run reopening infrastructure.
- PR 7.3: per-exercise history, filtering, sorting, reopening, and deletion.
- PR 7.4: progress summaries and trend charts built from the same summary data.

## Requirements

- Android Studio
- Android SDK 36
- JDK 21 for Gradle/CI
- Android 8.0 (API 26) or newer
- Microphone permission

A physical device is strongly recommended because audio latency, speaker
response, microphone response, and acoustic rejection vary by device. An
emulator is suitable for layout and navigation checks.

The bundled example exercise is located at
`app/src/main/assets/exercises/basic-quarter-notes.json`. The app also
initializes a B.A.D. exercise directory in shared Downloads where supported,
with an app-private fallback on older Android versions.

## Build and verify

From the repository root:

```shell
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

GitHub Actions runs the same unit-test, lint, and debug-build checks and uploads
the debug APK and reports for successful CI runs.

## Current limitations

- Exercises use one generic rhythmic lane rather than separate drum voices.
- There is no global browser for history belonging to deleted exercises.
- There is no progress tracking, cloud sync, AI coaching, or production audio
  playback.
- Calibration targets the phone speaker and built-in microphone path; changing
  audio routes or acoustic conditions can change timing and detection quality.
