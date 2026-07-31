package com.titaniumharmonics.bad.exercise

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCompilerTest {
    @Test
    fun compile_multiplierOneProducesOneRuntimeMeasure() {
        val runtime = validExercise(
            measureMultipliers = listOf(1),
        ).compileSuccessfully()

        assertEquals(1, runtime.measureCount)
        assertEquals(listOf(0), runtime.measures.map(RuntimeMeasure::index))
    }

    @Test
    fun compile_multiplierFourProducesFourSequentialRuntimeMeasures() {
        val runtime = validExercise(
            measureMultipliers = listOf(4),
        ).compileSuccessfully()

        assertEquals(4, runtime.measureCount)
        assertEquals(listOf(0, 1, 2, 3), runtime.measures.map(RuntimeMeasure::index))
        assertEquals(
            listOf(0L, 1_920L, 3_840L, 5_760L),
            runtime.measures.map(RuntimeMeasure::startTick),
        )
        assertEquals(7_680L, runtime.totalTicks)
    }

    @Test
    fun compile_mixedMultipliersExpandPatternsWithCorrectNoteOffsets() {
        val editable = validExercise(
            measureCount = 2,
            measureSubdivisions = listOf(
                MeasureSubdivision.QUARTER,
                MeasureSubdivision.EIGHTH,
            ),
            measureMultipliers = listOf(3, 2),
            notes = listOf(
                ExpectedNote(positionTicks = 0L, accent = true),
                ExpectedNote(positionTicks = 480L),
                ExpectedNote(positionTicks = 1_920L, targetIntensity = 0.7),
                ExpectedNote(positionTicks = 2_160L),
            ),
        )

        val runtime = editable.compileSuccessfully()

        assertEquals(2, editable.measureCount)
        assertEquals(listOf(3, 2), editable.measureMultipliers)
        assertEquals(5, runtime.measureCount)
        assertEquals(9_600L, runtime.totalTicks)
        assertEquals(listOf(0, 1, 2, 3, 4), runtime.measures.map { it.index })
        assertEquals(
            listOf(
                0L,
                480L,
                1_920L,
                2_400L,
                3_840L,
                4_320L,
                5_760L,
                6_000L,
                7_680L,
                7_920L,
            ),
            runtime.notes.map(RuntimeExpectedNote::positionTicks),
        )
        assertEquals(
            listOf(0L, 480L, 0L, 480L, 0L, 480L, 0L, 240L, 0L, 240L),
            runtime.notes.map(RuntimeExpectedNote::positionInMeasureTicks),
        )
        assertEquals(
            runtime.notes.map(RuntimeExpectedNote::positionTicks).distinct(),
            runtime.notes.map(RuntimeExpectedNote::positionTicks),
        )
    }

    @Test
    fun compile_preservesPlaybackMetadataAndBpm() {
        val runtime = validExercise(
            id = "metadata-id",
            name = "Metadata name",
            description = "Metadata description",
            tempoBpm = 137.5,
        ).compileSuccessfully()

        assertEquals("metadata-id", runtime.id)
        assertEquals("Metadata name", runtime.name)
        assertEquals("Metadata description", runtime.description)
        assertEquals(137.5, runtime.tempoBpm, 0.0)
        assertEquals(TimeSignature(4, 4), runtime.timeSignature)
        assertEquals(1, runtime.countInMeasures)
        assertEquals(480, runtime.ticksPerQuarterNote)
    }

    @Test
    fun compile_createsSequentialRuntimeMeasures() {
        val runtime = validExercise(
            measureCount = 3,
            measureSubdivisions = List(3) { MeasureSubdivision.QUARTER },
            notes = emptyList(),
        ).compileSuccessfully()

        assertEquals(listOf(0, 1, 2), runtime.measures.map { it.index })
        assertEquals(listOf(0L, 1_920L, 3_840L), runtime.measures.map { it.startTick })
        assertEquals(listOf(1_920L, 1_920L, 1_920L), runtime.measures.map { it.durationTicks })
        assertEquals(5_760L, runtime.totalTicks)
    }

    @Test
    fun compile_preservesQuarterNoteTiming() {
        assertCompiledPositions(
            subdivision = MeasureSubdivision.QUARTER,
            positions = listOf(0L, 480L, 960L, 1_440L),
        )
    }

    @Test
    fun compile_repeatsQuarterNotesAtEveryRuntimeMeasureOffset() {
        assertRepeatedPositions(
            subdivision = MeasureSubdivision.QUARTER,
            localPositions = listOf(0L, 480L, 960L, 1_440L),
        )
    }

    @Test
    fun compile_preservesEighthNoteTiming() {
        assertCompiledPositions(
            subdivision = MeasureSubdivision.EIGHTH,
            positions = (0 until 8).map { it * 240L },
        )
    }

    @Test
    fun compile_repeatsEighthNotesAtEveryRuntimeMeasureOffset() {
        assertRepeatedPositions(
            subdivision = MeasureSubdivision.EIGHTH,
            localPositions = (0 until 8).map { it * 240L },
        )
    }

    @Test
    fun compile_preservesExactEighthTripletTiming() {
        assertCompiledPositions(
            subdivision = MeasureSubdivision.EIGHTH_TRIPLET,
            positions = (0 until 12).map { it * 160L },
        )
    }

    @Test
    fun compile_repeatsExactEighthTripletsWithoutRounding() {
        assertRepeatedPositions(
            subdivision = MeasureSubdivision.EIGHTH_TRIPLET,
            localPositions = (0 until 12).map { it * 160L },
        )
    }

    @Test
    fun compile_preservesSixteenthNoteTiming() {
        assertCompiledPositions(
            subdivision = MeasureSubdivision.SIXTEENTH,
            positions = (0 until 16).map { it * 120L },
        )
    }

    @Test
    fun compile_repeatsSixteenthNotesAtEveryRuntimeMeasureOffset() {
        assertRepeatedPositions(
            subdivision = MeasureSubdivision.SIXTEENTH,
            localPositions = (0 until 16).map { it * 120L },
        )
    }

    @Test
    fun compile_mapsAbsoluteNotesToMeasureLocalOffsets() {
        val runtime = validExercise(
            measureCount = 3,
            measureSubdivisions = List(3) { MeasureSubdivision.EIGHTH_TRIPLET },
            notes = listOf(
                ExpectedNote(positionTicks = 160L),
                ExpectedNote(positionTicks = 2_080L),
                ExpectedNote(positionTicks = 4_160L),
            ),
        ).compileSuccessfully()

        assertEquals(listOf(0, 1, 2), runtime.notes.map { it.measureIndex })
        assertEquals(listOf(160L, 160L, 320L), runtime.notes.map {
            it.positionInMeasureTicks
        })
        assertEquals(listOf(160L, 2_080L, 4_160L), runtime.notes.map {
            it.positionTicks
        })
    }

    @Test
    fun compile_preservesNoteOrderAndProperties() {
        val runtime = validExercise(
            notes = listOf(
                ExpectedNote(positionTicks = 120L, targetIntensity = 0.25),
                ExpectedNote(positionTicks = 480L, accent = true),
                ExpectedNote(positionTicks = 1_440L, targetIntensity = 0.75),
            ),
        ).compileSuccessfully()

        assertEquals(listOf(120L, 480L, 1_440L), runtime.notes.map {
            it.positionTicks
        })
        assertEquals(listOf(false, true, false), runtime.notes.map { it.accent })
        assertEquals(listOf(0.25, null, 0.75), runtime.notes.map {
            it.targetIntensity
        })
    }

    @Test
    fun compile_allowsAValidSilentMeasure() {
        val runtime = validExercise(notes = emptyList()).compileSuccessfully()

        assertEquals(1, runtime.measureCount)
        assertTrue(runtime.notes.isEmpty())
        assertTrue(runtime.measures.single().notes.isEmpty())
    }

    @Test
    fun compile_repeatsSilentPatternsWithoutAddingNotes() {
        val runtime = validExercise(
            notes = emptyList(),
            measureMultipliers = listOf(4),
        ).compileSuccessfully()

        assertEquals(4, runtime.measureCount)
        assertTrue(runtime.notes.isEmpty())
        assertTrue(runtime.measures.all { it.notes.isEmpty() })
    }

    @Test
    fun compile_preservesMixedSubdivisionPatternTiming() {
        val runtime = validExercise(
            measureCount = 3,
            measureSubdivisions = listOf(
                MeasureSubdivision.QUARTER,
                MeasureSubdivision.EIGHTH_TRIPLET,
                MeasureSubdivision.SIXTEENTH,
            ),
            measureMultipliers = listOf(2, 2, 1),
            notes = listOf(
                ExpectedNote(positionTicks = 480L),
                ExpectedNote(positionTicks = 1_920L + 160L),
                ExpectedNote(positionTicks = 3_840L + 120L),
            ),
        ).compileSuccessfully()

        assertEquals(5, runtime.measureCount)
        assertEquals(
            listOf(480L, 2_400L, 4_000L, 5_920L, 7_800L),
            runtime.notes.map(RuntimeExpectedNote::positionTicks),
        )
        assertEquals(
            listOf(480L, 480L, 160L, 160L, 120L),
            runtime.notes.map(RuntimeExpectedNote::positionInMeasureTicks),
        )
    }

    @Test
    fun compile_returnsFailureForAnInvalidEmptyExercise() {
        val result = ExerciseCompiler.compile(
            validExercise(
                name = "",
                measureCount = 0,
                measureSubdivisions = emptyList(),
                notes = emptyList(),
            ),
        )

        assertTrue(result is ExerciseCompilationResult.Failure)
        val errors = (result as ExerciseCompilationResult.Failure).validationErrors
        assertTrue(errors.any { it.contains("name") })
        assertTrue(errors.any { it.contains("measureCount") })
    }

    @Test
    fun compile_returnsFailureForInvalidMultiplier() {
        val result = ExerciseCompiler.compile(
            validExercise(measureMultipliers = listOf(0)),
        )

        assertTrue(result is ExerciseCompilationResult.Failure)
        assertTrue(
            (result as ExerciseCompilationResult.Failure).validationErrors.any {
                it.contains("measureMultipliers[0]")
            },
        )
    }

    @Test
    fun compile_returnsFailureWhenExpandedTicksOverflow() {
        val result = ExerciseCompiler.compile(
            EditableExercise(
                formatVersion = ExerciseFormat.CURRENT_VERSION,
                id = "overflow",
                name = "Overflow",
                description = "",
                tempoBpm = 100.0,
                timeSignature = TimeSignature(
                    numerator = 50_000_000,
                    denominator = 4,
                ),
                countInMeasures = 0,
                measureCount = 1,
                ticksPerQuarterNote = Int.MAX_VALUE,
                notes = emptyList(),
                measureMultipliers = listOf(99),
            ),
        )

        assertTrue(result is ExerciseCompilationResult.Failure)
        assertTrue(
            (result as ExerciseCompilationResult.Failure).validationErrors.any {
                it.contains("expanded", ignoreCase = true)
            },
        )
    }

    @Test
    fun compile_isDeterministicAndDefensivelyCopiesCollections() {
        val editableNotes = mutableListOf(
            ExpectedNote(positionTicks = 0L),
            ExpectedNote(positionTicks = 480L),
        )
        val editable = validExercise(notes = editableNotes)

        val first = editable.compileSuccessfully()
        val second = editable.compileSuccessfully()
        editableNotes.clear()

        assertEquals(first, second)
        assertEquals(listOf(0L, 480L), first.notes.map { it.positionTicks })
        assertEquals(2, first.measures.single().notes.size)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (first.measures as MutableList<RuntimeMeasure>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (first.notes as MutableList<RuntimeExpectedNote>).clear()
        }
    }

    @Test
    fun compile_supportsTheBundledExampleExercise() {
        val assetFile = listOf(
            File("src/main/assets/exercises/basic-quarter-notes.json"),
            File("app/src/main/assets/exercises/basic-quarter-notes.json"),
        ).first(File::isFile)
        val editable = ExerciseJsonCodec.decode(assetFile.readText())
        val runtime = editable.compileSuccessfully()

        assertEquals("basic-quarter-notes", runtime.id)
        assertEquals(4, runtime.measureCount)
        assertEquals(16, runtime.notes.size)
        assertEquals(7_680L, runtime.totalTicks)
        assertEquals(7_200L, runtime.notes.last().positionTicks)
    }

    private fun assertCompiledPositions(
        subdivision: MeasureSubdivision,
        positions: List<Long>,
    ) {
        val runtime = validExercise(
            measureSubdivisions = listOf(subdivision),
            notes = positions.map(::ExpectedNote),
        ).compileSuccessfully()

        assertEquals(positions, runtime.notes.map { it.positionTicks })
        assertEquals(positions, runtime.notes.map { it.positionInMeasureTicks })
    }

    private fun assertRepeatedPositions(
        subdivision: MeasureSubdivision,
        localPositions: List<Long>,
    ) {
        val runtime = validExercise(
            measureSubdivisions = listOf(subdivision),
            measureMultipliers = listOf(2),
            notes = localPositions.map(::ExpectedNote),
        ).compileSuccessfully()

        val expectedPositions = localPositions + localPositions.map { it + 1_920L }
        assertEquals(2, runtime.measureCount)
        assertEquals(
            expectedPositions,
            runtime.notes.map(RuntimeExpectedNote::positionTicks),
        )
        assertEquals(
            localPositions + localPositions,
            runtime.notes.map(RuntimeExpectedNote::positionInMeasureTicks),
        )
    }

    private fun EditableExercise.compileSuccessfully(): RuntimeExercise =
        when (val result = ExerciseCompiler.compile(this)) {
            is ExerciseCompilationResult.Success -> result.exercise
            is ExerciseCompilationResult.Failure -> error(
                result.validationErrors.joinToString("\n"),
            )
        }

    private fun validExercise(
        id: String = "compiler-test",
        name: String = "Compiler test",
        description: String = "",
        tempoBpm: Double = 100.0,
        measureCount: Int = 1,
        measureSubdivisions: List<MeasureSubdivision> =
            List(measureCount) { MeasureSubdivision.QUARTER },
        measureMultipliers: List<Int> = List(measureCount) { 1 },
        notes: List<ExpectedNote> = listOf(ExpectedNote(positionTicks = 0L)),
    ): EditableExercise = EditableExercise(
        formatVersion = ExerciseFormat.CURRENT_VERSION,
        id = id,
        name = name,
        description = description,
        tempoBpm = tempoBpm,
        timeSignature = TimeSignature(numerator = 4, denominator = 4),
        countInMeasures = 1,
        measureCount = measureCount,
        ticksPerQuarterNote = 480,
        notes = notes,
        measureSubdivisions = measureSubdivisions,
        measureMultipliers = measureMultipliers,
    )
}
