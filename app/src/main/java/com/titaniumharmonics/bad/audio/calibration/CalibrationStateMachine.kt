package com.titaniumharmonics.bad.audio.calibration

enum class CalibrationPhase {
    IDLE,
    CHECKING_ROUTE,
    AWAITING_ROUTE_CONFIRMATION,
    PREPARING,
    RECORDING,
    PLAYING_CLICKS,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REJECTED,
}

data class TimingCalibrationUiState(
    val phase: CalibrationPhase = CalibrationPhase.IDLE,
    val activeCalibration: TimingCalibration? = null,
    val pendingCalibration: TimingCalibration? = null,
    val routeDecision: CalibrationRouteDecision? = null,
    val uncertainRouteConfirmed: Boolean = false,
    val failureReason: CalibrationFailureReason? = null,
    val diagnostics: CalibrationDiagnostics? = null,
) {
    val isActive: Boolean
        get() = phase in setOf(
            CalibrationPhase.CHECKING_ROUTE,
            CalibrationPhase.PREPARING,
            CalibrationPhase.RECORDING,
            CalibrationPhase.PLAYING_CLICKS,
            CalibrationPhase.PROCESSING,
        )

    val canStart: Boolean
        get() = !isActive && pendingCalibration == null && when (routeDecision?.status) {
            CalibrationRouteStatus.ALLOWED -> true
            CalibrationRouteStatus.UNCERTAIN -> uncertainRouteConfirmed
            else -> false
        }

    val isAwaitingDecision: Boolean
        get() = phase == CalibrationPhase.SUCCESS && pendingCalibration != null

    val isFinished: Boolean
        get() = !isAwaitingDecision && phase in setOf(
            CalibrationPhase.SUCCESS,
            CalibrationPhase.FAILED,
            CalibrationPhase.CANCELLED,
            CalibrationPhase.REJECTED,
        )
}

class TimingCalibrationStateMachine(initialCalibration: TimingCalibration?) {
    var state: TimingCalibrationUiState = TimingCalibrationUiState(
        activeCalibration = initialCalibration,
    )
        private set

    fun checkingRoute() = transition(CalibrationPhase.CHECKING_ROUTE)

    fun routeChecked(decision: CalibrationRouteDecision) {
        require(state.phase == CalibrationPhase.CHECKING_ROUTE || !state.isActive)
        state = state.copy(
            phase = if (decision.status == CalibrationRouteStatus.UNCERTAIN) {
                CalibrationPhase.AWAITING_ROUTE_CONFIRMATION
            } else {
                CalibrationPhase.IDLE
            },
            routeDecision = decision,
            uncertainRouteConfirmed = false,
            failureReason = null,
        )
    }

    fun confirmUncertainRoute(confirmed: Boolean) {
        require(state.routeDecision?.status == CalibrationRouteStatus.UNCERTAIN)
        state = state.copy(uncertainRouteConfirmed = confirmed)
    }

    fun preparing() {
        check(state.canStart)
        transition(CalibrationPhase.PREPARING)
    }
    fun recording() = transitionFrom(CalibrationPhase.PREPARING, CalibrationPhase.RECORDING)
    fun playingClicks() = transitionFrom(CalibrationPhase.RECORDING, CalibrationPhase.PLAYING_CLICKS)
    fun processing() = transitionFrom(CalibrationPhase.PLAYING_CLICKS, CalibrationPhase.PROCESSING)

    fun success(calibration: TimingCalibration, diagnostics: CalibrationDiagnostics?) {
        require(state.phase == CalibrationPhase.PROCESSING)
        state = state.copy(
            phase = CalibrationPhase.SUCCESS,
            pendingCalibration = calibration,
            failureReason = null,
            diagnostics = diagnostics,
        )
    }

    fun acceptPendingCalibration(): TimingCalibration {
        require(state.phase == CalibrationPhase.SUCCESS)
        val accepted = requireNotNull(state.pendingCalibration)
        state = state.copy(
            activeCalibration = accepted,
            pendingCalibration = null,
        )
        return accepted
    }

    fun rejectPendingCalibration() {
        require(state.phase == CalibrationPhase.SUCCESS)
        requireNotNull(state.pendingCalibration)
        state = state.copy(
            phase = CalibrationPhase.REJECTED,
            pendingCalibration = null,
        )
    }

    fun fail(reason: CalibrationFailureReason, diagnostics: CalibrationDiagnostics? = null) {
        state = state.copy(
            phase = CalibrationPhase.FAILED,
            pendingCalibration = null,
            failureReason = reason,
            diagnostics = diagnostics,
        )
    }

    fun cancel() {
        state = state.copy(phase = CalibrationPhase.CANCELLED, failureReason = null)
    }

    fun resetActiveCalibration() {
        check(!state.isActive)
        state = state.copy(
            activeCalibration = null,
            pendingCalibration = null,
            phase = CalibrationPhase.IDLE,
            diagnostics = null,
            failureReason = null,
        )
    }

    private fun transition(phase: CalibrationPhase) {
        check(!state.isActive)
        state = state.copy(
            phase = phase,
            pendingCalibration = null,
            failureReason = null,
            diagnostics = null,
        )
    }

    private fun transitionFrom(expected: CalibrationPhase, next: CalibrationPhase) {
        require(state.phase == expected)
        state = state.copy(phase = next)
    }
}
