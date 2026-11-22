@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.superessence.ultrafichiers.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.superessence.ultrafichiers.R

val Yellow = Color(0xFFFFC824)
val YellowDark = Color(0xFFE6A400)
val Bg = Color(0xFF0B0B0B)
val Surface1 = Color(0xFF151515)
val Surface2 = Color(0xFF1E1E1E)
val Muted = Color(0xFF9A9A9A)
val Danger = Color(0xFFFF5A5A)
val Ok = Color(0xFF5BE38C)

private fun baloo(w: Int) = Font(R.font.baloo, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Baloo = FontFamily(baloo(500), baloo(600), baloo(700), baloo(800))

@Composable
fun UltraTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Yellow, onPrimary = Color.Black,
            secondary = Yellow, onSecondary = Color.Black,
            background = Bg, onBackground = Color.White,
            surface = Surface1, onSurface = Color.White,
            surfaceVariant = Surface2, onSurfaceVariant = Muted,
            surfaceContainer = Surface1, surfaceContainerHigh = Surface2,
            surfaceContainerHighest = Surface2, surfaceContainerLow = Surface1,
            error = Danger, outline = Color(0xFF333333),
        ),
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold),
            headlineMedium = base.headlineMedium.copy(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold),
            headlineSmall = base.headlineSmall.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 17.sp),
            labelLarge = base.labelLarge.copy(fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 15.sp),
        ),
        content = content
    )
}

val TitleStyle = TextStyle(fontFamily = Baloo, fontWeight = FontWeight.ExtraBold)
