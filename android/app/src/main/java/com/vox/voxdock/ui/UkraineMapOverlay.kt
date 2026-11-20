package com.vox.voxdock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.AlertsPayload
import com.vox.voxdock.data.OblastAlert
import com.vox.voxdock.ui.theme.LocalVoxPalette
import kotlin.math.hypot

private data class RegionNode(
    val uid: Int,
    val code: String,
    val x: Float, // normalized 0..1
    val y: Float, // normalized 0..1
    val r: Float  // relative radius
)

// Geographically arranged Ukraine oblast tile-map layout (24 oblasts + Crimea + Kyiv + Sevastopol)
private val UKRAINE_REGIONS = listOf(
    RegionNode(8, "VO", 0.14f, 0.22f, 0.055f),  // Volyn
    RegionNode(5, "RV", 0.24f, 0.22f, 0.055f),  // Rivne
    RegionNode(10, "ZT", 0.35f, 0.25f, 0.058f), // Zhytomyr
    RegionNode(14, "KV", 0.46f, 0.26f, 0.058f), // Kyiv Oblast
    RegionNode(31, "KC", 0.46f, 0.26f, 0.028f), // Kyiv City (center badge)
    RegionNode(25, "CH", 0.56f, 0.17f, 0.058f), // Chernihiv
    RegionNode(20, "SM", 0.67f, 0.20f, 0.055f), // Sumy
    RegionNode(27, "LV", 0.10f, 0.36f, 0.055f), // Lviv
    RegionNode(21, "TE", 0.20f, 0.38f, 0.050f), // Ternopil
    RegionNode(3, "KM", 0.29f, 0.38f, 0.052f),  // Khmelnytskyi
    RegionNode(4, "VI", 0.39f, 0.41f, 0.056f),  // Vinnytsia
    RegionNode(24, "CK", 0.52f, 0.38f, 0.054f), // Cherkasy
    RegionNode(19, "PL", 0.64f, 0.35f, 0.058f), // Poltava
    RegionNode(22, "KH", 0.77f, 0.34f, 0.060f), // Kharkiv
    RegionNode(16, "LG", 0.90f, 0.40f, 0.058f), // Luhansk
    RegionNode(11, "ZK", 0.07f, 0.51f, 0.048f), // Zakarpattia
    RegionNode(13, "IF", 0.15f, 0.49f, 0.048f), // Ivano-Frankivsk
    RegionNode(26, "CV", 0.23f, 0.52f, 0.045f), // Chernivtsi
    RegionNode(15, "KR", 0.55f, 0.50f, 0.055f), // Kirovohrad
    RegionNode(9, "DP", 0.69f, 0.49f, 0.060f),  // Dnipropetrovsk
    RegionNode(28, "DN", 0.84f, 0.52f, 0.058f), // Donetsk
    RegionNode(18, "OD", 0.43f, 0.65f, 0.062f), // Odesa
    RegionNode(17, "MK", 0.54f, 0.62f, 0.054f), // Mykolaiv
    RegionNode(23, "KS", 0.65f, 0.66f, 0.058f), // Kherson
    RegionNode(12, "ZP", 0.75f, 0.61f, 0.056f), // Zaporizhzhia
    RegionNode(29, "CR", 0.67f, 0.84f, 0.065f), // Crimea
    RegionNode(30, "SV", 0.62f, 0.90f, 0.028f)  // Sevastopol
)

@Composable
fun UkraineMapOverlay(
    alerts: AlertsPayload,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current
    val byUid = remember(alerts) { alerts.oblasts.associateBy { it.uid } }
    var focused by remember(alerts) { mutableStateOf<OblastAlert?>(alerts.selected) }

    val activeCount = alerts.oblasts.count { it.level == "active" }
    val partialCount = alerts.oblasts.count { it.level == "partial" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "UKRAINE AIR RAID MAP",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "RED: $activeCount  ·  YELLOW: $partialCount  ·  SOURCE: ${alerts.source.uppercase()}",
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

        // Vector map canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, p.border, RoundedCornerShape(20.dp))
                .background(p.card, RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(byUid) {
                        detectTapGestures { offset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val hit = UKRAINE_REGIONS.minByOrNull { node ->
                                hypot(offset.x - node.x * w, offset.y - node.y * h)
                            }
                            if (hit != null) {
                                focused = byUid[hit.uid]
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Subtle silhouette outline connecting border regions
                val outline = Path().apply {
                    moveTo(0.06f * w, 0.50f * h)
                    lineTo(0.12f * w, 0.18f * h)
                    lineTo(0.58f * w, 0.12f * h)
                    lineTo(0.80f * w, 0.28f * h)
                    lineTo(0.95f * w, 0.42f * h)
                    lineTo(0.84f * w, 0.64f * h)
                    lineTo(0.74f * w, 0.90f * h)
                    lineTo(0.58f * w, 0.92f * h)
                    lineTo(0.40f * w, 0.74f * h)
                    lineTo(0.22f * w, 0.56f * h)
                    close()
                }
                drawPath(outline, color = p.border.copy(alpha = 0.5f), style = Stroke(width = 2f))

                // Draw each oblast polygon/hex node
                UKRAINE_REGIONS.forEach { node ->
                    val status = byUid[node.uid]?.level ?: "none"
                    val fillColor = when (status) {
                        "active" -> Color(0xFFEF4444)
                        "partial" -> Color(0xFFF59E0B)
                        else -> Color(0xFF22C55E)
                    }
                    val isSelected = focused?.uid == node.uid
                    val cx = node.x * w
                    val cy = node.y * h
                    val rad = node.r * minOf(w, h) * 1.15f

                    drawCircle(
                        color = fillColor.copy(alpha = if (status == "none") 0.28f else 0.78f),
                        radius = rad,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = if (isSelected) p.text else fillColor,
                        radius = rad,
                        center = Offset(cx, cy),
                        style = Stroke(width = if (isSelected) 4f else 2f)
                    )
                }
            }
        }

        // Focused oblast details footer
        val item = focused ?: alerts.selected
        val (badgeColor, statusText) = when (item.level) {
            "active" -> Color(0xFFEF4444) to "AIR RAID ALERT (FULL)"
            "partial" -> Color(0xFFF59E0B) to "PARTIAL ALERT (DISTRICT)"
            else -> Color(0xFF22C55E) to "ALL CLEAR"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .background(p.card, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${item.name.uppercase()} [${item.code}]",
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = statusText,
                color = badgeColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
