package com.titaniumharmonics.bad.history.persistence

import com.titaniumharmonics.bad.audio.calibration.CalibrationConfidence
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfiguration
import com.titaniumharmonics.bad.audio.detection.MetronomeRejectionConfiguration
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.detection.UncertainCandidateBehaviour
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.JudgementConfiguration
import com.titaniumharmonics.bad.audio.matching.RuntimeExerciseSampleTimeline
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.MetronomeNotchConfiguration
import com.titaniumharmonics.bad.audio.metronome.MetronomeToneConfiguration
import com.titaniumharmonics.bad.audio.metronome.MetronomeWindow
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.audio.result.ExtraHit
import com.titaniumharmonics.bad.audio.result.JudgedNote
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionEnvelopePoint
import com.titaniumharmonics.bad.audio.result.ProductionExpectedNoteMarker
import com.titaniumharmonics.bad.audio.result.ProductionExtraHitMarker
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.audio.result.ProductionMatchedHitMarker
import com.titaniumharmonics.bad.audio.result.ProductionMeasureGuide
import com.titaniumharmonics.bad.audio.result.ProductionMissedNoteMarker
import com.titaniumharmonics.bad.audio.result.ProductionTimingConnector
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.history.ExerciseRun
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object ExerciseRunPayloadCodec {
    const val MAXIMUM_PAYLOAD_BYTES = 1_048_576

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = false
    }

    fun encode(run: ExerciseRun): String {
        require(run.schemaVersion == ExerciseRun.CURRENT_SCHEMA_VERSION) {
            "Only the current run schema can be saved."
        }
        validateRun(run)
        return json.encodeToString(ExerciseRunPayloadDto.fromDomain(run))
    }

    fun decode(payload: String): ExerciseRun {
        val schemaVersion = try {
            json.parseToJsonElement(payload)
                .jsonObject["schemaVersion"]
                ?.jsonPrimitive
                ?.intOrNull
        } catch (exception: RuntimeException) {
            throw CorruptedRunPayloadException(exception)
        } ?: throw CorruptedRunPayloadException()
        if (schemaVersion != ExerciseRun.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedRunSchemaException(schemaVersion)
        }
        return try {
            val run = json.decodeFromString<ExerciseRunPayloadDto>(payload).toDomain()
            validateRun(run)
            run
        } catch (exception: UnsupportedRunSchemaException) {
            throw exception
        } catch (exception: InvalidProductionGraphPayloadException) {
            throw exception
        } catch (exception: SerializationException) {
            throw CorruptedRunPayloadException(exception)
        } catch (exception: IllegalArgumentException) {
            throw CorruptedRunPayloadException(exception)
        } catch (exception: IllegalStateException) {
            throw CorruptedRunPayloadException(exception)
        }
    }

    fun sizeBytes(payload: String): Int = payload.toByteArray(Charsets.UTF_8).size

    private fun validateRun(run: ExerciseRun) {
        val result = run.practiceResult
        val graph = run.productionGraph
        require(result.runtimeExercise.id == result.exerciseId)
        require(result.runtimeExercise.name == result.exerciseName)
        require(result.runtimeExercise.tempoBpm.toBits() == result.bpm.toBits())
        require(result.timingCalibration == result.detectionSnapshot.timingCalibration)
        require(result.calibrationApplied == (result.calibrationOffsetSamples != 0L))
        val expectedTimeline = RuntimeExerciseSampleTimeline.expectedNotes(
            result.runtimeExercise,
            result.sampleRateHz,
        )
        require(expectedTimeline.size == result.judgedNotes.size)
        expectedTimeline.zip(result.judgedNotes).forEach { (expected, judged) ->
            require(expected.index == judged.expectedNoteIndex)
            require(expected.exerciseSample == judged.expectedExerciseSample)
            require(expected.note.measureIndex == judged.measureIndex)
            require(expected.note.positionInMeasureTicks == judged.positionInMeasureTicks)
        }
        result.judgedNotes.forEach { note ->
            require(note.expectedNoteIndex >= 0)
            require(note.expectedExerciseSample >= 0L)
            require(note.expectedExerciseTimeMillis.isFinite())
            require(note.measureIndex >= 0)
            require(note.positionInMeasureTicks >= 0L)
            require(note.beatPosition.isFinite())
            note.validateMatchState()
        }
        result.extraHits.forEach { extra ->
            require(extra.detectedHitIndex >= 0)
            require(extra.rawSample == extra.detectedHit.rawExerciseSample)
            require(extra.calibratedSample == extra.detectedHit.calibratedExerciseSample)
            require(extra.relativeIntensity.isUnitValue())
            require(extra.confidence.isUnitValue())
            extra.detectedHit.requireValid()
        }
        try {
            require(graph.exerciseId == result.exerciseId)
            require(graph.sampleRateHz == result.sampleRateHz)
            require(graph.envelopePoints.size <= ProductionGraphModel.MAXIMUM_ENVELOPE_POINT_COUNT)
            require(graph.envelopePoints.all { point ->
                point.exerciseSample >= 0L &&
                    point.exerciseTimeMillis.isFinite() && point.exerciseTimeMillis >= 0.0 &&
                    point.amplitude.isFinite() && point.amplitude >= 0.0f
            })
            require(graph.expectedNotes.size == result.judgedNotes.size)
            require(graph.expectedNotes.map { it.expectedNoteIndex }.toSet().size ==
                graph.expectedNotes.size)
            require(graph.matchedHits.all { marker ->
                marker.expectedNoteIndex >= 0 && marker.calibratedExerciseTimeMillis.isFinite() &&
                    marker.relativeIntensity.isUnitValue() && marker.confidence.isUnitValue()
            })
            require(graph.timingConnectors.all { connector ->
                connector.expectedNoteIndex >= 0 && connector.timingErrorMillis.isFinite() &&
                    connector.calibratedActualSample - connector.expectedExerciseSample ==
                    connector.timingErrorSamples
            })
            require(graph.missedNotes.all { it.expectedNoteIndex >= 0 })
            require(graph.extraHits.all { marker ->
                marker.detectedHitIndex >= 0 && marker.calibratedExerciseTimeMillis.isFinite() &&
                    marker.relativeIntensity.isUnitValue() && marker.confidence.isUnitValue()
            })
            require(graph.measureGuides.all { it.measureIndex >= 0 && it.exerciseSample >= 0L })
            graph.expectedNotes.zip(result.judgedNotes).forEach { (marker, judged) ->
                require(marker.expectedNoteIndex == judged.expectedNoteIndex)
                require(marker.exerciseSample == judged.expectedExerciseSample)
                require(marker.judgement == judged.judgement)
            }
            require(graph.matchedHits.size == result.judgedNotes.count {
                it.judgement != HitJudgement.MISSED
            })
            require(graph.missedNotes.size == result.missedCount)
            require(graph.extraHits.size == result.extraCount)
            graph.extraHits.zip(result.extraHits).forEach { (marker, extra) ->
                require(marker.detectedHitIndex == extra.detectedHitIndex)
                require(marker.calibratedExerciseSample == extra.calibratedSample)
                require(marker.relativeIntensity.toBits() == extra.relativeIntensity.toBits())
            }
        } catch (exception: IllegalArgumentException) {
            throw InvalidProductionGraphPayloadException(exception)
        }
    }

    private fun JudgedNote.validateMatchState() {
        if (judgement == HitJudgement.MISSED) {
            require(matchedHit == null)
            require(rawDetectedSample == null && rawDetectedTimeMillis == null)
            require(calibratedDetectedSample == null && calibratedDetectedTimeMillis == null)
            require(timingErrorSamples == null && timingErrorMillis == null)
            require(detectionConfidence == null && relativeIntensity == null)
        } else {
            val hit = checkNotNull(matchedHit)
            hit.requireValid()
            require(rawDetectedSample == hit.rawExerciseSample)
            require(rawDetectedTimeMillis?.toBits() == hit.rawExerciseTimeMillis.toBits())
            require(calibratedDetectedSample == hit.calibratedExerciseSample)
            require(
                calibratedDetectedTimeMillis?.toBits() ==
                    hit.calibratedExerciseTimeMillis.toBits(),
            )
            require(checkNotNull(timingErrorSamples) ==
                hit.calibratedExerciseSample - expectedExerciseSample)
            require(checkNotNull(timingErrorMillis).isFinite())
            require(checkNotNull(detectionConfidence).isUnitValue())
            require(checkNotNull(relativeIntensity).isUnitValue())
        }
        targetIntensity?.let { require(it.isUnitValue()) }
    }

    private fun DetectedHit.requireValid() {
        require(rawExerciseTimeMillis.isFinite())
        require(calibratedExerciseTimeMillis.isFinite())
        require(peakTimeMillis.isFinite())
        require(peakAmplitude.isFinite())
        require(frameLevel.isFinite())
        require(envelope.isFinite())
        require(noiseFloor.isFinite())
        require(signalToNoiseRatio.isFinite())
        require(confidence.isUnitValue())
        require(metronomeBandRatio.isFinite())
        require(broadbandResidualEnergy.isFinite())
        require(spectralBandwidthHz.isFinite())
        require(spectralCentroidHz.isFinite())
    }

    private fun Double.isUnitValue(): Boolean = isFinite() && this in 0.0..1.0
}

