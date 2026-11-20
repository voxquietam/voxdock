package com.vox.voxdock.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.AppSettings
import com.vox.voxdock.data.DashboardResponse
import com.vox.voxdock.notifications.CapturedNotification
import com.vox.voxdock.ui.theme.LocalVoxPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    dashboard: DashboardResponse,
    settings: AppSettings,
    notifications: List<CapturedNotification>,
    syncError: String?,
    isNightOled: Boolean,
    onToggleTheme: () -> Unit,
    onToggleDock: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenUnseen: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissNote: (Int) -> Unit,
    onOpenNotification: (CapturedNotification) -> Unit,
    onDismissNotification: (String) -> Unit,
    onToggleMuteSource: (String) -> Unit,
    onWakeInteraction: () -> Unit
) {
    var clockText by remember { mutableStateOf(formatTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = formatTime()
            delay(15_000L)
        }
    }

    // Night OLED minimal screen (auto-bypassed when red alert is active)
    if (isNightOled && dashboard.alerts.selected.level != "active") {
        NightOledScreen(
            clockText = clockText,
            alertLevel = dashboard.alerts.selected.level,
            onWake = onWakeInteraction
        )
        return
    }

    val p = LocalVoxPalette.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var activeSourceFilter by remember { mutableStateOf("ALL") }
    var unseenIndex by remember { mutableIntStateOf(0) }

    val filteredNotifs = remember(notifications, settings.mutedSources, activeSourceFilter) {
        notifications.filter { n ->
            !settings.mutedSources.contains(n.source) &&
                (activeSourceFilter == "ALL" || n.source == activeSourceFilter)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .safeDrawingPadding()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onWakeInteraction() }
            .padding(12.dp)
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // LEFT COLUMN (Status & Context) — compact + scrollable so nothing clips with status bar
                Column(
                    modifier = Modifier
                        .weight(5f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeaderBar(
                        clockText = clockText,
                        city = settings.city,
                        settings = settings,
                        syncError = syncError,
                        compact = true,
                        onToggleDock = onToggleDock,
                        onToggleTheme = onToggleTheme,
                        onOpenSettings = onOpenSettings
                    )
                    AlertAndWeatherRow(
                        dashboard = dashboard,
                        compact = true,
                        onOpenMap = onOpenMap,
                        onOpenWeather = onOpenWeather
                    )
                    PowerStrip(dashboard = dashboard, onOpenSettings = onOpenSettings)
                    UnseenTile(
                        dashboard = dashboard,
                        index = unseenIndex,
                        compact = true,
                        onOpen = onOpenUnseen
                    )
                    AgentStatusStrip(dashboard = dashboard)
                }

                // RIGHT COLUMN (Live Stream: MCP + Notifications)
                Column(
                    modifier = Modifier.weight(7f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    McpNotesCard(
                        dashboard = dashboard,
                        onDismissNote = onDismissNote,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NotificationsCard(
                        items = filteredNotifs,
                        activeFilter = activeSourceFilter,
                        mutedSources = settings.mutedSources,
                        onFilterChange = { activeSourceFilter = it },
                        onOpen = onOpenNotification,
                        onDismiss = onDismissNotification,
                        onToggleMute = onToggleMuteSource,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        } else {
            // PORTRAIT (1-column Bento)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeaderBar(
                    clockText = clockText,
                    city = settings.city,
                    settings = settings,
                    syncError = syncError,
                    onToggleDock = onToggleDock,
                    onToggleTheme = onToggleTheme,
                    onOpenSettings = onOpenSettings
                )
                AlertAndWeatherRow(
                    dashboard = dashboard,
                    onOpenMap = onOpenMap,
                    onOpenWeather = onOpenWeather
                )
                PowerStrip(dashboard = dashboard, onOpenSettings = onOpenSettings)
                AgentStatusStrip(dashboard = dashboard)
                UnseenTile(
                    dashboard = dashboard,
                    index = unseenIndex,
                    onOpen = onOpenUnseen
                )
                McpNotesCard(
                    dashboard = dashboard,
                    onDismissNote = onDismissNote,
                    modifier = Modifier.fillMaxWidth()
                )
                NotificationsCard(
                    items = filteredNotifs,
                    activeFilter = activeSourceFilter,
                    mutedSources = settings.mutedSources,
                    onFilterChange = { activeSourceFilter = it },
                    onOpen = onOpenNotification,
                    onDismiss = onDismissNotification,
                    onToggleMute = onToggleMuteSource,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NightOledScreen(
    clockText: String,
    alertLevel: String,
    onWake: () -> Unit
) {
    val dotColor = when (alertLevel) {
        "partial" -> Color(0xFFF59E0B)
        else -> Color(0xFF166534)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onWake() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clockText,
                color = Color(0xFF52525B),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Text(
                    text = if (alertLevel == "partial") "PARTIAL ALERT" else "NIGHT DOCK · TAP TO WAKE",
                    color = Color(0xFF3F3F46),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    clockText: String,
    city: String,
    settings: AppSettings,
    syncError: String?,
    compact: Boolean = false,
    onToggleDock: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val p = LocalVoxPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (syncError == null) Color(0xFF22C55E) else Color(0xFFF59E0B))
                )
                Text(
                    text = "VOXDOCK // ${city.uppercase()}",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            Text(
                text = clockText,
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 23.sp else 30.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallPill(
                label = if (settings.dockMode) "☾ DOCK ON" else "☾ DOCK",
                highlighted = settings.dockMode,
                onClick = onToggleDock
            )
            SmallPill(
                label = if (settings.darkTheme) "☀" else "☾",
                highlighted = false,
                onClick = onToggleTheme
            )
            SmallPill(label = "⚙", highlighted = false, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun SmallPill(label: String, highlighted: Boolean, onClick: () -> Unit) {
    val p = LocalVoxPalette.current
    Box(
        modifier = Modifier
            .border(1.dp, if (highlighted) Color(0xFF6366F1) else p.border, RoundedCornerShape(10.dp))
            .background(if (highlighted) Color(0xFF6366F1) else p.card, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (highlighted) Color.White else p.text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun AlertAndWeatherRow(
    dashboard: DashboardResponse,
    compact: Boolean = false,
    onOpenMap: () -> Unit,
    onOpenWeather: () -> Unit
) {
    val p = LocalVoxPalette.current
    val sel = dashboard.alerts.selected
    val (bg, border, txt, label) = when (sel.level) {
        "active" -> Quad(p.alertActiveBg, p.alertActiveBorder, p.alertActiveText, "ALERT 🚨")
        "partial" -> Quad(p.alertPartialBg, p.alertPartialBorder, p.alertPartialText, "PARTIAL ⚠")
        else -> Quad(p.alertNoneBg, p.alertNoneBorder, p.alertNoneText, "CLEAR ✓")
    }
    val pad = if (compact) 8.dp else 10.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Alert tile (tap -> fullscreen Ukraine map)
        Column(
            modifier = Modifier
                .weight(3f)
                .border(1.dp, border, RoundedCornerShape(14.dp))
                .background(bg, RoundedCornerShape(14.dp))
                .clickable { onOpenMap() }
                .padding(pad)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AIR RAID", color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                Text("MAP ↗", color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Text(label, color = txt, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(sel.name, color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }

        // Weather tile (tap -> 10-day forecast)
        val cur = dashboard.weather.current
        Column(
            modifier = Modifier
                .weight(2f)
                .border(1.dp, p.weatherBorder.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .background(p.weatherBg, RoundedCornerShape(14.dp))
                .clickable { onOpenWeather() }
                .padding(pad)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(weatherIcon(cur.weatherCode), fontSize = 14.sp)
                Text("10D ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Text("${cur.temp}°C", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val seaTxt = cur.seaTemp?.let { "sea ${it}°" } ?: "${cur.windSpeed} km/h"
            Text(seaTxt, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PowerStrip(dashboard: DashboardResponse, onOpenSettings: () -> Unit) {
    val p = LocalVoxPalette.current
    val pw = dashboard.power
    val dot = when (pw.status) {
        "off" -> Color(0xFFEF4444)
        "possible" -> Color(0xFFF59E0B)
        "on" -> Color(0xFF22C55E)
        else -> p.muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.powerBorder.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .background(p.powerBg, RoundedCornerShape(14.dp))
            .clickable { onOpenSettings() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(
                text = "⚡ ${pw.summary}",
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        pw.nextChange?.let {
            Text(it, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
    }
}

@Composable
private fun UnseenTile(
    dashboard: DashboardResponse,
    index: Int,
    compact: Boolean = false,
    onOpen: () -> Unit
) {
    val p = LocalVoxPalette.current
    val item = dashboard.unseen.getOrNull(index)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.unseenBorder.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .background(p.unseenBg, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(if (compact) 8.dp else 10.dp)
    ) {
        if (item == null) {
            Text("◈ UNSEEN · QUIET", color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("Nothing unusual detected around you right now.", color = p.text.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("◈ UNSEEN · ${item.category.uppercase()}", color = p.unseenBorder, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                Text("score ${"%.1f".format(item.score)} · ${dashboard.unseen.size} ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Text(item.title, color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.explanation, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            val frac = (item.score / 3.0).toFloat().coerceIn(0.08f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(p.border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(p.unseenBorder)
                )
            }
        }
    }
}

@Composable
private fun AgentStatusStrip(dashboard: DashboardResponse) {
    val p = LocalVoxPalette.current
    val st = dashboard.agentStatus
    val dot = when (st.level) {
        "error" -> Color(0xFFEF4444)
        "warn", "busy" -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.border, RoundedCornerShape(14.dp))
            .background(p.card, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(
            text = "${st.source.uppercase()}: ${st.message}",
            color = p.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun McpNotesCard(
    dashboard: DashboardResponse,
    onDismissNote: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalVoxPalette.current
    Column(
        modifier = modifier
            .border(1.dp, p.mcpBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .background(p.mcpBg, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("✦ MCP // NOTES & REMINDERS", color = p.mcpBorder, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("${dashboard.notes.size}", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        if (dashboard.notes.isEmpty()) {
            Text("No active notes from agents.", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            dashboard.notes.take(3).forEach { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.cardAlt.copy(alpha = 0.85f))
                        .clickable { onDismissNote(note.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val prefix = if (note.kind == "reminder") "⏰ " else "› "
                    Text(
                        text = "$prefix${note.text}",
                        color = p.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    note.remindAt?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(it, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsCard(
    items: List<CapturedNotification>,
    activeFilter: String,
    mutedSources: Set<String>,
    onFilterChange: (String) -> Unit,
    onOpen: (CapturedNotification) -> Unit,
    onDismiss: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalVoxPalette.current
    val sources = listOf("ALL", "TG", "KSU24", "ZOOM")
    val listenerOn by com.vox.voxdock.notifications.NotificationStore.connected.collectAsState()

    Column(
        modifier = modifier
            .border(1.dp, p.border, RoundedCornerShape(16.dp))
            .background(p.card, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (listenerOn) Color(0xFF22C55E) else Color(0xFFF59E0B))
                )
                Text(
                    text = if (listenerOn) "INBOX" else "INBOX · RE-ENABLE ACCESS",
                    color = if (listenerOn) p.muted else Color(0xFFF59E0B),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                sources.forEach { src ->
                    val selected = activeFilter == src
                    val muted = mutedSources.contains(src)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) p.text else p.cardAlt)
                            .clickable { onFilterChange(src) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (muted) "$src✕" else src,
                            color = if (selected) p.bg else p.muted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Quiet — no unread notifications", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.key }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, p.border, RoundedCornerShape(10.dp))
                            .background(p.cardAlt, RoundedCornerShape(10.dp))
                            .clickable { onOpen(item) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "${item.source} · ${item.sender}",
                                color = sourceColor(item.source),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "🔇",
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable { onToggleMute(item.source) }
                                )
                                Text(
                                    text = "✕",
                                    color = p.muted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.clickable { onDismiss(item.key) }
                                )
                            }
                        }
                        Text(
                            text = item.text,
                            color = p.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun sourceColor(source: String): Color = when (source) {
    "TG" -> Color(0xFF38BDF8)
    "KSU24" -> Color(0xFF34D399)
    "ZOOM" -> Color(0xFF818CF8)
    else -> Color(0xFFA1A1AA)
}

private fun formatTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
