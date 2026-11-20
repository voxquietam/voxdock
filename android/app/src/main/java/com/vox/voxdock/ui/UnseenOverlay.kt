package com.vox.voxdock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.UnseenFinding
import com.vox.voxdock.ui.theme.LocalVoxPalette

private fun unseenAccent(category: String): Color = when (category) {
    "sea" -> Color(0xFF06B6D4)
    "air" -> Color(0xFFF59E0B)
    "quake" -> Color(0xFFFB7185)
    "space" -> Color(0xFF818CF8)
    "bio" -> Color(0xFF34D399)
    else -> Color(0xFF38BDF8)
}

@Composable
fun UnseenOverlay(
    city: String,
    findings: List<UnseenFinding>,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "UNSEEN // ${city.uppercase()}",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${findings.size} ANOMALIES DETECTED NEARBY",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            Box(
                modifier = Modifier
                    .border(1.dp, p.border, RoundedCornerShape(10.dp))
                    .background(p.card, RoundedCornerShape(10.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "CLOSE ✕",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        if (findings.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, p.border, RoundedCornerShape(16.dp))
                    .background(p.card, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "All quiet around $city right now.",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(findings, key = { it.id }) { item ->
                    val accent = unseenAccent(item.category)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .background(p.card, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "◈ ${item.category.uppercase()} · ${item.metric}",
                                color = accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "score ${"%.1f".format(item.score)} / 3.0",
                                color = p.muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = item.title,
                            color = p.text,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = item.explanation,
                            color = p.muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                        val frac = (item.score / 3.0).toFloat().coerceIn(0.08f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(p.border)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .fillMaxHeight()
                                    .background(accent)
                            )
                        }
                    }
                }
            }
        }
    }
}