internal class UnsupportedRunSchemaException(val schemaVersion: Int) :
    IllegalArgumentException("Unsupported run schema $schemaVersion.")

internal class CorruptedRunPayloadException(cause: Throwable? = null) :
    IllegalArgumentException("The stored run payload is corrupted.", cause)

internal class InvalidProductionGraphPayloadException(cause: Throwable? = null) :
    IllegalArgumentException("The stored production graph is invalid.", cause)

@Serializable
private data class ExerciseRunPayloadDto(
    val schemaVersion: Int,
    val runId: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val appVersion: String,
    val practiceResult: PracticeResultDto,
    val productionGraph: ProductionGraphDto,
) {
    fun toDomain(): ExerciseRun {
        if (schemaVersion != ExerciseRun.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedRunSchemaException(schemaVersion)
        }
        return ExerciseRun(
            runId = runId,
            startedAtEpochMillis = startedAtEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis,
            practiceResult = practiceResult.toDomain(),
            productionGraph = productionGraph.toDomain(),
            appVersion = appVersion,
            schemaVersion = schemaVersion,
        )
    }

    companion object {
        fun fromDomain(run: ExerciseRun) = ExerciseRunPayloadDto(
            schemaVersion = run.schemaVersion,
            runId = run.runId,
            startedAtEpochMillis = run.startedAtEpochMillis,
            completedAtEpochMillis = run.completedAtEpochMillis,
            appVersion = run.appVersion,
            practiceResult = PracticeResultDto.fromDomain(run.practiceResult),
            productionGraph = ProductionGraphDto.fromDomain(run.productionGraph),
        )
    }
}

