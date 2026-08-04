package com.titaniumharmonics.bad.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    activeTimingCalibration: TimingCalibration?,
    onOpenTimingCalibration: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    SettingsScreen(
        activeTimingCalibration = activeTimingCalibration,
        onOpenTimingCalibration = onOpenTimingCalibration,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    activeTimingCalibration: TimingCalibration?,
    onOpenTimingCalibration: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Timing calibration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(activeTimingCalibration.statusText())
                    Text("Calibrate the phone speaker and built-in microphone timing.")
                    OutlinedButton(
                        onClick = onOpenTimingCalibration,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (activeTimingCalibration == null) {
                                "Calibrate"
                            } else {
                                "Calibrate again"
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun TimingCalibration?.statusText(): String = if (this == null) {
    "Status: Not calibrated"
} else {
    val offset = offsetMillis.roundToInt()
    val formattedOffset = if (offset >= 0) "+$offset" else offset.toString()
    val formattedConfidence = confidence.name.lowercase().replaceFirstChar(Char::uppercase)
    "Status: Calibrated · $formattedOffset ms · $formattedConfidence confidence"
}
