package com.titaniumharmonics.bad.audio.calibration

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.titaniumharmonics.bad.audio.AudioRecordWavSessionRecorder
import com.titaniumharmonics.bad.audio.FinalizedRecording
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

data class AndroidCalibrationRouteSnapshot(
    val decision: CalibrationRouteDecision,
    val builtInSpeaker: AudioDeviceInfo?,
    val builtInMicrophone: AudioDeviceInfo?,
)

class AndroidCalibrationRouteChecker(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun check(): AndroidCalibrationRouteSnapshot {
        val connectedOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val anticipatedOutputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioManager.getAudioDevicesForAttributes(mediaAttributes)
        } else {
            connectedOutputs
        }
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        return AndroidCalibrationRouteSnapshot(
            decision = CalibrationRoutePolicy.evaluate(
                outputDevices = anticipatedOutputs.mapTo(mutableSetOf(), ::deviceKind),
                inputDevices = inputs.mapTo(mutableSetOf(), ::deviceKind),
            ),
            builtInSpeaker = connectedOutputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            },
            builtInMicrophone = inputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
            },
        )
    }

    fun registerRouteChangeCallback(onChanged: () -> Unit): AudioDeviceCallback {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (addedDevices.any { deviceKind(it).isExternal() }) onChanged()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (removedDevices.any {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                            deviceKind(it).isExternal()
                    }
                ) onChanged()
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        return callback
    }

    fun unregisterRouteChangeCallback(callback: AudioDeviceCallback) {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    companion object {
        fun deviceKind(device: AudioDeviceInfo): CalibrationAudioDeviceKind = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CalibrationAudioDeviceKind.BUILT_IN_SPEAKER
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CalibrationAudioDeviceKind.BUILT_IN_EARPIECE
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> CalibrationAudioDeviceKind.BUILT_IN_MICROPHONE
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            -> CalibrationAudioDeviceKind.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
            -> CalibrationAudioDeviceKind.BLUETOOTH
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            -> CalibrationAudioDeviceKind.USB
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            -> CalibrationAudioDeviceKind.HDMI
            AudioDeviceInfo.TYPE_DOCK -> CalibrationAudioDeviceKind.DOCK
            else -> CalibrationAudioDeviceKind.OTHER
        }

        private fun CalibrationAudioDeviceKind.isExternal(): Boolean = this in setOf(
            CalibrationAudioDeviceKind.WIRED,
            CalibrationAudioDeviceKind.BLUETOOTH,
            CalibrationAudioDeviceKind.USB,
            CalibrationAudioDeviceKind.HDMI,
            CalibrationAudioDeviceKind.DOCK,
        )
    }
}

class CalibrationAudioTrackPlayer {
    private var audioTrack: AudioTrack? = null