@Serializable
private data class PracticeResultDto(
    val schemaVersion: Int,
    val exerciseId: String,
    val exerciseName: String,
    val bpm: Double,
    val sampleRateHz: Int,
    val runtimeExercise: RuntimeExerciseDto,
    val judgedNotes: List<JudgedNoteDto>,
    val extraHits: List<ExtraHitDto>,
    val totalExpectedNotes: Int,
    val earlyCount: Int,
    val onTimeCount: Int,
    val lateCount: Int,
    val missedCount: Int,
    val extraCount: Int,
    val accuracy: Double,
    val hitRate: Double,
    val meanAbsoluteTimingErrorMillis: Double? = null,
    val signedMeanTimingErrorMillis: Double? = null,
    val medianAbsoluteTimingErrorMillis: Double? = null,
    val timingErrorStandardDeviationMillis: Double? = null,
    val missedRate: Double,
    val extraHitRate: Double,
    val meanRelativeIntensity: Double? = null,
    val minimumRelativeIntensity: Double? = null,
    val maximumRelativeIntensity: Double? = null,
    val judgementSnapshot: JudgementSnapshotDto,
    val detectionSnapshot: DetectionSnapshotDto,
    val metronomeSnapshot: MetronomeSnapshotDto,
    val calibrationOffsetSamples: Long,
    val calibrationApplied: Boolean,
) {
    fun toDomain(): PracticeResult {
        val frozenDetection = detectionSnapshot.toDomain()
        return PracticeResult(
            schemaVersion, exerciseId, exerciseName, bpm, sampleRateHz,
            runtimeExercise.toDomain(), judgedNotes.map(JudgedNoteDto::toDomain),
            extraHits.map(ExtraHitDto::toDomain), totalExpectedNotes, earlyCount,
            onTimeCount, lateCount, missedCount, extraCount, accuracy, hitRate,
            meanAbsoluteTimingErrorMillis, signedMeanTimingErrorMillis,
            medianAbsoluteTimingErrorMillis, timingErrorStandardDeviationMillis, missedRate,
            extraHitRate, meanRelativeIntensity, minimumRelativeIntensity,
            maximumRelativeIntensity, judgementSnapshot.toDomain(), frozenDetection,
            metronomeSnapshot.toDomain(), frozenDetection.timingCalibration,
            calibrationOffsetSamples, calibrationApplied,
        )
    }

    companion object {
        fun fromDomain(value: PracticeResult) = PracticeResultDto(
            value.schemaVersion, value.exerciseId, value.exerciseName, value.bpm,
            value.sampleRateHz, RuntimeExerciseDto.fromDomain(value.runtimeExercise),
            value.judgedNotes.map(JudgedNoteDto::fromDomain),
            value.extraHits.map(ExtraHitDto::fromDomain), value.totalExpectedNotes,
            value.earlyCount, value.onTimeCount, value.lateCount, value.missedCount,
            value.extraCount, value.accuracy, value.hitRate,
            value.meanAbsoluteTimingErrorMillis, value.signedMeanTimingErrorMillis,
            value.medianAbsoluteTimingErrorMillis, value.timingErrorStandardDeviationMillis,
            value.missedRate, value.extraHitRate, value.meanRelativeIntensity,
            value.minimumRelativeIntensity, value.maximumRelativeIntensity,
            JudgementSnapshotDto.fromDomain(value.judgementSnapshot),
            DetectionSnapshotDto.fromDomain(value.detectionSnapshot),
            MetronomeSnapshotDto.fromDomain(value.metronomeSnapshot),
            value.calibrationOffsetSamples, value.calibrationApplied,
        )
    }
}

