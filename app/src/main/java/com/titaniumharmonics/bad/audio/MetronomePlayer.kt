package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.RuntimeExercise

interface MetronomePlayer {
    fun start(
        exercise: RuntimeExercise,
        downbeatsOnly: Boolean,
    ): Long

    fun pause()

    fun startResumeCountIn(exercise: RuntimeExercise): Long

    fun resume()

    fun stop()
}
