package com.vox.voxdock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.PowerPayload
import com.vox.voxdock.ui.theme.LocalVoxPalette
import java.util.Calendar

@Composable
fun PowerScheduleOverlay(
    power: PowerPayload,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current
    val curHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

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
                    text = "DTEK 24H SCHEDULE // Q${power.queueGroup.ifBlank { "?" }}",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val next = power.nextChange?.let { " · NEXT: $it" }.orEmpty()
                Text(
                    text = "${power.summary}$next",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .border(1.dp, p.border, RoundedCornerShape(5.dp))
                        .background(p.card, RoundedCornerShape(5.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("⚙ QUEUE", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .border(1.dp, p.border, RoundedCornerShape(5.dp))
                        .background(p.card, RoundedCornerShape(5.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("CLOSE ✕", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        if (power.slots.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, p.border, RoundedCornerShape(7.dp))
                    .background(p.card, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Set your Odesa DTEK queue (e.g. 4.1) in Settings to load the 24h bar.",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        } else {
            // 4 rows x 6 hours = 24 hours grid (clean in both landscape and portrait)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, p.border, RoundedCornerShape(7.dp))
                    .background(p.card, RoundedCornerShape(7.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                val rows = power.slots.chunked(6)
                rows.forEach { rowSlots ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowSlots.forEach { slot ->
                            val isNow = slot.startHour == curHour
                            val (bg, txtColor, tag) = when (slot.state) {
                                "off" -> Triple(Color(0xFFEF4444).copy(alpha = 0.25f), Color(0xFFFCA5A5), "OFF")
                                "possible" -> Triple(Color(0xFFF59E0B).copy(alpha = 0.25f), Color(0xFFFDE68A), "30m")
                                else -> Triple(Color(0xFF22C55E).copy(alpha = 0.20f), Color(0xFF86EFAC), "ON")
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(
                                        width = if (isNow) 2.dp else 1.dp,
                                        color = if (isNow) p.text else txtColor.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                                    .background(bg, RoundedCornerShape(5.dp))
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "%02d:00".format(slot.startHour),
                                    color = p.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = if (isNow) "$tag ●" else tag,
                                    color = txtColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            Text("■ ON", color = Color(0xFF22C55E), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("■ 30m TRANSITION", color = Color(0xFFF59E0B), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            Text("■ OUTAGE", color = Color(0xFFEF4444), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}
