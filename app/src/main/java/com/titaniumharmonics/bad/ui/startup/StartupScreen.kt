package com.titaniumharmonics.bad.ui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.titaniumharmonics.bad.R

@Composable
fun StartupScreen(
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.bad_splash),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}
