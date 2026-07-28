package com.titaniumharmonics.bad

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.titaniumharmonics.bad.ui.practice.PracticeRoute
import com.titaniumharmonics.bad.ui.startup.StartupScreen
import com.titaniumharmonics.bad.ui.theme.BADTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_BAD)
        super.onCreate(savedInstanceState)
        val systemSplashExited = mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
                systemSplashExited.value = true
            }
        }
        enableEdgeToEdge()
        setContent {
            BADTheme {
                val systemSplashIsGone by systemSplashExited
                var startupVisible by rememberSaveable {
                    mutableStateOf(true)
                }
                LaunchedEffect(systemSplashIsGone) {
                    if (systemSplashIsGone) {
                        delay(STARTUP_SCREEN_DURATION_MILLIS)
                        startupVisible = false
                    }
                }

                if (startupVisible) {
                    StartupScreen()
                } else {
                    PracticeRoute()
                }
            }
        }
    }

    private companion object {
        const val STARTUP_SCREEN_DURATION_MILLIS = 1_000L
    }
}
