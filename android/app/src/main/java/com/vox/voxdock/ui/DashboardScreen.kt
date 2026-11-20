package com.vox.voxdock.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.vox.voxdock.data.*
import com.vox.voxdock.notifications.CapturedNotification
import com.vox.voxdock.notifications.MediaMonitor
import com.vox.voxdock.notifications.NotificationStore
import com.vox.voxdock.notifications.NowPlayingState
import com.vox.voxdock.ui.theme.LocalVoxPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    dashboard: DashboardResponse,
    settings: AppSettings,
    notifications: List<CapturedNotification>,
    nowPlaying: NowPlayingState,
    syncError: String?,
    updateAvailable: UpdatePayload?,
    updateProgress: Int?,
    isNightOled: Boolean,
    onToggleTheme: () -> Unit,
    onToggleDock: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenPower: () -> Unit,
    onOpenUnseen: () -> Unit,
    onOpenLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerUpdate: () -> Unit,
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
    val pages = settings.pages.ifEmpty { defaultDockPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    var activeSourceFilter by remember { mutableStateOf("ALL") }
    var previewNotif by remember { mutableStateOf<CapturedNotification?>(null) }

    val filteredNotifs = remember(notifications, settings.mutedSources, activeSourceFilter) {
        notifications.filter { n ->
            !settings.mutedSources.contains(n.source) &&
                (activeSourceFilter == "ALL" || n.source == activeSourceFilter)
        }
    }

    // Outer edge-to-edge container (so overlays like Preview dim the entire screen without cutout gaps)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onWakeInteraction() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Swipeable configurable screens
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { pageIndex ->
                val page = pages[pageIndex]
                val cols = if (isLandscape) 4 else 2

                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(
                        items = page.tiles,
                        key = { "${page.id}_${it.kind.name}" },
                        span = { tile ->
                            val clamped = tile.span.coerceIn(tile.kind.minSpan, tile.kind.maxSpan)
                            GridItemSpan(clamped.coerceAtMost(cols))
                        }
                    ) { tile ->
                        when (tile.kind) {
                            TileKind.CLOCK -> ClockTile(
                                clockText = clockText,
                                city = settings.city,
                                settings = settings,
                                syncError = syncError,
                                updateAvailable = updateAvailable,
                                updateProgress = updateProgress,
                                onToggleDock = onToggleDock,
                                onToggleTheme = onToggleTheme,
                                onOpenLayout = onOpenLayout,
                                onOpenSettings = onOpenSettings,
                                onTriggerUpdate = onTriggerUpdate
                            )
                            TileKind.ALERT -> AlertTile(
                                dashboard = dashboard,
                                onOpenMap = onOpenMap
                            )
                            TileKind.WEATHER -> WeatherTile(
                                dashboard = dashboard,
                                onOpenWeather = onOpenWeather
                            )
                            TileKind.POWER -> PowerTile(
                                dashboard = dashboard,
                                onOpenPower = onOpenPower
                            )
                            TileKind.CURRENCY -> CurrencyTile(currency = dashboard.currency)
                            TileKind.SUNSET_WAVES -> SunsetWavesTile(weather = dashboard.weather)
                            TileKind.AIR_SENSORS -> AirSensorsTile(sensors = dashboard.sensors)
                            TileKind.AGENT_STATUS -> AgentStatusStrip(dashboard = dashboard)
                            TileKind.SPOTIFY -> SpotifyTile(nowPlaying = nowPlaying)
                            TileKind.UNSEEN -> UnseenTile(
                                dashboard = dashboard,
                                onOpen = onOpenUnseen
                            )
                            TileKind.MCP_NOTES -> McpNotesCard(
                                dashboard = dashboard,
                                onDismissNote = onDismissNote
                            )
                            TileKind.ACTA_TASKS -> ActaTasksTile(acta = dashboard.acta)
                            TileKind.INBOX -> NotificationsCard(
                                items = filteredNotifs,
                                activeFilter = activeSourceFilter,
                                mutedSources = settings.mutedSources,
                                onFilterChange = { activeSourceFilter = it },
                                onOpen = { previewNotif = it },
                                onDismiss = onDismissNotification,
                                onToggleMute = onToggleMuteSource
                            )
                        }
                    }
                }
            }

            // Bottom bar: roaming pet + subtle page dots
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                if (settings.petEnabled) {
                    RoamingPetStrip(
                        alertLevel = dashboard.alerts.selected.level,
                        powerStatus = dashboard.power.status,
                        isPlayingMusic = nowPlaying.isPlaying
                    )
                }
                if (pages.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pages.forEachIndexed { idx, _ ->
                            val active = idx == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(if (active) 6.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(if (active) p.text else p.muted.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            }
        }

        // Edge-to-edge silent message preview popup (no cutout gap on the left)
        previewNotif?.let { item ->
            NotificationPreviewDialog(
                item = item,
                onOpenApp = {
                    previewNotif = null
                    onOpenNotification(item)
                },
                onDismiss = {
                    previewNotif = null
                    onDismissNotification(item.key)
                },
                onClose = { previewNotif = null }
            )
        }
    }
}