@Serializable
private data class RuntimeExerciseDto(
    val id: String,
    val name: String,
    val description: String,
    val tempoBpm: Double,
    val timeSignatureNumerator: Int,
    val timeSignatureDenominator: Int,
    val ticksPerQuarterNote: Int,
    val measures: List<RuntimeMeasureDto>,
) {
    fun toDomain() = RuntimeExercise(
        id, name, description, tempoBpm,
        TimeSignature(timeSignatureNumerator, timeSignatureDenominator),
        ticksPerQuarterNote, measures.map(RuntimeMeasureDto::toDomain),
    )

    companion object {
        fun fromDomain(value: RuntimeExercise) = RuntimeExerciseDto(
            value.id, value.name, value.description, value.tempoBpm,
            value.timeSignature.numerator, value.timeSignature.denominator,
            value.ticksPerQuarterNote, value.measures.map(RuntimeMeasureDto::fromDomain),
        )
    }
}

@Serializable
private data class RuntimeMeasureDto(
    val index: Int,
    val startTick: Long,
    val durationTicks: Long,
    val notes: List<RuntimeExpectedNoteDto>,
) {
    fun toDomain() = RuntimeMeasure(index, startTick, durationTicks, notes.map { it.toDomain() })
    companion object {
        fun fromDomain(value: RuntimeMeasure) = RuntimeMeasureDto(
            value.index, value.startTick, value.durationTicks,
            value.notes.map(RuntimeExpectedNoteDto::fromDomain),
        )
    }
}

@Serializable
private data class RuntimeExpectedNoteDto(
    val measureIndex: Int,
    val positionInMeasureTicks: Long,
    val positionTicks: Long,
    val accent: Boolean,
    val targetIntensity: Double? = null,
) {
    fun toDomain() = RuntimeExpectedNote(
        measureIndex, positionInMeasureTicks, positionTicks, accent, targetIntensity,
    )
    companion object {
        fun fromDomain(value: RuntimeExpectedNote) = RuntimeExpectedNoteDto(
            value.measureIndex, value.positionInMeasureTicks, value.positionTicks,
            value.accent, value.targetIntensity,
        )
    }
}

@Serializable
private data class JudgedNoteDto(
    val expectedNoteIndex: Int,
    val expectedExerciseSample: Long,
    val expectedExerciseTimeMillis: Double,
    val measureIndex: Int,
    val positionInMeasureTicks: Long,
    val beatPosition: Double,
    val matchedHit: DetectedHitDto? = null,
    val timingErrorSamples: Long? = null,
    val timingErrorMillis: Double? = null,
    val judgement: String,
    val relativeIntensity: Double? = null,
    val accent: Boolean,
    val targetIntensity: Double? = null,
) {
    fun toDomain(): JudgedNote {
        val hit = matchedHit?.toDomain()
        return JudgedNote(
            expectedNoteIndex, expectedExerciseSample, expectedExerciseTimeMillis, measureIndex,
            positionInMeasureTicks, beatPosition, hit, hit?.rawExerciseSample,
            hit?.rawExerciseTimeMillis, hit?.calibratedExerciseSample,
            hit?.calibratedExerciseTimeMillis, timingErrorSamples, timingErrorMillis,
            enumValue<HitJudgement>(judgement), hit?.confidence, relativeIntensity,
            accent, targetIntensity,
        )
    }
    companion object {
        fun fromDomain(value: JudgedNote) = JudgedNoteDto(
            value.expectedNoteIndex, value.expectedExerciseSample,
            value.expectedExerciseTimeMillis, value.measureIndex,
            value.positionInMeasureTicks, value.beatPosition,
            value.matchedHit?.let(DetectedHitDto::fromDomain), value.timingErrorSamples,
            value.timingErrorMillis, value.judgement.name, value.relativeIntensity,
            value.accent, value.targetIntensity,
        )
    }
}

