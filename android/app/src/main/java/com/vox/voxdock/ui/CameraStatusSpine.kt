package com.vox.voxdock.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.R
import com.vox.voxdock.ui.theme.LocalVoxPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Ambient vertical Status Spine occupying the left display-cutout strip in landscape:
 * - Left edge: 3dp air-raid alert light bar (green / yellow / pulsing red)
 * - Above camera cutout: vertical battery indicator + percentage
 * - Below camera cutout: live 3-bar Spotify equalizer (or vertical page dots when idle)
 * - Periodic cameo: selected pixel pet peeks out from beside the camera cutout every ~35s
 */
@Composable
fun CameraStatusSpine(
    batteryPct: Int,
    isCharging: Boolean,
    alertLevel: String,
    isPlayingMusic: Boolean,
    petEnabled: Boolean,
    petType: String,
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val p = LocalVoxPalette.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var peeking by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        while (true) {
            delay(250L)
            tick++
            // Peek for ~4 seconds every ~35 seconds
            val cycle = tick % 140
            peeking = petEnabled && cycle in 120..136
        }
    }

    val alertBarColor = when (alertLevel) {
        "active" -> if (tick % 2 == 0) Color(0xFFEF4444) else Color(0xFF7F1D1D)
        "partial" -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E).copy(alpha = 0.55f)
    }

    val batColor = when {
        isCharging -> Color(0xFF4ADE80)
        batteryPct <= 20 -> Color(0xFFEF4444)
        batteryPct <= 45 -> Color(0xFFF59E0B)
        else -> p.muted
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(34.dp)
            .padding(vertical = 10.dp)
    ) {
        // 1. Thin 3dp alert status light strip along the very left bezel
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.86f)
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(alertBarColor)
        )

        // 2. Top (above camera hole): Battery gauge + %
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 4.dp, top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(p.cardAlt),
                contentAlignment = Alignment.BottomCenter
            ) {
                val frac = (batteryPct / 100f).coerceIn(0.08f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(frac)
                        .background(batColor)
                )
            }
            Text(
                text = if (isCharging) "⚡$batteryPct" else "$batteryPct%",
                color = batColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )
        }

        // 3. Periodic pet peek right below the camera hole
        if (peeking && sheet != null) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 2.dp, y = 34.dp)
                    .size(26.dp)
            ) {
                val fw = sheet.width / 4
                val fh = sheet.height
                drawImage(
                    image = sheet,
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(fw, fh),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.None
                )
            }
        }

        // 4. Bottom (below camera hole): Live Spotify 3-bar equalizer OR vertical page dots
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 4.dp, bottom = 10.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isPlayingMusic) {
                val h1 = listOf(6, 16, 10, 20, 8)[tick % 5].dp
                val h2 = listOf(18, 8, 22, 12, 16)[(tick + 2) % 5].dp
                val h3 = listOf(10, 20, 8, 14, 18)[(tick + 4) % 5].dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(24.dp)
                ) {
                    Box(Modifier.width(4.dp).height(h1).clip(RoundedCornerShape(1.dp)).background(Color(0xFF1DB954)))
                    Box(Modifier.width(4.dp).height(h2).clip(RoundedCornerShape(1.dp)).background(Color(0xFF4ADE80)))
                    Box(Modifier.width(4.dp).height(h3).clip(RoundedCornerShape(1.dp)).background(Color(0xFF1DB954)))
                }
            } else if (pageCount > 1) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 0 until pageCount) {
                        val active = i == currentPage
                        Box(
                            modifier = Modifier
                                .size(if (active) 6.dp else 4.dp)
                                .clip(CircleShape)
                                .background(if (active) p.text else p.muted.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }
    }
}