@Composable
private fun ClockTile(
    clockText: String,
    city: String,
    settings: AppSettings,
    syncError: String?,
    updateAvailable: UpdatePayload?,
    updateProgress: Int?,
    onToggleDock: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenLayout: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerUpdate: () -> Unit
) {
    val p = LocalVoxPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.border, RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (syncError == null) Color(0xFF22C55E) else Color(0xFFF59E0B))
                )
                Text(
                    text = "VOXDOCK // ${city.uppercase()}",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = clockText,
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (updateAvailable != null) {
                val label = if (updateProgress != null) "↓$updateProgress%" else "↑${updateAvailable.version}"
                SmallPill(label = label, highlighted = true, onClick = onTriggerUpdate)
            }
            SmallPill(label = if (settings.dockMode) "☾ ON" else "☾", highlighted = settings.dockMode, onClick = onToggleDock)
            SmallPill(label = if (settings.darkTheme) "☀" else "☾", highlighted = false, onClick = onToggleTheme)
            SmallPill(label = "⊞", highlighted = false, onClick = onOpenLayout)
            SmallPill(label = "⚙", highlighted = false, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun AlertTile(dashboard: DashboardResponse, onOpenMap: () -> Unit) {
    val p = LocalVoxPalette.current
    val sel = dashboard.alerts.selected
    val (bg, border, txt, label) = when (sel.level) {
        "active" -> Quad(p.alertActiveBg, p.alertActiveBorder, p.alertActiveText, "ALERT 🚨")
        "partial" -> Quad(p.alertPartialBg, p.alertPartialBorder, p.alertPartialText, "PARTIAL ⚠")
        else -> Quad(p.alertNoneBg, p.alertNoneBorder, p.alertNoneText, "CLEAR ✓")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .background(bg, RoundedCornerShape(7.dp))
            .clickable { onOpenMap() }
            .padding(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AIR RAID", color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text("MAP ↗", color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Text(label, color = txt, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(sel.name, color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun WeatherTile(dashboard: DashboardResponse, onOpenWeather: () -> Unit) {
    val p = LocalVoxPalette.current
    val cur = dashboard.weather.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.weatherBorder.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .background(p.weatherBg, RoundedCornerShape(7.dp))
            .clickable { onOpenWeather() }
            .padding(9.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(weatherIcon(cur.weatherCode), fontSize = 13.sp)
            Text("10D ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Text("${cur.temp}°C", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val seaTxt = cur.seaTemp?.let { "sea ${it}°" } ?: "${cur.windSpeed} km/h"
        Text(seaTxt, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun PowerTile(dashboard: DashboardResponse, onOpenPower: () -> Unit) {
    val p = LocalVoxPalette.current
    val pw = dashboard.power
    val dot = when (pw.status) {
        "off" -> Color(0xFFEF4444)
        "possible" -> Color(0xFFF59E0B)
        "on" -> Color(0xFF22C55E)
        else -> p.muted
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.powerBorder.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.powerBg, RoundedCornerShape(7.dp))
            .clickable { onOpenPower() }
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                Text("⚡ DTEK", color = p.powerBorder, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
            Text(pw.nextChange ?: "24H ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Text(
            text = pw.summary,
            color = p.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CurrencyTile(currency: CurrencyPayload?) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.border, RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("MONO FX", color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        if (currency == null) {
            Text("Loading...", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            Text(
                text = "USD ${"%.2f".format(currency.usdBuy)}/${"%.2f".format(currency.usdSell)}",
                color = Color(0xFF22C55E),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1
            )
            Text(
                text = "EUR ${"%.2f".format(currency.eurBuy)}/${"%.2f".format(currency.eurSell)}",
                color = Color(0xFF38BDF8),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SunsetWavesTile(weather: WeatherPayload) {
    val p = LocalVoxPalette.current
    val cur = weather.current
    val sunsetText = remember(cur.sunset) { computeSunsetCountdown(cur.sunset) }
    val waveText = cur.waveHeight?.let {
        val state = when {
            it >= 1.8 -> "STORM"
            it >= 0.8 -> "CHOPPY"
            else -> "CALM"
        }
        "🌊 ${"%.1f".format(it)}m $state"
    } ?: "🌊 --"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("SUN & SEA", color = Color(0xFFF59E0B), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        Text(sunsetText, color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
        Text(waveText, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun AirSensorsTile(sensors: SensorsPayload?) {
    val p = LocalVoxPalette.current
    val aqiColor = when {
        sensors == null -> p.muted
        sensors.aqi <= 40 -> Color(0xFF22C55E)
        sensors.aqi <= 60 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, aqiColor.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AIR SENSORS", color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text(sensors?.label ?: "--", color = aqiColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        if (sensors == null) {
            Text("No sensor data", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            Text(
                text = "AQI ${sensors.aqi} · PM2.5 ${sensors.pm25} · PM10 ${sensors.pm10} · Dust ${sensors.dust}",
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpotifyTile(nowPlaying: NowPlayingState) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1DB954).copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "♫ ${nowPlaying.appLabel.ifBlank { "SPOTIFY / MEDIA" }}",
                color = Color(0xFF1DB954),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
            Text(
                text = if (nowPlaying.isPlaying) "PLAYING" else "PAUSED",
                color = p.muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
        Text(
            text = if (nowPlaying.hasSession) nowPlaying.title else "Start Spotify or YT Music",
            color = p.text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (nowPlaying.artist.isNotBlank()) {
            Text(
                text = nowPlaying.artist,
                color = p.muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallPill("⏮", false) { MediaMonitor.previous() }
            SmallPill(if (nowPlaying.isPlaying) "⏸ PAUSE" else "▶ PLAY", nowPlaying.isPlaying) { MediaMonitor.togglePlayPause() }
            SmallPill("⏭", false) { MediaMonitor.next() }
        }
    }
}

@Composable
private fun ActaTasksTile(acta: ActaPayload) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ACTA // MY TASKS", color = Color(0xFF818CF8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("${acta.count}", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        if (!acta.configured) {
            Text("ACTA MCP not configured", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else if (acta.tasks.isEmpty()) {
            Text("No open tasks found in ACTA", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            acta.tasks.take(3).forEach { t ->
                val idPrefix = if (t.identifier.isNotBlank()) "${t.identifier} · " else "• "
                Text(
                    text = "$idPrefix${t.title}",
                    color = p.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UnseenTile(dashboard: DashboardResponse, onOpen: () -> Unit) {
    val p = LocalVoxPalette.current
    val item = dashboard.unseen.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.unseenBorder.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.unseenBg, RoundedCornerShape(7.dp))
            .clickable { onOpen() }
            .padding(9.dp)
    ) {
        if (item == null) {
            Text("◈ UNSEEN · QUIET", color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("Nothing unusual nearby right now.", color = p.text.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("◈ UNSEEN · ${item.category.uppercase()}", color = p.unseenBorder, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                Text("score ${"%.1f".format(item.score)} · ${dashboard.unseen.size} ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Text(item.title, color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.explanation, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val frac = (item.score / 3.0).toFloat().coerceIn(0.08f, 1f)
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.border)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(p.unseenBorder))
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
            .border(1.dp, p.border, RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
private fun McpNotesCard(dashboard: DashboardResponse, onDismissNote: (Int) -> Unit) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.mcpBorder.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.mcpBg, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
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
                        .clip(RoundedCornerShape(5.dp))
                        .background(p.cardAlt.copy(alpha = 0.85f))
                        .clickable { onDismissNote(note.id) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val prefix = if (note.kind == "reminder") "⏰ " else "› "
                    Text(
                        text = "$prefix${note.text}",
                        color = p.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
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
    onToggleMute: (String) -> Unit
) {
    val p = LocalVoxPalette.current
    val sources = listOf("ALL", "TG", "KSU24", "ZOOM")
    val listenerOn by NotificationStore.connected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, p.border, RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    text = if (listenerOn) "INBOX" else "INBOX · ACCESS",
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
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (muted) "$src✕" else src,
                            color = if (selected) p.bg else p.muted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Text("Quiet — no unread notifications", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            items.take(4).forEach { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, p.border, RoundedCornerShape(5.dp))
                        .background(p.cardAlt, RoundedCornerShape(5.dp))
                        .clickable { onOpen(item) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
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
                            Text("🔇", fontSize = 9.sp, modifier = Modifier.clickable { onToggleMute(item.source) })
                            Text("✕", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.clickable { onDismiss(item.key) })
                        }
                    }
                    Text(
                        text = item.text,
                        color = p.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NightOledScreen(clockText: String, alertLevel: String, onWake: () -> Unit) {
    val dotColor = if (alertLevel == "partial") Color(0xFFF59E0B) else Color(0xFF166534)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onWake() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(clockText, color = Color(0xFF52525B), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 64.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
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
internal fun SmallPill(label: String, highlighted: Boolean, onClick: () -> Unit) {
    val p = LocalVoxPalette.current
    Box(
        modifier = Modifier
            .border(1.dp, if (highlighted) Color(0xFF6366F1) else p.border, RoundedCornerShape(5.dp))
            .background(if (highlighted) Color(0xFF6366F1) else p.cardAlt, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
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
private fun NotificationPreviewDialog(
    item: CapturedNotification,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current
    val timeStr = remember(item.timestampMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestampMs))
    }
    val lines = item.previewLines.ifEmpty { listOf(item.text) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable { onClose() }
            .safeDrawingPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(1.dp, sourceColor(item.source).copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                .background(p.card, RoundedCornerShape(7.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.source} · ${item.sender}",
                    color = sourceColor(item.source),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("$timeStr · UNREAD PREVIEW", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lines.forEach { line ->
                    Text(line, color = p.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                SmallPill(label = "CLOSE", highlighted = false, onClick = onClose)
                SmallPill(label = "DISMISS", highlighted = false, onClick = onDismiss)
                SmallPill(label = "OPEN CHAT ↗", highlighted = true, onClick = onOpenApp)
            }
        }
    }
}

private fun computeSunsetCountdown(sunsetIso: String?): String {
    if (sunsetIso.isNullOrBlank()) return "☀ Sunset --"
    return runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val sunsetDate = fmt.parse(sunsetIso.take(16)) ?: return "☀ Sunset --"
        val diffMin = ((sunsetDate.time - System.currentTimeMillis()) / 60_000L).toInt()
        when {
            diffMin > 60 -> "🌇 Sunset in ${diffMin / 60}h ${diffMin % 60}m"
            diffMin > 0 -> "🌇 Sunset in ${diffMin}m"
            else -> "🌙 Sun set at ${sunsetIso.substringAfter('T').take(5)}"
        }
    }.getOrDefault("☀ Sunset --")
}

private fun sourceColor(source: String): Color = when (source) {
    "TG" -> Color(0xFF38BDF8)
    "KSU24" -> Color(0xFF34D399)
    "ZOOM" -> Color(0xFF818CF8)
    else -> Color(0xFFA1A1AA)
}

private fun formatTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
