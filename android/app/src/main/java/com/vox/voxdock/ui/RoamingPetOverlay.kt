package com.vox.voxdock.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vox.voxdock.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val PET_CATALOG: List<Pair<String, String>> = listOf(
    "fox" to "Fox",
    "red_panda" to "Red Panda",
    "hedgehog" to "Hedgehog",
    "owl" to "Owl",
    "raven" to "Raven",
    "cyber_fox" to "Cyber Fox"
)

private val HEART_PIXELS = arrayOf(
    ".X.X.",
    "XXXXX",
    "XXXXX",
    ".XXX.",
    "..X.."
)

private val NOTE_PIXELS = arrayOf(
    "..XXX",
    "..X.X",
    "..X..",
    "XXX..",
    "XXX.."
)

private val ZZZ_PIXELS = arrayOf(
    "XXXX",
    "..X.",
    ".X..",
    "XXXX"
)

private val EXCL_PIXELS = arrayOf(
    "X",
    "X",
    "X",
    ".",
    "X"
)

private fun DrawScope.drawMiniPixels(
    grid: Array<String>,
    originX: Float,
    originY: Float,
    px: Float,
    color: Color
) {
    grid.forEachIndexed { r, row ->
        row.forEachIndexed { c, ch ->
            if (ch == 'X') {
                drawRect(
                    color = color,
                    topLeft = Offset(originX + c * px, originY + r * px),
                    size = Size(px + 0.5f, px + 0.5f)
                )
            }
        }
    }
}

/**
 * Crisp 16-bit pixel-art pet sprite roaming along the very bottom edge (0dp taken from grid).
 * Animated states:
 * - Spotify playing: bouncy dance + floating green pixel music notes
 * - Power outage (DTEK OFF): flickering pixel candle beside the pet
 * - Active air raid alert: hides near corner in alert pose + pulsing red '!'
 * - Resting: curled sleeping frame + floating pixel 'Z'
 * - Tap: pink pixel heart
 */
@Composable
fun RoamingPetOverlay(
    petType: String,
    alertLevel: String,
    powerStatus: String,
    isPlayingMusic: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawableRes = when (petType) {
        "red_panda" -> R.drawable.pet_red_panda
        "hedgehog" -> R.drawable.pet_hedgehog
        "owl" -> R.drawable.pet_owl
        "raven" -> R.drawable.pet_raven
        "cyber_fox" -> R.drawable.pet_cyber_fox
        else -> R.drawable.pet_fox
    }

    val sheet = remember(drawableRes) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(context.resources, drawableRes, opts)?.asImageBitmap()
    }

    var posFraction by remember { mutableFloatStateOf(0.18f) }
    var facingRight by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var heartTicks by remember { mutableIntStateOf(0) }
    var resting by remember { mutableStateOf(false) }

    LaunchedEffect(alertLevel) {
        while (true) {
            delay(240L)
            tick++
            if (heartTicks > 0) heartTicks--

            if (alertLevel == "active") {
                posFraction = (posFraction - 0.035f).coerceAtLeast(0.02f)
                facingRight = false
                continue
            }

            if (tick % 36 == 0) resting = !resting
            if (!resting) {
                val step = if (isPlayingMusic) 0.017f else 0.010f
                val next = if (facingRight) posFraction + step else posFraction - step
                when {
                    next >= 0.90f -> {
                        posFraction = 0.90f
                        facingRight = false
                    }
                    next <= 0.02f -> {
                        posFraction = 0.02f
                        facingRight = true
                    }
                    else -> posFraction = next
                }
            }
        }
    }

    // Frame index: 0 = walk A, 1 = walk B, 2 = sleep, 3 = alert
    val frameIdx = when {
        alertLevel == "active" -> 3
        resting && !isPlayingMusic -> 2
        tick % 2 == 0 -> 0
        else -> 1
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        val travelPx = (constraints.maxWidth - 130).coerceAtLeast(0)
        val bounceY = if (isPlayingMusic && tick % 2 == 0) -6 else 0

        Canvas(
            modifier = Modifier
                .offset { IntOffset((travelPx * posFraction).roundToInt(), bounceY) }
                .size(width = 44.dp, height = 42.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { heartTicks = 8 }
        ) {
            val px = size.width / 22f
            val floatOffset = ((tick % 4) * px * 0.6f)

            // Floating status pixel effects above the sprite
            when {
                heartTicks > 0 -> {
                    drawMiniPixels(HEART_PIXELS, size.width * 0.38f, 2f - floatOffset * 0.4f, px, Color(0xFFF472B6))
                }
                alertLevel == "active" -> {
                    if (tick % 2 == 0) {
                        drawMiniPixels(EXCL_PIXELS, size.width * 0.48f, 2f, px * 1.1f, Color(0xFFEF4444))
                    }
                }
                isPlayingMusic -> {
                    drawMiniPixels(NOTE_PIXELS, size.width * 0.62f, 4f - floatOffset, px * 0.9f, Color(0xFF1DB954))
                    if (tick % 2 == 1) {
                        drawMiniPixels(NOTE_PIXELS, size.width * 0.16f, 8f - floatOffset, px * 0.75f, Color(0xFF4ADE80))
                    }
                }
                resting -> {
                    drawMiniPixels(ZZZ_PIXELS, size.width * 0.65f, 6f - floatOffset, px * 0.85f, Color(0xFF93C5FD))
                }
            }

            // Draw pixel candle beside pet during DTEK power outage
            if (powerStatus == "off") {
                val cx = if (facingRight) size.width - 5 * px else 2 * px
                val cy = size.height - 6 * px
                drawRect(Color(0xFFFEF3C7), Offset(cx, cy), Size(2 * px, 5 * px))
                val flameColor = if (tick % 2 == 0) Color(0xFFFACC15) else Color(0xFFF97316)
                drawRect(flameColor, Offset(cx, cy - 2 * px), Size(2 * px, 2 * px))
            }

            // Draw main 16-bit pet sprite
            if (sheet != null) {
                val fw = sheet.width / 4
                val fh = sheet.height
                val spritePx = (size.height * 0.78f).roundToInt()
                val dstX = ((size.width - spritePx) / 2f).roundToInt()
                val dstY = (size.height - spritePx).roundToInt()
                withTransform({
                    if (!facingRight) {
                        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f))
                    }
                }) {
                    drawImage(
                        image = sheet,
                        srcOffset = IntOffset(frameIdx * fw, 0),
                        srcSize = IntSize(fw, fh),
                        dstOffset = IntOffset(dstX, dstY),
                        dstSize = IntSize(spritePx, spritePx),
                        filterQuality = FilterQuality.None
                    )
                }
            }
        }
    }
}
