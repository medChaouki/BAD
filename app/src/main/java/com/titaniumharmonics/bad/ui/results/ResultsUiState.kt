package com.titaniumharmonics.bad.ui.results

import com.titaniumharmonics.bad.audio.result.PracticeResult

data class ResultsUiState(
    val result: PracticeResult,
    val showDetails: Boolean = false,
)

