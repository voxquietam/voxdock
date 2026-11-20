package com.vox.voxdock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.notifications.MediaMonitor
import com.vox.voxdock.ui.theme.LocalVoxPalette
import kotlinx.coroutines.delay

/**
 * Reimagined left-bezel Bento capsules flanking the landscape camera cutout:
 * - Top capsule (above camera): Bento-styled battery meter with fill gauge and %
 * - Center: clear space around the front camera lens + subtle 3dp alert status bar on bezel edge
 * - Bottom capsule (below camera): clickable mini media badge (live EQ bars when playing, tap to Play/Pause)
 */
@Composable
fun LeftBezelDock(
    batteryPct: Int,
    isCharging: Boolean,
    alertLevel: String,
    isPlayingMusic: Boolean,
    hasMediaSession: Boolean,
    modifier: Modifier = Modifier
) {
    val p = LocalVoxPalette.current
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isPlayingMusic, alertLevel) {
        while (true) {
            delay(220L)
            tick++
        }
    }

    val alertColor = when (alertLevel) {
        "active" -> if (tick % 2 == 0) Color(0xFFEF4444) else Color(0xFF991B1B)
        "partial" -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E).copy(alpha = 0.65f)
    }

    val batColor = when {
        isCharging -> Color(0xFF4ADE80)
        batteryPct <= 20 -> Color(0xFFEF4444)
        batteryPct <= 45 -> Color(0xFFF59E0B)
        else -> Color(0xFF38BDF8)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(42.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
    ) {
        // Thin vertical alert bar hugging the left edge
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(0.92f)
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(alertColor)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP CAPSULE (above camera hole): Bento Battery Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, batColor.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(p.card),
                contentAlignment = Alignment.BottomCenter
            ) {
                val fillFrac = (batteryPct / 100f).coerceIn(0.06f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fillFrac)
                        .background(batColor.copy(alpha = 0.22f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isCharging) "⚡" else "BAT",
                        color = batColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp
                    )
                    Text(
                        text = "$batteryPct",
                        color = p.text,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // CAMERA HOLE CLEARANCE (keeps lens completely unobstructed)
            Spacer(modifier = Modifier.height(54.dp))

            // BOTTOM CAPSULE (below camera hole): Clickable Bento Media / EQ Card
            val mediaAccent = if (isPlayingMusic) Color(0xFF1DB954) else p.border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, mediaAccent.copy(alpha = if (isPlayingMusic) 0.7f else 1f), RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isPlayingMusic) Color(0xFF1DB954).copy(alpha = 0.12f) else p.card)
                    .clickable { MediaMonitor.togglePlayPause() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "♫",
                        color = if (isPlayingMusic) Color(0xFF4ADE80) else p.muted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    if (isPlayingMusic) {
                        val h1 = listOf(8, 22, 12, 28, 10)[tick % 5].dp
                        val h2 = listOf(24, 10, 30, 16, 22)[(tick + 2) % 5].dp
                        val h3 = listOf(14, 28, 10, 20, 26)[(tick + 4) % 5].dp
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(Modifier.width(4.dp).height(h1).clip(RoundedCornerShape(1.dp)).background(Color(0xFF1DB954)))
                            Box(Modifier.width(4.dp).height(h2).clip(RoundedCornerShape(1.dp)).background(Color(0xFF4ADE80)))
                            Box(Modifier.width(4.dp).height(h3).clip(RoundedCornerShape(1.dp)).background(Color(0xFF1DB954)))
                        }
                    } else {
                        Text(
                            text = if (hasMediaSession) "▶" else "—",
                            color = p.muted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Text(
                        text = if (isPlayingMusic) "PAUSE" else "PLAY",
                        color = if (isPlayingMusic) Color(0xFF4ADE80) else p.muted,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp
                    )
                }
            }
        }
    }
}
