package com.vox.voxdock.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vox.voxdock.data.*
import com.vox.voxdock.notifications.CapturedNotification
import com.vox.voxdock.notifications.MediaMonitor
import com.vox.voxdock.notifications.NotificationStore
import com.vox.voxdock.notifications.NowPlayingState
import com.vox.voxdock.ui.theme.LocalVoxPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
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
    onOpenActa: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerUpdate: () -> Unit,
    onUpdatePages: (List<DockPage>) -> Unit,
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

    // Direct in-place Edit Mode (triggered by long-pressing any tile)
    var editMode by remember { mutableStateOf(false) }
    var showAddTilePicker by remember { mutableStateOf(false) }

    val filteredNotifs = remember(notifications, settings.mutedSources, activeSourceFilter) {
        notifications.filter { n ->
            !settings.mutedSources.contains(n.source) &&
                (activeSourceFilter == "ALL" || n.source == activeSourceFilter)
        }
    }

    fun mutatePage(pageIndex: Int, transform: (DockPage) -> DockPage) {
        val next = pages.mapIndexed { i, pg -> if (i == pageIndex) transform(pg) else pg }
        onUpdatePages(next)
    }

    // Outer edge-to-edge container
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
            // Compact in-place Edit Mode toolbar (only visible while editing)
            if (editMode) {
                val curIdx = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .border(1.dp, Color(0xFF6366F1), RoundedCornerShape(6.dp))
                        .background(p.card, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EDIT · DRAG TO MOVE · EDGE ⋮ TO RESIZE",
                        color = Color(0xFF818CF8),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SmallPill("+ TILE", false) { showAddTilePicker = true }
                        if (pages.size < 5) {
                            SmallPill("+ PAGE", false) {
                                val next = pages + DockPage(
                                    id = "p_${System.currentTimeMillis()}",
                                    name = "Page ${pages.size + 1}",
                                    tiles = listOf(DockTile(TileKind.CLOCK, 2))
                                )
                                onUpdatePages(next)
                            }
                        }
                        if (pages.size > 1) {
                            SmallPill("DEL PG", false) {
                                onUpdatePages(pages.filterIndexed { i, _ -> i != curIdx })
                            }
                        }
                        SmallPill("DONE", true) { editMode = false }
                    }
                }
            }

            // Swipeable screens (disable swipe while dragging tiles in editMode)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !editMode,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
                val cols = if (isLandscape) 4 else 2

                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = page.tiles,
                        key = { _, tile -> "${page.id}_${tile.kind.name}" },
                        span = { _, tile ->
                            val clamped = tile.span.coerceIn(tile.kind.minSpan, tile.kind.maxSpan)
                            GridItemSpan(clamped.coerceAtMost(cols))
                        }
                    ) { tileIdx, tile ->
                        var dragX by remember(tile.kind) { mutableFloatStateOf(0f) }
                        var dragY by remember(tile.kind) { mutableFloatStateOf(0f) }
                        var isDragging by remember(tile.kind) { mutableStateOf(false) }
                        var resizeAccum by remember(tile.kind) { mutableFloatStateOf(0f) }
                        val canResize = tile.kind.minSpan != tile.kind.maxSpan

                        Box(
                            modifier = Modifier
                                .zIndex(if (isDragging) 10f else 0f)
                                .graphicsLayer {
                                    translationX = dragX
                                    translationY = dragY
                                    scaleX = if (isDragging) 1.03f else 1f
                                    scaleY = if (isDragging) 1.03f else 1f
                                    alpha = if (isDragging) 0.92f else 1f
                                }
                                .then(
                                    if (editMode) {
                                        Modifier
                                            .border(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.85f), RoundedCornerShape(7.dp))
                                            .pointerInput(page.tiles, tileIdx) {
                                                detectDragGestures(
                                                    onDragStart = { isDragging = true },
                                                    onDragEnd = {
                                                        isDragging = false
                                                        dragX = 0f
                                                        dragY = 0f
                                                    },
                                                    onDragCancel = {
                                                        isDragging = false
                                                        dragX = 0f
                                                        dragY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragX += dragAmount.x
                                                        dragY += dragAmount.y
                                                        val thresholdX = 90f
                                                        val thresholdY = 75f
                                                        val targetIdx = when {
                                                            dragX > thresholdX -> (tileIdx + 1).coerceAtMost(page.tiles.lastIndex)
                                                            dragX < -thresholdX -> (tileIdx - 1).coerceAtLeast(0)
                                                            dragY > thresholdY -> (tileIdx + 2).coerceAtMost(page.tiles.lastIndex)
                                                            dragY < -thresholdY -> (tileIdx - 2).coerceAtLeast(0)
                                                            else -> tileIdx
                                                        }
                                                        if (targetIdx != tileIdx) {
                                                            dragX = 0f
                                                            dragY = 0f
                                                            mutatePage(pageIndex) { pg ->
                                                                val list = pg.tiles.toMutableList()
                                                                val moved = list.removeAt(tileIdx)
                                                                list.add(targetIdx, moved)
                                                                pg.copy(tiles = list)
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                    } else {
                                        Modifier.combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onWakeInteraction() },
                                            onLongClick = { editMode = true }
                                        )
                                    }
                                )
                        ) {
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
                                    onToggleEdit = { editMode = !editMode },
                                    onOpenSettings = onOpenSettings,
                                    onTriggerUpdate = onTriggerUpdate
                                )
                                TileKind.ALERT -> AlertTile(
                                    dashboard = dashboard,
                                    onOpenMap = { if (!editMode) onOpenMap() }
                                )
                                TileKind.WEATHER -> WeatherTile(
                                    dashboard = dashboard,
                                    onOpenWeather = { if (!editMode) onOpenWeather() }
                                )
                                TileKind.POWER -> PowerTile(
                                    dashboard = dashboard,
                                    onOpenPower = { if (!editMode) onOpenPower() }
                                )
                                TileKind.CURRENCY -> CurrencyTile(currency = dashboard.currency)
                                TileKind.SUNSET_WAVES -> SunsetWavesTile(weather = dashboard.weather)
                                TileKind.AIR_SENSORS -> AirSensorsTile(sensors = dashboard.sensors)
                                TileKind.AGENT_STATUS -> AgentStatusStrip(dashboard = dashboard)
                                TileKind.SPOTIFY -> SpotifyTile(nowPlaying = nowPlaying)
                                TileKind.UNSEEN -> UnseenTile(
                                    dashboard = dashboard,
                                    onOpen = { if (!editMode) onOpenUnseen() }
                                )
                                TileKind.MCP_NOTES -> McpNotesCard(
                                    dashboard = dashboard,
                                    onDismissNote = { if (!editMode) onDismissNote(it) }
                                )
                                TileKind.ACTA_TASKS -> ActaTasksTile(
                                    acta = dashboard.acta,
                                    onOpen = { if (!editMode) onOpenActa() }
                                )
                                TileKind.INBOX -> NotificationsCard(
                                    items = filteredNotifs,
                                    activeFilter = activeSourceFilter,
                                    mutedSources = settings.mutedSources,
                                    onFilterChange = { activeSourceFilter = it },
                                    onOpen = { if (!editMode) previewNotif = it },
                                    onDismiss = onDismissNotification,
                                    onToggleMute = onToggleMuteSource
                                )
                            }

                            // Edit Mode overlays: remove (top-right) & drag-to-resize handle (right edge)
                            if (editMode) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFEF4444))
                                        .clickable {
                                            mutatePage(pageIndex) { pg ->
                                                pg.copy(tiles = pg.tiles.filterIndexed { i, _ -> i != tileIdx })
                                            }
                                        }
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("✕", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }

                                if (canResize) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                            .width(20.dp)
                                            .background(Color(0xFF6366F1).copy(alpha = 0.25f), RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp))
                                            .pointerInput(tile.span, tileIdx) {
                                                detectHorizontalDragGestures(
                                                    onDragEnd = { resizeAccum = 0f },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.consume()
                                                        resizeAccum += dragAmount
                                                        if (resizeAccum > 55f) {
                                                            resizeAccum = 0f
                                                            val next = (if (tile.span == 2) 4 else tile.span + 1)
                                                                .coerceAtMost(tile.kind.maxSpan)
                                                            if (next != tile.span) {
                                                                mutatePage(pageIndex) { pg ->
                                                                    pg.copy(tiles = pg.tiles.mapIndexed { i, t ->
                                                                        if (i == tileIdx) t.copy(span = next) else t
                                                                    })
                                                                }
                                                            }
                                                        } else if (resizeAccum < -55f) {
                                                            resizeAccum = 0f
                                                            val next = (if (tile.span == 4) 2 else tile.span - 1)
                                                                .coerceAtLeast(tile.kind.minSpan)
                                                            if (next != tile.span) {
                                                                mutatePage(pageIndex) { pg ->
                                                                    pg.copy(tiles = pg.tiles.mapIndexed { i, t ->
                                                                        if (i == tileIdx) t.copy(span = next) else t
                                                                    })
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⋮", color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Page indicator dots (tiny, floating bottom-center, takes 0dp from grid)
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { idx, _ ->
                    val active = idx == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (active) p.text else p.muted.copy(alpha = 0.45f))
                    )
                }
            }
        }

        // Pixel-art pet floating along the very bottom edge (0dp taken from grid height!)
        if (settings.petEnabled) {
            RoamingPetOverlay(
                alertLevel = dashboard.alerts.selected.level,
                powerStatus = dashboard.power.status,
                isPlayingMusic = nowPlaying.isPlaying,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // Quick Add Tile modal in Edit Mode
        if (showAddTilePicker) {
            val curIdx = pagerState.currentPage.coerceIn(0, pages.lastIndex)
            val existing = pages[curIdx].tiles.map { it.kind }.toSet()
            val available = TileKind.entries.filterNot { existing.contains(it) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showAddTilePicker = false }
                    .safeDrawingPadding()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .border(1.dp, p.border, RoundedCornerShape(7.dp))
                        .background(p.card, RoundedCornerShape(7.dp))
                        .clickable(enabled = false) {}
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ADD TILE TO SCREEN", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        SmallPill("✕", false) { showAddTilePicker = false }
                    }
                    if (available.isEmpty()) {
                        Text("All tiles are already on this screen.", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            available.forEach { kind ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, p.border, RoundedCornerShape(5.dp))
                                        .background(p.cardAlt, RoundedCornerShape(5.dp))
                                        .clickable {
                                            mutatePage(curIdx) { pg ->
                                                pg.copy(tiles = pg.tiles + DockTile(kind, kind.defaultSpan))
                                            }
                                            showAddTilePicker = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("+ ${kind.title}", color = p.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    Text("span ${kind.minSpan}..${kind.maxSpan}", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Edge-to-edge silent message preview popup
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
    onToggleEdit: () -> Unit,
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
            SmallPill(label = "⊞", highlighted = false, onClick = onToggleEdit)
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
        // Full-width playback controls bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FullWidthMediaBtn("⏮", false, Modifier.weight(1f)) { MediaMonitor.previous() }
            FullWidthMediaBtn(
                label = if (nowPlaying.isPlaying) "⏸ PAUSE" else "▶ PLAY",
                highlighted = nowPlaying.isPlaying,
                modifier = Modifier.weight(1.6f)
            ) { MediaMonitor.togglePlayPause() }
            FullWidthMediaBtn("⏭", false, Modifier.weight(1f)) { MediaMonitor.next() }
        }
    }
}

@Composable
private fun FullWidthMediaBtn(
    label: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LocalVoxPalette.current
    Box(
        modifier = modifier
            .border(1.dp, if (highlighted) Color(0xFF1DB954) else p.border, RoundedCornerShape(5.dp))
            .background(if (highlighted) Color(0xFF1DB954).copy(alpha = 0.25f) else p.cardAlt, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) Color(0xFF4ADE80) else p.text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ActaTasksTile(acta: ActaPayload, onOpen: () -> Unit) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .clickable { onOpen() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ACTA // MY TASKS", color = Color(0xFF818CF8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("${acta.count} ↗", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
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
                            .clip(RoundedCornerShape(4.dp))
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
