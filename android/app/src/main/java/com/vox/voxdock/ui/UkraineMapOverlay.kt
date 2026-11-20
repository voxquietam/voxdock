package com.vox.voxdock.ui

import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.Region
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import com.vox.voxdock.data.AlertsPayload
import com.vox.voxdock.data.OblastAlert
import com.vox.voxdock.ui.theme.LocalVoxPalette

private data class ParsedRegion(
    val uid: Int,
    val name: String,
    val androidPath: android.graphics.Path
)

@Composable
fun UkraineMapOverlay(
    alerts: AlertsPayload,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val byUid = remember(alerts) { alerts.oblasts.associateBy { it.uid } }
    var focused by remember(alerts) { mutableStateOf<OblastAlert?>(alerts.selected) }

    val parsedRegions = remember {
        UKRAINE_REGION_PATHS.map { item ->
            ParsedRegion(
                uid = item.uid,
                name = item.name,
                androidPath = PathParser.createPathFromPathData(item.svgPath)
            )
        }
    }

    val activeCount = alerts.oblasts.count { it.level == "active" }
    val partialCount = alerts.oblasts.count { it.level == "partial" }
    val item = focused ?: alerts.selected
    val (badgeColor, statusText) = when (item.level) {
        "active" -> Color(0xFFEF4444) to "AIR RAID ALERT"
        "partial" -> Color(0xFFF59E0B) to "PARTIAL ALERT"
        else -> Color(0xFF22C55E) to "ALL CLEAR"
    }

    if (isLandscape) {
        // Landscape: full-height map canvas with floating corner badges so the map is ~2x larger
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(p.bg)
                .safeDrawingPadding()
                .padding(8.dp)
        ) {
            UkraineVectorCanvas(
                parsedRegions = parsedRegions,
                byUid = byUid,
                focusedUid = focused?.uid,
                onSelect = { focused = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
            )

            // Top-left compact title & counters
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .border(1.dp, p.border, RoundedCornerShape(5.dp))
                    .background(p.card.copy(alpha = 0.88f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "UKRAINE MAP",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = "R:$activeCount · Y:$partialCount · ${alerts.source.uppercase()}",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }

            // Top-right Close button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .border(1.dp, p.border, RoundedCornerShape(5.dp))
                    .background(p.card.copy(alpha = 0.88f), RoundedCornerShape(5.dp))
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

            // Bottom-left selected oblast badge (tucks neatly under Zakarpattia / Odesa empty space)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                    .background(p.card.copy(alpha = 0.9f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "${item.name.uppercase()} [${item.code}]",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Text(
                    text = statusText,
                    color = badgeColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    } else {
        // Portrait: classic stacked header + map + footer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(p.bg)
                .safeDrawingPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                        .border(1.dp, p.border, RoundedCornerShape(5.dp))
                        .background(p.card, RoundedCornerShape(5.dp))
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, p.border, RoundedCornerShape(7.dp))
                    .background(p.card, RoundedCornerShape(7.dp))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                UkraineVectorCanvas(
                    parsedRegions = parsedRegions,
                    byUid = byUid,
                    focusedUid = focused?.uid,
                    onSelect = { focused = it },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                    .background(p.card, RoundedCornerShape(7.dp))
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
}

@Composable
private fun UkraineVectorCanvas(
    parsedRegions: List<ParsedRegion>,
    byUid: Map<Int, OblastAlert>,
    focusedUid: Int?,
    onSelect: (OblastAlert) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalVoxPalette.current
    Canvas(
        modifier = modifier.pointerInput(byUid, parsedRegions) {
            detectTapGestures { offset ->
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                val scale = minOf(w / UKRAINE_MAP_WIDTH, h / UKRAINE_MAP_HEIGHT)
                val dx = (w - UKRAINE_MAP_WIDTH * scale) / 2f
                val dy = (h - UKRAINE_MAP_HEIGHT * scale) / 2f

                val mapX = ((offset.x - dx) / scale).toInt()
                val mapY = ((offset.y - dy) / scale).toInt()
                val clip = Region(0, 0, UKRAINE_MAP_WIDTH.toInt(), UKRAINE_MAP_HEIGHT.toInt())

                val hit = parsedRegions.asReversed().firstOrNull { reg ->
                    val bounds = RectF()
                    @Suppress("DEPRECATION")
                    reg.androidPath.computeBounds(bounds, true)
                    if (!bounds.contains(mapX.toFloat(), mapY.toFloat())) {
                        false
                    } else {
                        val region = Region()
                        region.setPath(reg.androidPath, clip)
                        region.contains(mapX, mapY)
                    }
                }
                if (hit != null) {
                    byUid[hit.uid]?.let(onSelect)
                }
            }
        }
    ) {
        val scale = minOf(size.width / UKRAINE_MAP_WIDTH, size.height / UKRAINE_MAP_HEIGHT)
        val dx = (size.width - UKRAINE_MAP_WIDTH * scale) / 2f
        val dy = (size.height - UKRAINE_MAP_HEIGHT * scale) / 2f

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }

        parsedRegions.forEach { reg ->
            val status = byUid[reg.uid]?.level ?: "none"
            val fillColor = when (status) {
                "active" -> Color(0xFFEF4444)
                "partial" -> Color(0xFFF59E0B)
                else -> Color(0xFF22C55E)
            }
            val isSelected = focusedUid == reg.uid

            val transformed = android.graphics.Path()
            reg.androidPath.transform(matrix, transformed)
            val composePath = transformed.asComposePath()

            drawPath(
                path = composePath,
                color = fillColor.copy(alpha = when {
                    status == "active" -> 0.78f
                    status == "partial" -> 0.72f
                    isSelected -> 0.45f
                    else -> 0.24f
                })
            )
            drawPath(
                path = composePath,
                color = if (isSelected) p.text else p.bg.copy(alpha = 0.85f),
                style = Stroke(width = if (isSelected) 3.5f else 1.5f)
            )
        }
    }
}