@Serializable
private data class ExtraHitDto(
    val detectedHitIndex: Int,
    val detectedHit: DetectedHitDto,
    val relativeIntensity: Double,
) {
    fun toDomain(): ExtraHit {
        val hit = detectedHit.toDomain()
        return ExtraHit(
            detectedHitIndex, hit, hit.rawExerciseSample, hit.rawExerciseTimeMillis,
            hit.calibratedExerciseSample, hit.calibratedExerciseTimeMillis,
            hit.peakAmplitude, relativeIntensity, hit.confidence,
        )
    }
    companion object {
        fun fromDomain(value: ExtraHit) = ExtraHitDto(
            value.detectedHitIndex, DetectedHitDto.fromDomain(value.detectedHit),
            value.relativeIntensity,
        )
    }
}

@Serializable
private data class DetectedHitDto(
    val rawExerciseSample: Long,
    val rawExerciseTimeMillis: Double,
    val calibratedExerciseSample: Long,
    val calibratedExerciseTimeMillis: Double,
    val onsetFrame: Int,
    val peakFrame: Int,
    val peakExerciseSample: Long,
    val peakTimeMillis: Double,
    val peakAmplitude: Float,
    val frameLevel: Float,
    val envelope: Float,
    val noiseFloor: Float,
    val signalToNoiseRatio: Double,
    val confidence: Double,
    val metronomeBandRatio: Double,
    val broadbandResidualEnergy: Double,
    val spectralBandwidthHz: Double,
    val spectralCentroidHz: Double,
    val calibrationApplied: Boolean,
) {
    fun toDomain() = DetectedHit(
        rawExerciseSample, rawExerciseTimeMillis, calibratedExerciseSample,
        calibratedExerciseTimeMillis, onsetFrame, peakFrame, peakExerciseSample,
        peakTimeMillis, peakAmplitude, frameLevel, envelope, noiseFloor,
        signalToNoiseRatio, confidence, metronomeBandRatio, broadbandResidualEnergy,
        spectralBandwidthHz, spectralCentroidHz, calibrationApplied,
    )
    companion object {
        fun fromDomain(value: DetectedHit) = DetectedHitDto(
            value.rawExerciseSample, value.rawExerciseTimeMillis,
            value.calibratedExerciseSample, value.calibratedExerciseTimeMillis,
            value.onsetFrame, value.peakFrame, value.peakExerciseSample, value.peakTimeMillis,
            value.peakAmplitude, value.frameLevel, value.envelope, value.noiseFloor,
            value.signalToNoiseRatio, value.confidence, value.metronomeBandRatio,
            value.broadbandResidualEnergy, value.spectralBandwidthHz,
            value.spectralCentroidHz, value.calibrationApplied,
        )
    }
}

@Serializable
private data class JudgementSnapshotDto(val configuration: JudgementConfigurationDto) {
    fun toDomain() = SessionJudgementSnapshot(configuration.toDomain())
    companion object {
        fun fromDomain(value: SessionJudgementSnapshot) =
            JudgementSnapshotDto(JudgementConfigurationDto.fromDomain(value.configuration))
    }
}

