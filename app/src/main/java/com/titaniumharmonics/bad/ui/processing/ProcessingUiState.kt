package com.titaniumharmonics.bad.ui.processing

import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisState
import com.titaniumharmonics.bad.audio.detection.HitDetectionState
import com.titaniumharmonics.bad.audio.result.PracticeResultState
import com.titaniumharmonics.bad.audio.result.PracticeVerdict
import com.titaniumharmonics.bad.ui.practice.PracticePhase
import com.titaniumharmonics.bad.ui.practice.PracticeUiState

enum class ProcessingStage {
    WEIGHING,
    MEASURING,
    VERDICT,
    FAILED,
}

data class ProcessingPresentation(
    val stage: ProcessingStage,
    val verdict: PracticeVerdict? = null,
    val failureMessage: String? = null,
)

fun PracticeUiState.processingPresentation(showVerdict: Boolean): ProcessingPresentation {
    val failure = when (val analysis = audioAnalysis) {
        is AudioAnalysisState.Failed -> analysis.message
        else -> when (val detection = hitDetection) {
            is HitDetectionState.Failed -> detection.message
            else -> (practiceResult as? PracticeResultState.Failed)?.message
        }
    } ?: errorMessage?.takeIf { phase == PracticePhase.ERROR }
    if (failure != null) {
        return ProcessingPresentation(ProcessingStage.FAILED, failureMessage = failure)
    }

    if (showVerdict && practiceResult is PracticeResultState.Ready && practiceVerdict != null) {
        return ProcessingPresentation(ProcessingStage.VERDICT, verdict = practiceVerdict)
    }

    return ProcessingPresentation(
        if (audioAnalysis is AudioAnalysisState.Ready) {
            ProcessingStage.MEASURING
        } else {
            ProcessingStage.WEIGHING
        },
    )
}
