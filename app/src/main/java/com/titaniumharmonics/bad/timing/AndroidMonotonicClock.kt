package com.titaniumharmonics.bad.timing

import android.os.SystemClock

object AndroidMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
