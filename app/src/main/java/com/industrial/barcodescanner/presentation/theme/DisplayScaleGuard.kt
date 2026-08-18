package com.industrial.barcodescanner.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Keeps the Compose layout usable when Android's Display size setting is very
 * large. The guard caps enlargement without disabling scrolling, accessibility
 * touch targets, orientation changes, or the user's appearance preference.
 */
@Composable
fun BarcodeDisplayScaleGuard(
    isArabic: Boolean = false,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val shortSide = min(configuration.screenWidthDp, configuration.screenHeightDp).dp
    val layoutFactor = (shortSide / 360.dp).coerceIn(0.80f, 1f)
    val baseFontScale = min(density.fontScale, 1f)
    val guardedFontScale = if (isArabic) {
        (baseFontScale * 1.12f).coerceAtMost(1.14f)
    } else {
        baseFontScale
    }
    val guardedDensity = Density(
        density = density.density * layoutFactor,
        fontScale = guardedFontScale
    )
    CompositionLocalProvider(LocalDensity provides guardedDensity) {
        content()
    }
}
