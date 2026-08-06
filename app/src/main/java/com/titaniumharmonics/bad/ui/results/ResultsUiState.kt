package com.titaniumharmonics.bad.ui.results

import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel

data class ResultsUiState(
    val result: PracticeResult,
    val graphModel: ProductionGraphModel,
    val showDetails: Boolean = false,
)
