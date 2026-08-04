package com.titaniumharmonics.bad.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingCalibrationStateMachineTest {
    @Test
    fun successfulLifecycleWaitsForAcceptanceBeforeActivatingResult() {
        val machine = allowedMachine()
        machine.preparing()
        machine.recording()
        machine.playingClicks()
        machine.processing()
        val calibration = calibration(50L)
        machine.success(calibration, null)
        assertEquals(CalibrationPhase.SUCCESS, machine.state.phase)
        assertEquals(calibration, machine.state.pendingCalibration)
        assertEquals(null, machine.state.activeCalibration)
        assertTrue(machine.state.isAwaitingDecision)
        assertFalse(machine.state.canStart)
        assertFalse(machine.state.isFinished)

        assertEquals(calibration, machine.acceptPendingCalibration())
        assertEquals(calibration, machine.state.activeCalibration)
        assertEquals(null, machine.state.pendingCalibration)
        assertTrue(machine.state.isFinished)
    }

    @Test
    fun rejectingResultPreservesPreviousCalibration() {
        val previous = calibration(10L)
        val candidate = calibration(50L)
        val machine = TimingCalibrationStateMachine(previous)
        machine.checkingRoute()
        machine.routeChecked(CalibrationRouteDecision(CalibrationRouteStatus.ALLOWED, "allowed"))
        machine.preparing()
        machine.recording()
        machine.playingClicks()
        machine.processing()
        machine.success(candidate, null)

        machine.rejectPendingCalibration()

        assertEquals(CalibrationPhase.REJECTED, machine.state.phase)
        assertEquals(previous, machine.state.activeCalibration)
        assertEquals(null, machine.state.pendingCalibration)
        assertTrue(machine.state.isFinished)
    }

    @Test
    fun secondStartIsPreventedWhileActive() {
        val machine = allowedMachine()
        machine.preparing()
        assertFalse(machine.state.canStart)
        assertThrows(IllegalStateException::class.java) { machine.preparing() }
    }

    @Test
    fun blockedAndUncertainRoutesControlStart() {
        val blocked = TimingCalibrationStateMachine(null)
        blocked.checkingRoute()
        blocked.routeChecked(CalibrationRouteDecision(CalibrationRouteStatus.BLOCKED, "blocked"))
        assertFalse(blocked.state.canStart)
        val uncertain = TimingCalibrationStateMachine(null)
        uncertain.checkingRoute()
        uncertain.routeChecked(CalibrationRouteDecision(CalibrationRouteStatus.UNCERTAIN, "check"))
        assertFalse(uncertain.state.canStart)
        uncertain.confirmUncertainRoute(true)
        assertTrue(uncertain.state.canStart)
    }

    @Test
    fun cancellationWorksDuringPreparationPlaybackAndProcessing() {
        listOf(CalibrationPhase.PREPARING, CalibrationPhase.PLAYING_CLICKS, CalibrationPhase.PROCESSING).forEach { target ->
            val machine = allowedMachine()
            machine.preparing()
            if (target >= CalibrationPhase.PLAYING_CLICKS) { machine.recording(); machine.playingClicks() }
            if (target == CalibrationPhase.PROCESSING) machine.processing()
            machine.cancel()
            assertEquals(CalibrationPhase.CANCELLED, machine.state.phase)
        }
    }

    @Test
    fun failurePreservesPreviousCalibrationAndResetRemovesIt() {
        val previous = calibration(10L)
        val machine = TimingCalibrationStateMachine(previous)
        machine.fail(CalibrationFailureReason.TOO_FEW_CLICKS)
        assertEquals(previous, machine.state.activeCalibration)
        assertTrue(machine.state.isFinished)
        machine.resetActiveCalibration()
        assertEquals(null, machine.state.activeCalibration)
        assertFalse(machine.state.isFinished)
    }

    @Test
    fun activeAndIdleStatesAreNotFinishedButCancellationIs() {
        val machine = allowedMachine()
        assertFalse(machine.state.isFinished)
        machine.preparing()
        assertFalse(machine.state.isFinished)
        machine.cancel()
        assertTrue(machine.state.isFinished)
    }

    private fun allowedMachine() = TimingCalibrationStateMachine(null).also {
        it.checkingRoute()
        it.routeChecked(CalibrationRouteDecision(CalibrationRouteStatus.ALLOWED, "allowed"))
    }

    private fun calibration(offset: Long) = TimingCalibration(
        offset, 48_000, CalibrationConfidence.HIGH, 8, 8, 0, 1, 1,
    )
}
