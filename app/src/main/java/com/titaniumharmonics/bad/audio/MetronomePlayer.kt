package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.Exercise

interface MetronomePlayer {
    fun start(
        exercise: Exercise,
        downbeatsOnly: Boolean,
    ): Long

    fun stop()
}