@Serializable
private data class JudgementConfigurationDto(
    val onTimeBeforeMillis: Double,
    val onTimeAfterMillis: Double,
    val maximumEarlyMillis: Double,
    val maximumLateMillis: Double,
    val minimumDetectedHitConfidence: Double,
    val minimumHitRateForVerdict: Double,
    val minimumExtraHitRateForCreativeVerdict: Double,
    val extraHitHandlingEnabled: Boolean,
    val version: Int,
) {
    fun toDomain() = JudgementConfiguration(
        onTimeBeforeMillis, onTimeAfterMillis, maximumEarlyMillis, maximumLateMillis,
        minimumDetectedHitConfidence, minimumHitRateForVerdict,
        minimumExtraHitRateForCreativeVerdict, extraHitHandlingEnabled, version,
    )
    companion object {
        fun fromDomain(value: JudgementConfiguration) = JudgementConfigurationDto(
            value.onTimeBeforeMillis, value.onTimeAfterMillis, value.maximumEarlyMillis,
            value.maximumLateMillis, value.minimumDetectedHitConfidence,
            value.minimumHitRateForVerdict, value.minimumExtraHitRateForCreativeVerdict,
            value.extraHitHandlingEnabled, value.version,
        )
    }
}

@Serializable
private data class DetectionSnapshotDto(
    val configuration: HitDetectionConfigurationDto,
    val timingCalibration: TimingCalibrationDto? = null,
) {
    fun toDomain() = SessionDetectionSnapshot(
        configuration.toDomain(), timingCalibration?.toDomain(),
    )
    companion object {
        fun fromDomain(value: SessionDetectionSnapshot) = DetectionSnapshotDto(
            HitDetectionConfigurationDto.fromDomain(value.configuration),
            value.timingCalibration?.let(TimingCalibrationDto::fromDomain),
        )
    }
}

@Serializable
private data class HitDetectionConfigurationDto(
    val enabled: Boolean,
    val minimumAbsoluteThreshold: Double,
    val noiseFloorMultiplier: Double,
    val minimumSignalToNoiseRatio: Double,
    val minimumAttackRise: Double,
    val onsetLookBackMillis: Double,
    val peakSearchMillis: Double,
    val releaseHysteresisRatio: Double,
    val minimumHitSpacingMillis: Double,
    val minimumConfidence: Double,
    val applyTimingCalibration: Boolean,
    val metronomeRejection: MetronomeRejectionConfigurationDto,
    val version: Int,
) {
    fun toDomain() = HitDetectionConfiguration(
        enabled, minimumAbsoluteThreshold, noiseFloorMultiplier, minimumSignalToNoiseRatio,
        minimumAttackRise, onsetLookBackMillis, peakSearchMillis, releaseHysteresisRatio,
        minimumHitSpacingMillis, minimumConfidence, applyTimingCalibration,
        metronomeRejection.toDomain(), version,
    )
    companion object {
        fun fromDomain(value: HitDetectionConfiguration) = HitDetectionConfigurationDto(
            value.enabled, value.minimumAbsoluteThreshold, value.noiseFloorMultiplier,
            value.minimumSignalToNoiseRatio, value.minimumAttackRise,
            value.onsetLookBackMillis, value.peakSearchMillis, value.releaseHysteresisRatio,
            value.minimumHitSpacingMillis, value.minimumConfidence,
            value.applyTimingCalibration,
            MetronomeRejectionConfigurationDto.fromDomain(value.metronomeRejection),
            value.version,
        )
    }
}

@Serializable
private data class MetronomeRejectionConfigurationDto(
    val enabled: Boolean,
    val fftSize: Int,
    val analysisWindowMillis: Double,
    val metronomeBandWidthHz: Double,
    val minimumMetronomeBandEnergyRatio: Double,
    val minimumBroadbandResidualEnergy: Double,
    val spectralConfidenceThreshold: Double,
    val maximumScheduledDistanceMillis: Double,
    val uncertainCandidateBehaviour: String,
) {
    fun toDomain() = MetronomeRejectionConfiguration(
        enabled, fftSize, analysisWindowMillis, metronomeBandWidthHz,
        minimumMetronomeBandEnergyRatio, minimumBroadbandResidualEnergy,
        spectralConfidenceThreshold, maximumScheduledDistanceMillis,
        enumValue<UncertainCandidateBehaviour>(uncertainCandidateBehaviour),
    )
    companion object {
        fun fromDomain(value: MetronomeRejectionConfiguration) =
            MetronomeRejectionConfigurationDto(
                value.enabled, value.fftSize, value.analysisWindowMillis,
                value.metronomeBandWidthHz, value.minimumMetronomeBandEnergyRatio,
                value.minimumBroadbandResidualEnergy, value.spectralConfidenceThreshold,
                value.maximumScheduledDistanceMillis, value.uncertainCandidateBehaviour.name,
            )
    }
}

