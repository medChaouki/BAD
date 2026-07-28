package com.titaniumharmonics.bad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.titaniumharmonics.bad.ui.practice.PracticeRoute
import com.titaniumharmonics.bad.ui.theme.BADTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BADTheme {
                PracticeRoute()
            }
        }
    }
}
