package com.titaniumharmonics.bad.ui.processing

import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisState
import com.titaniumharmonics.bad.audio.result.PracticeResultState
import com.titaniumharmonics.bad.audio.result.PracticeVerdict
import com.titaniumharmonics.bad.audio.result.graphFixture
import com.titaniumharmonics.bad.ui.practice.PracticeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessingUiStateTest {
    @Test
    fun followsPreprocessingMeasuringVerdictAndFailureStates() {
        val fixture = graphFixture()
        val stageOne = PracticeUiState(audioAnalysis = AudioAnalysisState.Processing)
        assertEquals(ProcessingStage.WEIGHING, stageOne.processingPresentation(false).stage)

        val stageTwo = stageOne.copy(audioAnalysis = AudioAnalysisState.Ready(fixture.analysis))
        assertEquals(ProcessingStage.MEASURING, stageTwo.processingPresentation(false).stage)

        val complete = stageTwo.copy(
            practiceResult = PracticeResultState.Ready(fixture.result, fixture.result.let {
                com.titaniumharmonics.bad.audio.result.ProductionGraphModelBuilder
                    .build(it, fixture.analysis)
                    .let { built ->
                        (built as com.titaniumharmonics.bad.audio.result.ProductionGraphBuildResult.Success).model
                    }
            }),
            practiceVerdict = PracticeVerdict.LATE,
        )
        assertEquals(ProcessingStage.MEASURING, complete.processingPresentation(false).stage)
        assertEquals(PracticeVerdict.LATE, complete.processingPresentation(true).verdict)

        val failed = stageOne.copy(audioAnalysis = AudioAnalysisState.Failed("bad wav"))
        assertEquals(ProcessingStage.FAILED, failed.processingPresentation(false).stage)
        assertEquals("bad wav", failed.processingPresentation(false).failureMessage)
        assertNull(failed.processingPresentation(false).verdict)
    }
}
