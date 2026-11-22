package com.superessence.ultrafichiers.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superessence.ultrafichiers.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Écran de démarrage : « SuperEssence. » studio puis logo de l'app + chargement. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val studioAlpha = remember { Animatable(0f) }
    val studioScale = remember { Animatable(0.7f) }
    val dot = remember { Animatable(0f) }
    val sub = remember { Animatable(0f) }
    val appAlpha = remember { Animatable(0f) }
    val appScale = remember { Animatable(0.6f) }
    val bar = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { studioAlpha.animateTo(1f, tween(500)) }
        studioScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        dot.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
        sub.animateTo(1f, tween(400))
        delay(550)
        launch { studioAlpha.animateTo(0f, tween(350)) }
        delay(250)
        launch { appAlpha.animateTo(1f, tween(350)) }
        launch { appScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        bar.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        delay(150)
        onDone()
    }

    val shadow = Shadow(Color(0xFF3A3A3A), Offset(5f, 7f), 0f)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        // Étape 1 : signature du studio
        Column(
            Modifier.alpha(studioAlpha.value).scale(studioScale.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Super") }
                    withStyle(SpanStyle(color = Yellow)) { append("Essence") }
                    withStyle(SpanStyle(color = Yellow.copy(alpha = dot.value))) { append(".") }
                },
                style = TextStyle(fontFamily = Baloo, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 46.sp, shadow = shadow)
            )
            Text(
                "S T U D I O",
                color = Muted, fontFamily = Baloo, fontSize = 14.sp,
                modifier = Modifier.alpha(sub.value).padding(top = 2.dp)
            )
        }
        // Étape 2 : l'application
        Column(
            Modifier.alpha(appAlpha.value).graphicsLayer { scaleX = appScale.value; scaleY = appScale.value },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(30.dp)))
            Spacer(Modifier.height(18.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Ultra ") }
                    withStyle(SpanStyle(color = Yellow)) { append("Fichiers") }
                },
                style = TextStyle(fontFamily = Baloo, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 34.sp, shadow = shadow)
            )
            Text("par SuperEssence Studio", color = Muted, fontSize = 13.sp, fontFamily = Baloo)
            Spacer(Modifier.height(26.dp))
            Box(Modifier.width(180.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Surface2)) {
                Box(Modifier.fillMaxWidth(bar.value).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Yellow))
            }
            Spacer(Modifier.height(10.dp))
            Text("Chargement du gestionnaire…", color = Muted, fontSize = 12.sp)
        }
    }
}
