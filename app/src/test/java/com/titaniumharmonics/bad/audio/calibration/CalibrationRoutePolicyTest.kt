package com.titaniumharmonics.bad.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationRoutePolicyTest {
    @Test
    fun builtInSpeakerAndMicrophoneAreAllowed() {
        val decision = CalibrationRoutePolicy.evaluate(
            setOf(CalibrationAudioDeviceKind.BUILT_IN_SPEAKER, CalibrationAudioDeviceKind.BUILT_IN_EARPIECE),
            setOf(CalibrationAudioDeviceKind.BUILT_IN_MICROPHONE),
        )
        assertEquals(CalibrationRouteStatus.ALLOWED, decision.status)
    }

    @Test
    fun wiredBluetoothUsbHdmiAndDockAreBlocked() {
        listOf(
            CalibrationAudioDeviceKind.WIRED,
            CalibrationAudioDeviceKind.BLUETOOTH,
            CalibrationAudioDeviceKind.USB,
            CalibrationAudioDeviceKind.HDMI,
            CalibrationAudioDeviceKind.DOCK,
        ).forEach { external ->
            assertEquals(
                CalibrationRouteStatus.BLOCKED,
                CalibrationRoutePolicy.evaluate(
                    setOf(CalibrationAudioDeviceKind.BUILT_IN_SPEAKER, external),
                    setOf(CalibrationAudioDeviceKind.BUILT_IN_MICROPHONE),
                ).status,
            )
        }
    }

    @Test
    fun unknownOrMissingRouteRequiresConfirmation() {
        assertEquals(
            CalibrationRouteStatus.UNCERTAIN,
            CalibrationRoutePolicy.evaluate(setOf(CalibrationAudioDeviceKind.OTHER), emptySet()).status,
        )
    }
}
