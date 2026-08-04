package com.titaniumharmonics.bad.audio.calibration

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalibrationAudioTrackPlayerInstrumentedTest {
    @Test
    fun calibrationSequenceInitializesAndPlaysThroughBuiltInSpeaker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val speaker = requireNotNull(
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            },
        )
        val sequence = CalibrationClickSequenceGenerator.generate(sampleRateHz = 48_000)
        val playbackStarted = AtomicBoolean(false)
        val invalidRouteObserved = AtomicBoolean(false)

        val startFrame = CalibrationAudioTrackPlayer().playAndWait(
            sequence = sequence,
            preferredSpeaker = speaker,
            beforePlay = { 12_345L },
            onPlaybackStarted = { playbackStarted.set(true) },
            onRouteInvalid = { invalidRouteObserved.set(true) },
            cancellationCheck = {},
        )

        assertTrue(playbackStarted.get())
        assertFalse(invalidRouteObserved.get())
        assertTrue(startFrame == 12_345L)
    }
}
