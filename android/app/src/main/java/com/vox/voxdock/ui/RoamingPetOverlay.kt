package com.vox.voxdock.ui

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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// 14x10 pixel-art cat frames (facing right; flipped horizontally when walking left)
// Legend: '.' transparent, 'O' orange fur, 'L' light belly/face, 'D' dark outline/stripes, 'E' eye, 'P' pink nose/heart, 'G' music note
private val CAT_WALK_1 = arrayOf(
    "........O..O..",
    ".......OOOOOO.",
    ".O.....OEOOEO.",
    ".O....OOOOPLOO",
    "..OOOOOOODDOO.",
    "..OOODDOOOOO..",
    "..OOOOOOOOOO..",
    "..OOLLLLLLOO..",
    "..OO......OO..",
    ".OO........OO."
)

private val CAT_WALK_2 = arrayOf(
    "........O..O..",
    ".......OOOOOO.",
    "..O....OEOOEO.",
    ".O....OOOOPLOO",
    "..OOOOOOODDOO.",
    "..OOODDOOOOO..",
    "..OOOOOOOOOO..",
    "..OOLLLLLLOO..",
    "...OO....OO...",
    "...OO....OO..."
)

private val CAT_SLEEP = arrayOf(
    "..............",
    "..............",
    ".........O..O.",
    "....OOOO.OOOO.",
    "..OOOOOODDOOO.",
    ".OOODDOOODODO.",
    ".OOOOOOOOOOOO.",
    ".OOOOOOOOPLOO.",
    "..OOOOOOOOOO..",
    ".............."
)

private val CAT_HIDE = arrayOf(
    "..............",
    "..............",
    ".....O....O...",
    "....OOOOOOOO..",
    "...OOEOOOEOOO.",
    "...OOOOPLOOOO.",
    "..OOOOOOOOOOOO",
    "..OOLLLLLLLLOO",
    "..............",
    ".............."
)

private val HEART_SPRITE = arrayOf(
    ".PP.PP.",
    "PPPPPPP",
    "PPPPPPP",
    ".PPPPP.",
    "..PPP..",
    "...P..."
)

/**
 * Pixel-art cat roaming along the very bottom edge (floats over the bottom margin,
 * takes 0dp of grid height). Reacts to alerts, power outages, Spotify, and taps.
 */
@Composable
fun RoamingPetOverlay(
    alertLevel: String,
    powerStatus: String,
    isPlayingMusic: Boolean,
    modifier: Modifier = Modifier
) {
    var posFraction by remember { mutableFloatStateOf(0.18f) }
    var facingRight by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var heartTicks by remember { mutableIntStateOf(0) }
    var resting by remember { mutableStateOf(false) }

    LaunchedEffect(alertLevel) {
        while (true) {
            delay(280L)
            tick++
            if (heartTicks > 0) heartTicks--

            if (alertLevel == "active") {
                posFraction = (posFraction - 0.035f).coerceAtLeast(0.02f)
                facingRight = false
                continue
            }

            if (tick % 32 == 0) resting = !resting
            if (!resting) {
                val step = if (isPlayingMusic) 0.018f else 0.011f
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

    val frame = when {
        alertLevel == "active" -> CAT_HIDE
        resting && !isPlayingMusic -> CAT_SLEEP
        tick % 2 == 0 -> CAT_WALK_1
        else -> CAT_WALK_2
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        val spriteWidthDp = 32.dp
        val travelPx = (constraints.maxWidth - 110).coerceAtLeast(0)
        val bounceY = if (isPlayingMusic && tick % 2 == 0) -3 else 0

        Canvas(
            modifier = Modifier
                .offset { IntOffset((travelPx * posFraction).roundToInt(), bounceY) }
                .size(width = spriteWidthDp, height = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { heartTicks = 7 }
        ) {
            val cols = 14
            val rows = 10
            val px = minOf(size.width / cols, size.height / (rows + 4))
            val yOffset = size.height - rows * px

            val furColor = when {
                alertLevel == "active" -> Color(0xFFF87171)
                powerStatus == "off" -> Color(0xFFFDE047)
                else -> Color(0xFFF59E0B)
            }
            val bellyColor = Color(0xFFFEF3C7)
            val stripeColor = Color(0xFFB45309)
            val eyeColor = Color(0xFF111116)
            val pinkColor = Color(0xFFF472B6)

            // Draw pixel heart above cat when petted
            if (heartTicks > 0) {
                val hp = px * 0.75f
                val hx0 = (size.width - 7 * hp) / 2f
                HEART_SPRITE.forEachIndexed { r, row ->
                    row.forEachIndexed { c, ch ->
                        if (ch == 'P') {
                            drawRect(
                                color = pinkColor,
                                topLeft = Offset(hx0 + c * hp, r * hp),
                                size = Size(hp + 0.5f, hp + 0.5f)
                            )
                        }
                    }
                }
            }

            // Draw cat sprite
            frame.forEachIndexed { r, row ->
                row.forEachIndexed { c, ch ->
                    val color = when (ch) {
                        'O' -> furColor
                        'L' -> bellyColor
                        'D' -> stripeColor
                        'E' -> eyeColor
                        'P' -> pinkColor
                        else -> null
                    } ?: return@forEachIndexed

                    val drawCol = if (facingRight) c else (cols - 1 - c)
                    drawRect(
                        color = color,
                        topLeft = Offset(drawCol * px, yOffset + r * px),
                        size = Size(px + 0.5f, px + 0.5f)
                    )
                }
            }
        }
    }
}
