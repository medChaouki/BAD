package com.titaniumharmonics.bad.timing

fun interface MonotonicClock {
    fun nowNanos(): Long
}
