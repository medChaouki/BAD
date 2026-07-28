package com.titaniumharmonics.bad.ui.practice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.titaniumharmonics.bad.audio.AudioTrackMetronome
import com.titaniumharmonics.bad.audio.MetronomePlayer
import com.titaniumharmonics.bad.exercise.AssetExerciseLoader
import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExercisePlaybackSettings
import com.titaniumharmonics.bad.timing.AndroidMonotonicClock
import com.titaniumharmonics.bad.timing.ExerciseTiming
import com.titaniumharmonics.bad.timing.MonotonicClock
import com.titaniumharmonics.bad.timing.PlaybackPhase
import com.titaniumharmonics.bad.timing.SessionElapsedClock
import com.titaniumharmonics.bad.timing.SessionProgressCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class PracticeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val clock: MonotonicClock = AndroidMonotonicClock
    private val exerciseLoader = AssetExerciseLoader(application.assets)
    private val metronomePlayer: MetronomePlayer = AudioTrackMetronome(clock)
    private val sessionElapsedClock = SessionElapsedClock(clock)

    private val mutableUiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = mutableUiState.asStateFlow()

    private var loadJob: Job? = null
    private var playbackJob: Job? = null
    private var audioControlJob: Job? = null
    private var restartJob: Job? = null
    private var phaseBeforePause: PracticePhase = PracticePhase.RUNNING

    fun loadExercise() {
        if (loadJob?.isActive == true) return
        stopPlayback()
        mutableUiState.value = PracticeUiState(phase = PracticePhase.LOADING)

        loadJob = viewModelScope.launch {
            try {
                val exercise = withContext(Dispatchers.IO) {
                    exerciseLoader.load(DEFAULT_EXERCISE_ASSET)
                }
                val timing = ExerciseTiming(exercise)
                mutableUiState.value = PracticeUiState(
                    exercise = exercise,
                    playbackSettings = ExercisePlaybackSettings.fromExercise(exercise),
                    phase = PracticePhase.READY,
                    exerciseElapsedNanos = -timing.countInDurationNanos,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = PracticeUiState(
                    phase = PracticePhase.ERROR,
                    errorMessage = exception.message ?: "Exercise loading failed.",
                )
            }
        }
    }

    fun unloadExercise() {
        loadJob?.cancel()
        loadJob = null
        stopPlayback()
        mutableUiState.value = PracticeUiState()
    }

    fun startPlayback() {
        if (restartJob?.isActive == true || playbackJob?.isActive == true) return
        val previousPlaybackJob = playbackJob
        if (previousPlaybackJob != null && !previousPlaybackJob.isCompleted) {
            restartJob = viewModelScope.launch {
                previousPlaybackJob.join()
                if (mutableUiState.value.phase == PracticePhase.READY) {
                    beginPlayback()
                }
            }
            return
        }
        beginPlayback()
    }

    private fun beginPlayback() {
        val state = mutableUiState.value
        val exercise = state.playbackExercise ?: return
        val downbeatsOnly = state.playbackSettings?.downbeatsOnly == true

        val timing = ExerciseTiming(exercise)
        val progressCalculator = SessionProgressCalculator(timing)
        mutableUiState.value = mutableUiState.value.copy(
            phase = PracticePhase.PREPARING,
            exerciseElapsedNanos = -timing.countInDurationNanos,
            countInBeatsRemaining = exercise.countInMeasures *
                exercise.timeSignature.numerator,
            errorMessage = null,
        )

        playbackJob = viewModelScope.launch {
            try {
                val playbackStartedNanos = withContext(Dispatchers.IO) {
                    metronomePlayer.start(
                        exercise = exercise,
                        downbeatsOnly = downbeatsOnly,
                    )
                }
                sessionElapsedClock.start(playbackStartedNanos)

                while (isActive) {
                    if (sessionElapsedClock.isPaused) {
                        delay(UI_UPDATE_INTERVAL_MILLIS)
                        continue
                    }
                    val sessionElapsedNanos =
                        sessionElapsedClock.elapsedNanos() ?: break
                    val progress = progressCalculator.calculate(sessionElapsedNanos)
                    mutableUiState.value = mutableUiState.value.copy(
                        phase = progress.phase.toUiPhase(),
                        exerciseElapsedNanos = progress.exerciseElapsedNanos,
                        countInBeatsRemaining = countInBeatsRemaining(
                            exerciseElapsedNanos = progress.exerciseElapsedNanos,
                            beatDurationNanos = timing.beatDurationNanos,
                        ),
                    )

                    if (progress.phase == PlaybackPhase.COMPLETED) break
                    delay(UI_UPDATE_INTERVAL_MILLIS)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    phase = PracticePhase.ERROR,
                    errorMessage = exception.message ?: "Metronome playback failed.",
                )
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    metronomePlayer.stop()
                }
            }
        }
    }

    fun pausePlayback() {
        val state = mutableUiState.value
        if (state.phase !in setOf(PracticePhase.COUNTING_IN, PracticePhase.RUNNING)) return
        if (audioControlJob?.isActive == true) return

        val exercise = state.playbackExercise ?: return
        val timing = ExerciseTiming(exercise)
        val sessionElapsedNanos = sessionElapsedClock.pause() ?: return
        val progress = SessionProgressCalculator(timing).calculate(sessionElapsedNanos)
        phaseBeforePause = progress.phase.toUiPhase()
        mutableUiState.value = state.copy(
            phase = PracticePhase.PAUSED,
            exerciseElapsedNanos = progress.exerciseElapsedNanos,
            countInBeatsRemaining = countInBeatsRemaining(
                exerciseElapsedNanos = progress.exerciseElapsedNanos,
                beatDurationNanos = timing.beatDurationNanos,
            ),
        )

        audioControlJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    metronomePlayer.pause()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                sessionElapsedClock.resume()
                playbackJob?.cancel()
                mutableUiState.value = mutableUiState.value.copy(
                    phase = PracticePhase.ERROR,
                    errorMessage = exception.message ?: "Unable to pause metronome playback.",
                )
            }
        }
    }

    fun resumePlayback() {
        val state = mutableUiState.value
        if (state.phase != PracticePhase.PAUSED) return
        if (audioControlJob?.isActive == true) return
        val exercise = state.playbackExercise ?: return

        audioControlJob = viewModelScope.launch {
            try {
                if (exercise.countInMeasures > 0) {
                    runResumeCountIn(exercise)
                } else {
                    resumePausedPlayback()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                playbackJob?.cancel()
                mutableUiState.value = mutableUiState.value.copy(
                    phase = PracticePhase.ERROR,
                    errorMessage = exception.message ?: "Unable to resume metronome playback.",
                )
            }
        }
    }

    private suspend fun runResumeCountIn(exercise: Exercise) {
        val timing = ExerciseTiming(exercise)
        mutableUiState.value = mutableUiState.value.copy(
            phase = PracticePhase.RESUME_COUNT_IN,
            countInBeatsRemaining = exercise.countInMeasures *
                exercise.timeSignature.numerator,
            errorMessage = null,
        )
        val countInStartedNanos = withContext(Dispatchers.IO) {
            metronomePlayer.startResumeCountIn(exercise)
        }

        while (true) {
            val countInElapsedNanos =
                (clock.nowNanos() - countInStartedNanos).coerceAtLeast(0L)
            val countInRemainingNanos =
                (timing.countInDurationNanos - countInElapsedNanos).coerceAtLeast(0L)
            mutableUiState.value = mutableUiState.value.copy(
                countInBeatsRemaining = ceil(
                    countInRemainingNanos.toDouble() / timing.beatDurationNanos,
                ).toInt(),
            )
            if (countInElapsedNanos >= timing.countInDurationNanos) break
            delay(UI_UPDATE_INTERVAL_MILLIS)
        }

        resumePausedPlayback()
    }

    private suspend fun resumePausedPlayback() {
        withContext(Dispatchers.IO) {
            metronomePlayer.resume()
        }
        sessionElapsedClock.resume()
        mutableUiState.value = mutableUiState.value.copy(
            phase = phaseBeforePause,
            countInBeatsRemaining = 0,
            errorMessage = null,
        )
    }

    fun restartPlayback() {
        if (restartJob?.isActive == true) return
        if (!mutableUiState.value.phase.isPlayerVisible()) return

        restartJob = viewModelScope.launch {
            audioControlJob?.cancelAndJoin()
            audioControlJob = null
            val previousPlaybackJob = playbackJob
            playbackJob = null
            previousPlaybackJob?.cancelAndJoin()
            sessionElapsedClock.reset()
            beginPlayback()
        }
    }

    fun stopPlayback() {
        val state = mutableUiState.value
        val playerWasVisible = state.phase.isPlayerVisible()
        restartJob?.cancel()
        restartJob = null
        audioControlJob?.cancel()
        audioControlJob = null
        playbackJob?.cancel()
        sessionElapsedClock.reset()

        if (!playerWasVisible) return
        val exercise = state.playbackExercise ?: return
        val timing = ExerciseTiming(exercise)
        mutableUiState.value = state.copy(
            phase = PracticePhase.READY,
            exerciseElapsedNanos = -timing.countInDurationNanos,
            countInBeatsRemaining = 0,
            errorMessage = null,
        )
    }

    fun decreaseTempo() {
        updatePlaybackSettings { settings ->
            settings.copy(
                tempoBpm = (settings.tempoBpm - ExercisePlaybackSettings.TEMPO_STEP_BPM)
                    .coerceAtLeast(ExercisePlaybackSettings.MIN_TEMPO_BPM),
            )
        }
    }

    fun increaseTempo() {
        updatePlaybackSettings { settings ->
            settings.copy(
                tempoBpm = (settings.tempoBpm + ExercisePlaybackSettings.TEMPO_STEP_BPM)
                    .coerceAtMost(ExercisePlaybackSettings.MAX_TEMPO_BPM),
            )
        }
    }

    fun setCountInEnabled(enabled: Boolean) {
        updatePlaybackSettings { settings ->
            settings.copy(countInEnabled = enabled)
        }
    }

    fun setDownbeatsOnly(enabled: Boolean) {
        updatePlaybackSettings { settings ->
            settings.copy(downbeatsOnly = enabled)
        }
    }

    fun decreaseMeasureCount() {
        updatePlaybackSettings { settings ->
            settings.copy(
                measureCount = (settings.measureCount - 1)
                    .coerceAtLeast(ExercisePlaybackSettings.MIN_MEASURE_COUNT),
            )
        }
    }

    fun increaseMeasureCount() {
        updatePlaybackSettings { settings ->
            settings.copy(
                measureCount = (settings.measureCount + 1)
                    .coerceAtMost(ExercisePlaybackSettings.MAX_MEASURE_COUNT),
            )
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        restartJob?.cancel()
        audioControlJob?.cancel()
        playbackJob?.cancel()
        metronomePlayer.stop()
        super.onCleared()
    }

    private fun PlaybackPhase.toUiPhase(): PracticePhase = when (this) {
        PlaybackPhase.COUNTING_IN -> PracticePhase.COUNTING_IN
        PlaybackPhase.RUNNING -> PracticePhase.RUNNING
        PlaybackPhase.COMPLETED -> PracticePhase.COMPLETED
    }

    private fun countInBeatsRemaining(
        exerciseElapsedNanos: Long,
        beatDurationNanos: Long,
    ): Int {
        if (exerciseElapsedNanos >= 0L) return 0
        return ceil(-exerciseElapsedNanos.toDouble() / beatDurationNanos)
            .toInt()
            .coerceAtLeast(1)
    }

    private fun updatePlaybackSettings(
        transform: (ExercisePlaybackSettings) -> ExercisePlaybackSettings,
    ) {
        val state = mutableUiState.value
        if (state.phase.isPlaybackActive()) return
        val settings = state.playbackSettings ?: return
        val updatedSettings = transform(settings)
        val exercise = state.exercise ?: return
        val timing = ExerciseTiming(updatedSettings.applyTo(exercise))
        mutableUiState.value = state.copy(
            playbackSettings = updatedSettings,
            exerciseElapsedNanos = -timing.countInDurationNanos,
            countInBeatsRemaining = 0,
            errorMessage = null,
        )
    }

    private fun PracticePhase.isPlaybackActive(): Boolean = this in setOf(
        PracticePhase.PREPARING,
        PracticePhase.COUNTING_IN,
        PracticePhase.RUNNING,
        PracticePhase.PAUSED,
        PracticePhase.RESUME_COUNT_IN,
    )

    private fun PracticePhase.isPlayerVisible(): Boolean = this in setOf(
        PracticePhase.PREPARING,
        PracticePhase.COUNTING_IN,
        PracticePhase.RUNNING,
        PracticePhase.PAUSED,
        PracticePhase.RESUME_COUNT_IN,
        PracticePhase.COMPLETED,
    )

    private companion object {
        const val DEFAULT_EXERCISE_ASSET = "basic-quarter-notes.json"
        const val UI_UPDATE_INTERVAL_MILLIS = 16L
    }
}