    fun playAndWait(
        sequence: CalibrationClickSequence,
        preferredSpeaker: AudioDeviceInfo,
        beforePlay: () -> Long,
        onPlaybackStarted: () -> Unit,
        onRouteInvalid: () -> Unit,
        cancellationCheck: () -> Unit,
    ): Long {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sequence.sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minimumBufferSizeBytes = AudioTrack.getMinBufferSize(
            sequence.sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferSizeBytes > 0) {
            "Unable to determine the calibration audio buffer size."
        }
        val chunkSizeSamples = max(
            sequence.sampleRateHz / STREAM_CHUNKS_PER_SECOND,
            1,
        )
        val streamBufferSizeBytes = max(
            minimumBufferSizeBytes,
            chunkSizeSamples * Short.SIZE_BYTES,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(streamBufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        check(track.state == AudioTrack.STATE_INITIALIZED) {
            "Unable to initialize calibration audio output."
        }
        if (!track.setPreferredDevice(preferredSpeaker)) {
            // Some Android audio implementations reject the optional preferred-device hint
            // even though AudioPolicy has already selected the built-in speaker. The routed
            // device is still checked below while playback is active.
            Log.w(LOG_TAG, "Built-in speaker preference was rejected; validating the actual route.")
        }
        val listener = AudioRouting.OnRoutingChangedListener { routing ->
            if (routing.routedDevice?.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                onRouteInvalid()
            }
        }
        track.addOnRoutingChangedListener(listener, null)
        try {
            var writeOffset = 0
            val prebufferSize = min(track.bufferSizeInFrames, sequence.samples.size)
            writeOffset += writeFully(
                track = track,
                samples = sequence.samples,
                offset = writeOffset,
                size = prebufferSize,
                cancellationCheck = cancellationCheck,
            )
            val playbackStartSampleFrame = beforePlay()
            track.play()
            onPlaybackStarted()
            val timeoutNanos = System.nanoTime() +
                (sequence.samples.size * 1_000_000_000L / sequence.sampleRateHz) +
                PLAYBACK_TIMEOUT_MARGIN_NANOS
            while (writeOffset < sequence.samples.size) {
                cancellationCheck()
                check(System.nanoTime() < timeoutNanos) { "Calibration playback timed out." }
                val requested = min(chunkSizeSamples, sequence.samples.size - writeOffset)
                writeOffset += writeFully(
                    track = track,
                    samples = sequence.samples,
                    offset = writeOffset,
                    size = requested,
                    cancellationCheck = cancellationCheck,
                )
            }
            while (track.playbackHeadPosition.toLong() < sequence.samples.size) {
                cancellationCheck()
                check(System.nanoTime() < timeoutNanos) { "Calibration playback timed out." }
                if (track.routedDevice?.type?.let { it != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } == true) {
                    onRouteInvalid()
                }
                Thread.sleep(20L)
            }
            return playbackStartSampleFrame
        } finally {
            track.removeOnRoutingChangedListener(listener)
            release()
        }
    }

    private fun writeFully(
        track: AudioTrack,
        samples: ShortArray,
        offset: Int,
        size: Int,
        cancellationCheck: () -> Unit,
    ): Int {
        var writtenTotal = 0
        while (writtenTotal < size) {
            cancellationCheck()
            val written = track.write(
                samples,
                offset + writtenTotal,
                size - writtenTotal,
                AudioTrack.WRITE_BLOCKING,
            )
            check(written > 0) { "Calibration click buffer write failed: $written." }
            writtenTotal += written
        }
        return writtenTotal
    }

    fun release() {
        val track = audioTrack ?: return
        audioTrack = null
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        track.release()
    }

    private companion object {
        const val LOG_TAG = "CalibrationAudio"
        const val PLAYBACK_TIMEOUT_MARGIN_NANOS = 2_000_000_000L
        const val STREAM_CHUNKS_PER_SECOND = 50
    }
}

sealed interface CalibrationRunResult {
    data class Success(
        val calibration: TimingCalibration,
        val diagnostics: CalibrationDiagnostics?,
    ) : CalibrationRunResult
    data class Failure(
        val reason: CalibrationFailureReason,
        val diagnostics: CalibrationDiagnostics? = null,
        val reviewableCalibration: TimingCalibration? = null,
    ) : CalibrationRunResult
}

class AndroidTimingCalibrationRunner(
    context: Context,
    private val configuration: TimingCalibrationConfig = TimingCalibrationConfig(),
    private val routeChecker: AndroidCalibrationRouteChecker = AndroidCalibrationRouteChecker(context),
    private val processor: TimingCalibrationProcessor = TimingCalibrationProcessor(configuration),
    private val retainDebugRecording: Boolean,
) {
    private val applicationContext = context.applicationContext
    private var recorder: AudioRecordWavSessionRecorder? = null
    private var player: CalibrationAudioTrackPlayer? = null
    private val cancelled = AtomicBoolean(false)

    fun checkRoute(): AndroidCalibrationRouteSnapshot = routeChecker.check()

    fun run(
        uncertainRouteConfirmed: Boolean,
        onPhase: (CalibrationPhase) -> Unit,
        nowEpochMillis: () -> Long = System::currentTimeMillis,
    ): CalibrationRunResult {
        cancelled.set(false)
        val route = routeChecker.check()
        if (
            route.decision.status == CalibrationRouteStatus.BLOCKED ||
            route.decision.status == CalibrationRouteStatus.UNCERTAIN && !uncertainRouteConfirmed
        ) return CalibrationRunResult.Failure(CalibrationFailureReason.EXTERNAL_AUDIO_ROUTE)
        val speaker = route.builtInSpeaker
            ?: return CalibrationRunResult.Failure(CalibrationFailureReason.AUDIO_OUTPUT_UNAVAILABLE)
        val microphone = route.builtInMicrophone
            ?: return CalibrationRunResult.Failure(CalibrationFailureReason.MICROPHONE_UNAVAILABLE)
        val routeChanged = AtomicBoolean(false)
        val callback = routeChecker.registerRouteChangeCallback { routeChanged.set(true) }
        var finalized: FinalizedRecording? = null
        try {
            onPhase(CalibrationPhase.PREPARING)
            val activeRecorder = AudioRecordWavSessionRecorder(
                context = applicationContext,
                recordingsDirectoryName = "B.A.D/calibration",
                partialFileName = "timing-calibration.partial",
                recordingFileName = "timing-calibration.wav",
                preferredInputDevice = microphone,
            ).also { recorder = it }
            try {
                activeRecorder.start()
            } catch (exception: SecurityException) {
                throw exception
            } catch (_: Exception) {
                return CalibrationRunResult.Failure(CalibrationFailureReason.MICROPHONE_UNAVAILABLE)
            }
            onPhase(CalibrationPhase.RECORDING)
            cancellableWait(configuration.microphoneWarmupMillis) {
                checkActive(routeChanged)
            }
            activeRecorder.routedDeviceType()?.let {
                if (it != AudioDeviceInfo.TYPE_BUILTIN_MIC) routeChanged.set(true)
            }
            checkActive(routeChanged)
            val sampleRate = activeRecorder.format?.sampleRateHz
                ?: return CalibrationRunResult.Failure(CalibrationFailureReason.MICROPHONE_UNAVAILABLE)
            if (sampleRate !in setOf(48_000, 44_100)) {
                return CalibrationRunResult.Failure(CalibrationFailureReason.UNSUPPORTED_SAMPLE_RATE)
            }
            val sequence = CalibrationClickSequenceGenerator.generate(sampleRate, configuration)
            val activePlayer = CalibrationAudioTrackPlayer().also { player = it }
            val playbackStartFrame = try {
                activePlayer.playAndWait(
                    sequence = sequence,
                    preferredSpeaker = speaker,
                    beforePlay = { activeRecorder.totalWrittenSampleFrames },
                    onPlaybackStarted = { onPhase(CalibrationPhase.PLAYING_CLICKS) },
                    onRouteInvalid = { routeChanged.set(true) },
                    cancellationCheck = { checkActive(routeChanged) },
                )
            } catch (exception: CalibrationCancelledException) {
                throw exception
            } catch (exception: CalibrationRouteChangedException) {
                throw exception
            } catch (exception: Exception) {
                if (cancelled.get()) throw CalibrationCancelledException()
                Log.e(LOG_TAG, "Calibration click playback failed.", exception)
                return CalibrationRunResult.Failure(CalibrationFailureReason.PLAYBACK_FAILED)
            }
            checkActive(routeChanged)
            finalized = try {
                activeRecorder.finish()
            } catch (_: Exception) {
                if (cancelled.get()) throw CalibrationCancelledException()
                return CalibrationRunResult.Failure(CalibrationFailureReason.RECORDING_FAILED)
            }
            recorder = null
            onPhase(CalibrationPhase.PROCESSING)
            val processed = processor.process(
                recording = finalized,
                playbackStartSampleFrame = playbackStartFrame,
                calibratedAtEpochMillis = nowEpochMillis(),
                cancellationCheck = { checkActive(routeChanged) },
            )
            return when (processed) {
                is CalibrationProcessingResult.Success -> {
                    CalibrationRunResult.Success(
                        processed.calibration,
                        processed.diagnostics.takeIf { retainDebugRecording },
                    )
                }
                is CalibrationProcessingResult.Failure -> CalibrationRunResult.Failure(
                    processed.reason,
                    processed.diagnostics.takeIf { retainDebugRecording },
                    processed.reviewableCalibration,
                )
            }
        } catch (_: CalibrationCancelledException) {
            return CalibrationRunResult.Failure(CalibrationFailureReason.CANCELLED)
        } catch (_: CalibrationRouteChangedException) {
            return CalibrationRunResult.Failure(CalibrationFailureReason.AUDIO_ROUTE_CHANGED)
        } catch (_: SecurityException) {
            return CalibrationRunResult.Failure(CalibrationFailureReason.MICROPHONE_PERMISSION_DENIED)
        } catch (_: Exception) {
            return CalibrationRunResult.Failure(CalibrationFailureReason.UNKNOWN)
        } finally {
            routeChecker.unregisterRouteChangeCallback(callback)
            player?.release()
            player = null
            recorder?.cancel()
            recorder = null
            if (!retainDebugRecording) finalized?.filePath?.let { File(it).delete() }
        }
    }

    fun cancel() {
        cancelled.set(true)
        player?.release()
        recorder?.cancel()
    }

    fun release() = cancel()

    private fun checkActive(routeChanged: AtomicBoolean) {
        if (cancelled.get()) throw CalibrationCancelledException()
        if (routeChanged.get()) throw CalibrationRouteChangedException()
    }

    private fun cancellableWait(durationMillis: Long, check: () -> Unit) {
        var remaining = durationMillis
        while (remaining > 0L) {
            check()
            val interval = min(remaining, 25L)
            Thread.sleep(interval)
            remaining -= interval
        }
    }

    private companion object {
        const val LOG_TAG = "TimingCalibration"
    }
}

private class CalibrationCancelledException : Exception()
private class CalibrationRouteChangedException : Exception()
