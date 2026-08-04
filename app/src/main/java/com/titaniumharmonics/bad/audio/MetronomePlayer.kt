package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration

interface MetronomePlayer {
    fun start(
        exercise: RuntimeExercise,
        downbeatsOnly: Boolean,
        configuration: MetronomeConfiguration,
    ): Long

    fun pause()

    fun startResumeCountIn(
        exercise: RuntimeExercise,
        configuration: MetronomeConfiguration,
    ): Long

    fun resume()

    fun stop()
}