@Serializable
private data class MetronomeSnapshotDto(
    val configuration: MetronomeConfigurationDto,
    val downbeatsOnly: Boolean,
) {
    fun toDomain() = SessionMetronomeSnapshot(configuration.toDomain(), downbeatsOnly)
    companion object {
        fun fromDomain(value: SessionMetronomeSnapshot) = MetronomeSnapshotDto(
            MetronomeConfigurationDto.fromDomain(value.configuration), value.downbeatsOnly,
        )
    }
}

@Serializable
private data class MetronomeConfigurationDto(
    val frequencyHz: Int,
    val durationMillis: Int,
    val normalVolumePercent: Int,
    val accentVolumePercent: Int,
    val window: String,
    val notchEnabled: Boolean,
    val notchCenterFrequencyHz: Int,
    val notchQFactor: Double,
    val notchCenterLinkedToTone: Boolean,
    val version: Int,
) {
    fun toDomain() = MetronomeConfiguration(
        MetronomeToneConfiguration(
            frequencyHz, durationMillis, normalVolumePercent, accentVolumePercent,
            enumValue<MetronomeWindow>(window),
        ),
        MetronomeNotchConfiguration(
            notchEnabled, notchCenterFrequencyHz, notchQFactor, notchCenterLinkedToTone,
        ),
        version,
    )
    companion object {
        fun fromDomain(value: MetronomeConfiguration) = MetronomeConfigurationDto(
            value.tone.frequencyHz, value.tone.durationMillis,
            value.tone.normalVolumePercent, value.tone.accentVolumePercent,
            value.tone.window.name, value.notch.enabled, value.notch.centerFrequencyHz,
            value.notch.qFactor, value.notch.centerLinkedToTone, value.version,
        )
    }
}

@Serializable
private data class TimingCalibrationDto(
    val offsetSamples: Long,
    val sampleRateHz: Int,
    val confidence: String,
    val expectedClickCount: Int,
    val matchedClickCount: Int,
    val offsetSpreadSamples: Long,
    val calibratedAtEpochMillis: Long,
    val algorithmVersion: Int,
) {
    fun toDomain() = TimingCalibration(
        offsetSamples, sampleRateHz, enumValue<CalibrationConfidence>(confidence),
        expectedClickCount, matchedClickCount, offsetSpreadSamples,
        calibratedAtEpochMillis, algorithmVersion,
    )
    companion object {
        fun fromDomain(value: TimingCalibration) = TimingCalibrationDto(
            value.offsetSamples, value.sampleRateHz, value.confidence.name,
            value.expectedClickCount, value.matchedClickCount, value.offsetSpreadSamples,
            value.calibratedAtEpochMillis, value.algorithmVersion,
        )
    }
}

@Serializable
private data class ProductionGraphDto(
    val exerciseId: String,
    val sampleRateHz: Int,
    val exerciseDurationSamples: Long,
    val exerciseDurationMillis: Double,
    val maximumEnvelopeAmplitude: Float,
    val envelopePoints: List<ProductionEnvelopePointDto>,
    val expectedNotes: List<ProductionExpectedNoteMarkerDto>,
    val matchedHits: List<ProductionMatchedHitMarkerDto>,
    val timingConnectors: List<ProductionTimingConnectorDto>,
    val missedNotes: List<ProductionMissedNoteMarkerDto>,
    val extraHits: List<ProductionExtraHitMarkerDto>,
    val measureGuides: List<ProductionMeasureGuideDto>,
) {
    fun toDomain(): ProductionGraphModel = try {
        ProductionGraphModel(
            exerciseId, sampleRateHz, exerciseDurationSamples, exerciseDurationMillis,
            maximumEnvelopeAmplitude, envelopePoints.map { it.toDomain() },
            expectedNotes.map { it.toDomain() }, matchedHits.map { it.toDomain() },
            timingConnectors.map { it.toDomain() }, missedNotes.map { it.toDomain() },
            extraHits.map { it.toDomain() }, measureGuides.map { it.toDomain() },
        )
    } catch (exception: IllegalArgumentException) {
        throw InvalidProductionGraphPayloadException(exception)
    }
    companion object {
        fun fromDomain(value: ProductionGraphModel) = ProductionGraphDto(
            value.exerciseId, value.sampleRateHz, value.exerciseDurationSamples,
            value.exerciseDurationMillis, value.maximumEnvelopeAmplitude,
            value.envelopePoints.map(ProductionEnvelopePointDto::fromDomain),
            value.expectedNotes.map(ProductionExpectedNoteMarkerDto::fromDomain),
            value.matchedHits.map(ProductionMatchedHitMarkerDto::fromDomain),
            value.timingConnectors.map(ProductionTimingConnectorDto::fromDomain),
            value.missedNotes.map(ProductionMissedNoteMarkerDto::fromDomain),
            value.extraHits.map(ProductionExtraHitMarkerDto::fromDomain),
            value.measureGuides.map(ProductionMeasureGuideDto::fromDomain),
        )
    }
}

