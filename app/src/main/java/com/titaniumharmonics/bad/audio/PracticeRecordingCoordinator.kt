package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import java.io.File

enum class PracticeRecordingPhase {
    IDLE,
    INITIAL_COUNT_IN,
    ACTIVE_EXERCISE,
    PAUSED,
    RESUME_COUNT_IN,
    COMPLETED,
    CANCELLED,
    ERROR,
}

class PracticeRecordingCoordinator(
    private val recorder: SessionAudioRecorder,
    private val playbackController: DebugRecordingPlaybackController,
) {
    @Volatile
    var phase: PracticeRecordingPhase = PracticeRecordingPhase.IDLE
        private set

    @Volatile
    var completedSession: RecordedSession? = null
        private set

    private var runtimeExercise: RuntimeExercise? = null
    private var metronomeSnapshot: SessionMetronomeSnapshot? = null
    private var exerciseStartSampleFrame: Long? = null
    private var phaseBeforePause: PracticeRecordingPhase? = null

    fun startSession(
        exercise: RuntimeExercise,
        sessionMetronomeSnapshot: SessionMetronomeSnapshot =
            SessionMetronomeSnapshot.COMPATIBILITY_FALLBACK,
    ) {
        playbackController.deleteRecording()
        completedSession = null
        runtimeExercise = exercise
        metronomeSnapshot = sessionMetronomeSnapshot
        exerciseStartSampleFrame = null
        phaseBeforePause = null
        try {
            recorder.start()
            phase = PracticeRecordingPhase.INITIAL_COUNT_IN
        } catch (exception: Exception) {
            phase = PracticeRecordingPhase.ERROR
            runtimeExercise = null
            metronomeSnapshot = null
            throw exception
        }
    }

    fun markExerciseStarted(): Long {
        check(phase == PracticeRecordingPhase.INITIAL_COUNT_IN) {
            "Exercise start can only be marked after the initial count-in."
        }
        val sampleFrame = recorder.totalWrittenSampleFrames
        check(sampleFrame >= 0L) { "Recorder returned a negative sample-frame count." }
        exerciseStartSampleFrame = sampleFrame
        phase = PracticeRecordingPhase.ACTIVE_EXERCISE
        return sampleFrame
    }

    fun pauseSession() {
        check(
            phase == PracticeRecordingPhase.INITIAL_COUNT_IN ||
                phase == PracticeRecordingPhase.ACTIVE_EXERCISE,
        ) { "Recording cannot be paused from $phase." }
        val previousPhase = phase
        try {
            recorder.pause()
            phaseBeforePause = previousPhase
            phase = PracticeRecordingPhase.PAUSED
        } catch (exception: Exception) {
            failSession()
            throw exception
        }
    }

    fun beginResumeCountIn() {
        check(phase == PracticeRecordingPhase.PAUSED) {
            "Resume count-in requires a paused recording."
        }
        phase = PracticeRecordingPhase.RESUME_COUNT_IN
    }

    fun resumeSession(resumeExercise: () -> Unit) {
        check(phase == PracticeRecordingPhase.RESUME_COUNT_IN) {
            "Recording can resume only after the resume count-in."
        }
        val resumedPhase = checkNotNull(phaseBeforePause) {
            "The pre-pause recording phase is unavailable."
        }
        try {
            recorder.resume()
            resumeExercise()
            phase = resumedPhase
            phaseBeforePause = null
        } catch (exception: Exception) {
            failSession()
            throw exception
        }
    }

    fun completeSession(): RecordedSession {
        check(phase == PracticeRecordingPhase.ACTIVE_EXERCISE) {
            "Only an active exercise can produce a completed recording."
        }
        val exercise = checkNotNull(runtimeExercise) {
            "Runtime exercise is unavailable."
        }
        val startSampleFrame = checkNotNull(exerciseStartSampleFrame) {
            "Exercise-start sample frame was not captured."
        }
        val sessionMetronomeSnapshot = checkNotNull(metronomeSnapshot) {
            "Session metronome configuration is unavailable."
        }
        return try {
            val recording = recorder.finish()
            check(File(recording.filePath).isFile) {
                "Finalized WAV file is missing."
            }
            val session = RecordedSession(
                wavFilePath = recording.filePath,
                audioFormat = recording.format,
                totalRecordedSampleFrames = recording.totalSampleFrames,
                exerciseStartSampleFrame = startSampleFrame,
                runtimeExercise = exercise,
                metronomeSnapshot = sessionMetronomeSnapshot,
            )
            completedSession = session
            phase = PracticeRecordingPhase.COMPLETED
            playbackController.setRecording(session)
            session
        } catch (exception: Exception) {
            failSession()
            throw exception
        }
    }

    fun cancelSession() {
        if (phase in setOf(
                PracticeRecordingPhase.COMPLETED,
                PracticeRecordingPhase.CANCELLED,
                PracticeRecordingPhase.ERROR,
            )
        ) return
        recorder.cancel()
        completedSession = null
        runtimeExercise = null
        metronomeSnapshot = null
        exerciseStartSampleFrame = null
        phaseBeforePause = null
        phase = PracticeRecordingPhase.CANCELLED
    }

    fun deleteCompletedSession() {
        playbackController.deleteRecording()
        completedSession = null
        if (phase == PracticeRecordingPhase.COMPLETED) {
            phase = PracticeRecordingPhase.IDLE
        }
    }

    fun release() {
        if (phase !in setOf(PracticeRecordingPhase.COMPLETED, PracticeRecordingPhase.IDLE)) {
            runCatching { recorder.cancel() }
            completedSession = null
            phase = PracticeRecordingPhase.CANCELLED
        }
        recorder.release()
        playbackController.release()
    }

    private fun failSession() {
        runCatching { recorder.cancel() }
        completedSession = null
        runtimeExercise = null
        metronomeSnapshot = null
        exerciseStartSampleFrame = null
        phaseBeforePause = null
        phase = PracticeRecordingPhase.ERROR
    }
}
