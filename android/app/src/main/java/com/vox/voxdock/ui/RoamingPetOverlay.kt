package com.vox.voxdock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.ui.theme.LocalVoxPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Unobtrusive mini monospace pet roaming along the bottom edge of the screen.
 * Reacts to air raid alerts, power outages, Spotify playback, and taps.
 */
@Composable
fun RoamingPetStrip(
    alertLevel: String,
    powerStatus: String,
    isPlayingMusic: Boolean,
    modifier: Modifier = Modifier
) {
    val p = LocalVoxPalette.current
    var posFraction by remember { mutableFloatStateOf(0.15f) }
    var dir by remember { mutableIntStateOf(1) }
    var stepTick by remember { mutableIntStateOf(0) }
    var heartTicks by remember { mutableIntStateOf(0) }
    var resting by remember { mutableStateOf(false) }

    LaunchedEffect(alertLevel) {
        while (true) {
            delay(350L)
            stepTick++
            if (heartTicks > 0) heartTicks--

            if (alertLevel == "active") {
                // Hide calmly near the left corner during active alert
                posFraction = (posFraction - 0.04f).coerceAtLeast(0.02f)
                dir = -1
                continue
            }

            if (stepTick % 24 == 0) {
                resting = !resting
            }
            if (!resting) {
                val speed = if (isPlayingMusic) 0.024f else 0.014f
                val next = posFraction + dir * speed
                if (next >= 0.84f) {
                    posFraction = 0.84f
                    dir = -1
                } else if (next <= 0.02f) {
                    posFraction = 0.02f
                    dir = 1
                } else {
                    posFraction = next
                }
            }
        }
    }

    val sprite = when {
        heartTicks > 0 -> "(=^♥ω♥^=)"
        alertLevel == "active" -> "[ (>_<) ]"
        powerStatus == "off" -> "🕯(=･_･=)"
        isPlayingMusic -> if (stepTick % 2 == 0) "♪(=^･o･^=)" else " (=^･ω･^=)♪"
        resting -> if (stepTick % 4 == 0) "(=-ω-=)z" else "(=-ω-=) "
        dir > 0 -> if (stepTick % 2 == 0) "=^･ω･^=»" else "=^･ω･^= "
        else -> if (stepTick % 2 == 0) "«=^･ω･^=" else " =^･ω･^="
    }

    val tint = when {
        heartTicks > 0 -> Color(0xFFF472B6)
        alertLevel == "active" -> Color(0xFFEF4444)
        powerStatus == "off" -> Color(0xFFFACC15)
        isPlayingMusic -> Color(0xFF22C55E)
        else -> p.muted.copy(alpha = 0.85f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val travelPx = (constraints.maxWidth - 180).coerceAtLeast(0)
        Text(
            text = sprite,
            color = tint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier
                .offset { IntOffset((travelPx * posFraction).roundToInt(), 0) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { heartTicks = 6 }
        )
    }
}