@Serializable
private data class ProductionEnvelopePointDto(val sample: Long, val millis: Double, val value: Float) {
    fun toDomain() = ProductionEnvelopePoint(sample, millis, value)
    companion object { fun fromDomain(v: ProductionEnvelopePoint) =
        ProductionEnvelopePointDto(v.exerciseSample, v.exerciseTimeMillis, v.amplitude) }
}

@Serializable
private data class ProductionExpectedNoteMarkerDto(
    val index: Int, val sample: Long, val millis: Double, val measure: Int,
    val beat: Double, val judgement: String,
) {
    fun toDomain() = ProductionExpectedNoteMarker(
        index, sample, millis, measure, beat, enumValue<HitJudgement>(judgement),
    )
    companion object { fun fromDomain(v: ProductionExpectedNoteMarker) =
        ProductionExpectedNoteMarkerDto(v.expectedNoteIndex, v.exerciseSample,
            v.exerciseTimeMillis, v.measureIndex, v.beatPosition, v.judgement.name) }
}

@Serializable
private data class ProductionMatchedHitMarkerDto(
    val index: Int, val sample: Long, val millis: Double, val judgement: String,
    val intensity: Double, val confidence: Double,
) {
    fun toDomain() = ProductionMatchedHitMarker(
        index, sample, millis, enumValue<HitJudgement>(judgement), intensity, confidence,
    )
    companion object { fun fromDomain(v: ProductionMatchedHitMarker) =
        ProductionMatchedHitMarkerDto(v.expectedNoteIndex, v.calibratedExerciseSample,
            v.calibratedExerciseTimeMillis, v.judgement.name, v.relativeIntensity,
            v.confidence) }
}

@Serializable
private data class ProductionTimingConnectorDto(
    val index: Int, val expectedSample: Long, val actualSample: Long,
    val errorSamples: Long, val errorMillis: Double,
) {
    fun toDomain() = ProductionTimingConnector(
        index, expectedSample, actualSample, errorSamples, errorMillis,
    )
    companion object { fun fromDomain(v: ProductionTimingConnector) =
        ProductionTimingConnectorDto(v.expectedNoteIndex, v.expectedExerciseSample,
            v.calibratedActualSample, v.timingErrorSamples, v.timingErrorMillis) }
}

@Serializable
private data class ProductionMissedNoteMarkerDto(
    val index: Int, val sample: Long, val millis: Double, val measure: Int, val beat: Double,
) {
    fun toDomain() = ProductionMissedNoteMarker(index, sample, millis, measure, beat)
    companion object { fun fromDomain(v: ProductionMissedNoteMarker) =
        ProductionMissedNoteMarkerDto(v.expectedNoteIndex, v.exerciseSample,
            v.exerciseTimeMillis, v.measureIndex, v.beatPosition) }
}

@Serializable
private data class ProductionExtraHitMarkerDto(
    val index: Int, val sample: Long, val millis: Double,
    val intensity: Double, val confidence: Double,
) {
    fun toDomain() = ProductionExtraHitMarker(index, sample, millis, intensity, confidence)
    companion object { fun fromDomain(v: ProductionExtraHitMarker) =
        ProductionExtraHitMarkerDto(v.detectedHitIndex, v.calibratedExerciseSample,
            v.calibratedExerciseTimeMillis, v.relativeIntensity, v.confidence) }
}

@Serializable
private data class ProductionMeasureGuideDto(val index: Int, val sample: Long) {
    fun toDomain() = ProductionMeasureGuide(index, sample)
    companion object { fun fromDomain(v: ProductionMeasureGuide) =
        ProductionMeasureGuideDto(v.measureIndex, v.exerciseSample) }
}

private inline fun <reified T : Enum<T>> enumValue(value: String): T = try {
    enumValueOf<T>(value)
} catch (exception: IllegalArgumentException) {
    throw CorruptedRunPayloadException(exception)
}
