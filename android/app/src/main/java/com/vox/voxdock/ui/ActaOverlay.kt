package com.vox.voxdock.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.ActaPayload
import com.vox.voxdock.ui.theme.LocalVoxPalette

@Composable
fun ActaOverlay(
    acta: ActaPayload,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current
    val context = LocalContext.current

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
                    text = "ACTA // OPEN TASKS (${acta.count})",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Tap any task to open in browser",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            SmallPill(label = "CLOSE ✕", highlighted = false, onClick = onClose)
        }

        if (acta.tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, p.border, RoundedCornerShape(7.dp))
                    .background(p.card, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No open tasks in ACTA.", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(acta.tasks, key = { it.id.ifBlank { it.title } }) { task ->
                    val openBrowser = {
                        if (task.url.startsWith("http")) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(task.url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                            .background(p.card, RoundedCornerShape(7.dp))
                            .clickable { openBrowser() }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val meta = listOfNotNull(
                                task.identifier.takeIf { it.isNotBlank() },
                                task.status.uppercase().takeIf { it.isNotBlank() },
                                task.assignee.takeIf { it.isNotBlank() }?.let { "@$it" }
                            ).joinToString(" · ")
                            Text(
                                text = meta.ifBlank { task.project },
                                color = Color(0xFF818CF8),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            if (task.url.startsWith("http")) {
                                SmallPill(label = "OPEN ↗", highlighted = true, onClick = openBrowser)
                            }
                        }
                        Text(
                            text = task.title,
                            color = p.text,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (task.labels.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                task.labels.take(4).forEach { lbl ->
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, p.border, RoundedCornerShape(4.dp))
                                            .background(p.cardAlt, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(lbl, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
