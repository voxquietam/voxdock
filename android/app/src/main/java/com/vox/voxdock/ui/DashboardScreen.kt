package com.vox.voxdock.ui

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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

private const val GRID_COLS = 4
private const val GRID_ROWS = 3

private fun tilesOverlap(a: DockTile, b: DockTile): Boolean {
    return a.col < b.col + b.colSpan &&
        a.col + a.colSpan > b.col &&
        a.row < b.row + b.rowSpan &&
        a.row + a.rowSpan > b.row
}

private fun fitsInGrid(candidate: DockTile, others: List<DockTile>): Boolean {
    if (candidate.col < 0 || candidate.row < 0) return false
    if (candidate.col + candidate.colSpan > GRID_COLS) return false
    if (candidate.row + candidate.rowSpan > GRID_ROWS) return false
    return others.none { tilesOverlap(candidate, it) }
}

private fun findFreeCell(tiles: List<DockTile>, colSpan: Int, rowSpan: Int): Pair<Int, Int>? {
    for (r in 0..(GRID_ROWS - rowSpan)) {
        for (c in 0..(GRID_COLS - colSpan)) {
            val probe = DockTile(TileKind.CLOCK, col = c, row = r, colSpan = colSpan, rowSpan = rowSpan)
            if (fitsInGrid(probe, tiles)) return c to r
        }
    }
    return null
}

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

    // 2D Bento Edit Mode state
    var editMode by remember { mutableStateOf(false) }
    var selectedKind by remember { mutableStateOf<TileKind?>(null) }
    var addTargetCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }

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
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Unified clean top Edit Bar (never overlaps tiles!)
            if (editMode) {
                val curIdx = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                val curPage = pages[curIdx]
                val selTile = curPage.tiles.firstOrNull { it.kind == selectedKind }

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
                    if (selTile != null) {
                        val others = curPage.tiles.filterNot { it.kind == selTile.kind }
                        val canWMinus = selTile.colSpan > selTile.kind.minColSpan
                        val canWPlus = selTile.colSpan < selTile.kind.maxColSpan &&
                            fitsInGrid(selTile.copy(colSpan = selTile.colSpan + 1), others)
                        val canHMinus = selTile.rowSpan > selTile.kind.minRowSpan
                        val canHPlus = selTile.rowSpan < selTile.kind.maxRowSpan &&
                            fitsInGrid(selTile.copy(rowSpan = selTile.rowSpan + 1), others)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${selTile.kind.title.uppercase()} (${selTile.colSpan}×${selTile.rowSpan})",
                                color = Color(0xFF4ADE80),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            if (canWMinus) SmallPill("W-", false) {
                                mutatePage(curIdx) { pg ->
                                    pg.copy(tiles = pg.tiles.map { if (it.kind == selTile.kind) it.copy(colSpan = it.colSpan - 1) else it })
                                }
                            }
                            if (canWPlus) SmallPill("W+", false) {
                                mutatePage(curIdx) { pg ->
                                    pg.copy(tiles = pg.tiles.map { if (it.kind == selTile.kind) it.copy(colSpan = it.colSpan + 1) else it })
                                }
                            }
                            if (canHMinus) SmallPill("H-", false) {
                                mutatePage(curIdx) { pg ->
                                    pg.copy(tiles = pg.tiles.map { if (it.kind == selTile.kind) it.copy(rowSpan = it.rowSpan - 1) else it })
                                }
                            }
                            if (canHPlus) SmallPill("H+", false) {
                                mutatePage(curIdx) { pg ->
                                    pg.copy(tiles = pg.tiles.map { if (it.kind == selTile.kind) it.copy(rowSpan = it.rowSpan + 1) else it })
                                }
                            }
                            SmallPill("DEL", false) {
                                selectedKind = null
                                mutatePage(curIdx) { pg ->
                                    pg.copy(tiles = pg.tiles.filterNot { it.kind == selTile.kind })
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "EDIT 4×3 · TAP TILE TO RESIZE/SWAP · TAP + TO ADD",
                            color = Color(0xFF818CF8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (pages.size < 5) {
                            SmallPill("+PG", false) {
                                onUpdatePages(
                                    pages + DockPage(
                                        id = "p_${System.currentTimeMillis()}",
                                        name = "Page ${pages.size + 1}",
                                        tiles = listOf(DockTile(TileKind.CLOCK, 0, 0, 2, 1))
                                    )
                                )
                            }
                        }
                        if (pages.size > 1) {
                            SmallPill("-PG", false) {
                                selectedKind = null
                                onUpdatePages(pages.filterIndexed { i, _ -> i != curIdx })
                            }
                        }
                        SmallPill("RESET", false) {
                            selectedKind = null
                            onUpdatePages(defaultDockPages())
                        }
                        SmallPill("DONE", true) {
                            editMode = false
                            selectedKind = null
                        }
                    }
                }
            }

            // Swipeable full-height 2D Bento screens
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !editMode,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager

                if (isLandscape) {
                    // TRUE 4x3 2D BENTO GRID — fills 100% of screen height with zero uneven heights
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val gap = 7.dp
                        val cellW = (maxWidth - gap * (GRID_COLS - 1)) / GRID_COLS
                        val cellH = (maxHeight - gap * (GRID_ROWS - 1)) / GRID_ROWS

                        // Empty grid slots visible in Edit Mode (tap to move selected tile or add new tile)
                        if (editMode) {
                            for (r in 0 until GRID_ROWS) {
                                for (c in 0 until GRID_COLS) {
                                    val occupied = page.tiles.any {
                                        c >= it.col && c < it.col + it.colSpan &&
                                            r >= it.row && r < it.row + it.rowSpan
                                    }
                                    if (!occupied) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = (cellW + gap) * c, y = (cellH + gap) * r)
                                                .size(width = cellW, height = cellH)
                                                .border(1.dp, p.border, RoundedCornerShape(7.dp))
                                                .background(p.card.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                                                .clickable {
                                                    val sel = page.tiles.firstOrNull { it.kind == selectedKind }
                                                    if (sel != null) {
                                                        // Move selected tile to this empty cell (shrinking span if needed to fit)
                                                        val others = page.tiles.filterNot { it.kind == sel.kind }
                                                        val moved = sel.copy(
                                                            col = c,
                                                            row = r,
                                                            colSpan = sel.colSpan.coerceAtMost(GRID_COLS - c),
                                                            rowSpan = sel.rowSpan.coerceAtMost(GRID_ROWS - r)
                                                        )
                                                        if (fitsInGrid(moved, others)) {
                                                            mutatePage(pageIndex) { pg ->
                                                                pg.copy(tiles = others + moved)
                                                            }
                                                            selectedKind = null
                                                        }
                                                    } else {
                                                        addTargetCell = c to r
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (selectedKind != null) "MOVE HERE" else "+",
                                                color = p.muted,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (selectedKind != null) 9.sp else 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Render each tile locked to its exact 2D rectangle (col, row, colSpan, rowSpan)
                        page.tiles.forEach { tile ->
                            val tileW = cellW * tile.colSpan + gap * (tile.colSpan - 1)
                            val tileH = cellH * tile.rowSpan + gap * (tile.rowSpan - 1)
                            val isSelected = editMode && selectedKind == tile.kind

                            Box(
                                modifier = Modifier
                                    .offset(x = (cellW + gap) * tile.col, y = (cellH + gap) * tile.row)
                                    .size(width = tileW, height = tileH)
                                    .then(
                                        if (editMode) {
                                            Modifier
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF6366F1).copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(7.dp)
                                                )
                                                .clickable {
                                                    val prev = page.tiles.firstOrNull { it.kind == selectedKind }
                                                    if (prev == null) {
                                                        selectedKind = tile.kind
                                                    } else if (prev.kind == tile.kind) {
                                                        selectedKind = null
                                                    } else {
                                                        // Swap positions & sizes cleanly so layout never breaks
                                                        mutatePage(pageIndex) { pg ->
                                                            pg.copy(tiles = pg.tiles.map { cur ->
                                                                when (cur.kind) {
                                                                    prev.kind -> prev.copy(
                                                                        col = tile.col,
                                                                        row = tile.row,
                                                                        colSpan = tile.colSpan.coerceIn(prev.kind.minColSpan, prev.kind.maxColSpan),
                                                                        rowSpan = tile.rowSpan.coerceIn(prev.kind.minRowSpan, prev.kind.maxRowSpan)
                                                                    )
                                                                    tile.kind -> tile.copy(
                                                                        col = prev.col,
                                                                        row = prev.row,
                                                                        colSpan = prev.colSpan.coerceIn(tile.kind.minColSpan, tile.kind.maxColSpan),
                                                                        rowSpan = prev.rowSpan.coerceIn(tile.kind.minRowSpan, tile.kind.maxRowSpan)
                                                                    )
                                                                    else -> cur
                                                                }
                                                            })
                                                        }
                                                        selectedKind = null
                                                    }
                                                }
                                        } else {
                                            Modifier.combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onWakeInteraction() },
                                                onLongClick = {
                                                    editMode = true
                                                    selectedKind = tile.kind
                                                }
                                            )
                                        }
                                    )
                            ) {
                                RenderTileContent(
                                    tile = tile,
                                    clockText = clockText,
                                    dashboard = dashboard,
                                    settings = settings,
                                    notifications = filteredNotifs,
                                    nowPlaying = nowPlaying,
                                    syncError = syncError,
                                    updateAvailable = updateAvailable,
                                    updateProgress = updateProgress,
                                    activeSourceFilter = activeSourceFilter,
                                    editMode = editMode,
                                    onToggleDock = onToggleDock,
                                    onToggleTheme = onToggleTheme,
                                    onToggleEdit = {
                                        editMode = !editMode
                                        if (!editMode) selectedKind = null
                                    },
                                    onOpenMap = onOpenMap,
                                    onOpenWeather = onOpenWeather,
                                    onOpenPower = onOpenPower,
                                    onOpenUnseen = onOpenUnseen,
                                    onOpenActa = onOpenActa,
                                    onOpenSettings = onOpenSettings,
                                    onTriggerUpdate = onTriggerUpdate,
                                    onDismissNote = onDismissNote,
                                    onFilterChange = { activeSourceFilter = it },
                                    onOpenPreview = { previewNotif = it },
                                    onDismissNotification = onDismissNotification,
                                    onToggleMuteSource = onToggleMuteSource
                                )
                            }
                        }
                    }
                } else {
                    // Portrait: adaptive equal-height scrollable stack of the same tiles
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        page.tiles.sortedWith(compareBy({ it.row }, { it.col })).forEach { tile ->
                            val heightDp = when {
                                tile.rowSpan >= 2 -> 175.dp
                                tile.kind == TileKind.INBOX || tile.kind == TileKind.SPOTIFY || tile.kind == TileKind.ACTA_TASKS -> 140.dp
                                else -> 88.dp
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(heightDp)) {
                                RenderTileContent(
                                    tile = tile,
                                    clockText = clockText,
                                    dashboard = dashboard,
                                    settings = settings,
                                    notifications = filteredNotifs,
                                    nowPlaying = nowPlaying,
                                    syncError = syncError,
                                    updateAvailable = updateAvailable,
                                    updateProgress = updateProgress,
                                    activeSourceFilter = activeSourceFilter,
                                    editMode = editMode,
                                    onToggleDock = onToggleDock,
                                    onToggleTheme = onToggleTheme,
                                    onToggleEdit = { editMode = !editMode },
                                    onOpenMap = onOpenMap,
                                    onOpenWeather = onOpenWeather,
                                    onOpenPower = onOpenPower,
                                    onOpenUnseen = onOpenUnseen,
                                    onOpenActa = onOpenActa,
                                    onOpenSettings = onOpenSettings,
                                    onTriggerUpdate = onTriggerUpdate,
                                    onDismissNote = onDismissNote,
                                    onFilterChange = { activeSourceFilter = it },
                                    onOpenPreview = { previewNotif = it },
                                    onDismissNotification = onDismissNotification,
                                    onToggleMuteSource = onToggleMuteSource
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tiny floating page dots
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
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

        // Pixel-art pet floating along the very bottom edge (0dp taken from grid)
        if (settings.petEnabled) {
            RoamingPetOverlay(
                petType = settings.petType,
                alertLevel = dashboard.alerts.selected.level,
                powerStatus = dashboard.power.status,
                isPlayingMusic = nowPlaying.isPlaying,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // Add tile picker when tapping an empty '+' cell in Edit Mode
        addTargetCell?.let { (targetCol, targetRow) ->
            val curIdx = pagerState.currentPage.coerceIn(0, pages.lastIndex)
            val curPage = pages[curIdx]
            val existing = curPage.tiles.map { it.kind }.toSet()
            val available = TileKind.entries.filterNot { existing.contains(it) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { addTargetCell = null }
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
                        Text("ADD TILE AT ($targetCol, $targetRow)", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        SmallPill("✕", false) { addTargetCell = null }
                    }
                    if (available.isEmpty()) {
                        Text("All tiles are already on this page.", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            available.forEach { kind ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, p.border, RoundedCornerShape(5.dp))
                                        .background(p.cardAlt, RoundedCornerShape(5.dp))
                                        .clickable {
                                            // Fit at target cell, falling back to smallest allowed span or first free slot
                                            val direct = DockTile(
                                                kind = kind,
                                                col = targetCol,
                                                row = targetRow,
                                                colSpan = kind.defaultColSpan.coerceAtMost(GRID_COLS - targetCol).coerceAtLeast(1),
                                                rowSpan = kind.defaultRowSpan.coerceAtMost(GRID_ROWS - targetRow).coerceAtLeast(1)
                                            )
                                            val minimal = DockTile(kind = kind, col = targetCol, row = targetRow, colSpan = 1, rowSpan = 1)
                                            val chosen = when {
                                                fitsInGrid(direct, curPage.tiles) -> direct
                                                fitsInGrid(minimal, curPage.tiles) -> minimal
                                                else -> findFreeCell(curPage.tiles, 1, 1)?.let { (fc, fr) ->
                                                    DockTile(kind, fc, fr, 1, 1)
                                                }
                                            }
                                            if (chosen != null) {
                                                mutatePage(curIdx) { pg -> pg.copy(tiles = pg.tiles + chosen) }
                                            }
                                            addTargetCell = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("+ ${kind.title}", color = p.text, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    Text("${kind.defaultColSpan}×${kind.defaultRowSpan}", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
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
private fun RenderTileContent(
    tile: DockTile,
    clockText: String,
    dashboard: DashboardResponse,
    settings: AppSettings,
    notifications: List<CapturedNotification>,
    nowPlaying: NowPlayingState,
    syncError: String?,
    updateAvailable: UpdatePayload?,
    updateProgress: Int?,
    activeSourceFilter: String,
    editMode: Boolean,
    onToggleDock: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleEdit: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenPower: () -> Unit,
    onOpenUnseen: () -> Unit,
    onOpenActa: () -> Unit,
    onOpenSettings: () -> Unit,
    onTriggerUpdate: () -> Unit,
    onDismissNote: (Int) -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenPreview: (CapturedNotification) -> Unit,
    onDismissNotification: (String) -> Unit,
    onToggleMuteSource: (String) -> Unit
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
            onToggleEdit = onToggleEdit,
            onOpenSettings = onOpenSettings,
            onTriggerUpdate = onTriggerUpdate
        )
        TileKind.ALERT -> AlertTile(dashboard = dashboard, onOpenMap = { if (!editMode) onOpenMap() })
        TileKind.WEATHER -> WeatherTile(dashboard = dashboard, onOpenWeather = { if (!editMode) onOpenWeather() })
        TileKind.POWER -> PowerTile(dashboard = dashboard, onOpenPower = { if (!editMode) onOpenPower() })
        TileKind.CURRENCY -> CurrencyTile(currency = dashboard.currency)
        TileKind.SUNSET_WAVES -> SunsetWavesTile(weather = dashboard.weather)
        TileKind.AIR_SENSORS -> AirSensorsTile(sensors = dashboard.sensors)
        TileKind.AGENT_STATUS -> AgentStatusStrip(dashboard = dashboard)
        TileKind.SPOTIFY -> SpotifyTile(nowPlaying = nowPlaying)
        TileKind.UNSEEN -> UnseenTile(dashboard = dashboard, onOpen = { if (!editMode) onOpenUnseen() })
        TileKind.MCP_NOTES -> McpNotesCard(dashboard = dashboard, onDismissNote = { if (!editMode) onDismissNote(it) })
        TileKind.ACTA_TASKS -> ActaTasksTile(acta = dashboard.acta, onOpen = { if (!editMode) onOpenActa() })
        TileKind.INBOX -> NotificationsCard(
            items = notifications,
            activeFilter = activeSourceFilter,
            mutedSources = settings.mutedSources,
            onFilterChange = onFilterChange,
            onOpen = { if (!editMode) onOpenPreview(it) },
            onDismiss = onDismissNotification,
            onToggleMute = onToggleMuteSource
        )
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
            .fillMaxSize()
            .border(1.dp, p.border, RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.Center) {
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
                fontSize = 26.sp
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
            .fillMaxSize()
            .border(1.dp, border, RoundedCornerShape(7.dp))
            .background(bg, RoundedCornerShape(7.dp))
            .clickable { onOpenMap() }
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AIR RAID", color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            Text("MAP ↗", color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Text(label, color = txt, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(sel.name, color = txt.copy(alpha = 0.75f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun WeatherTile(dashboard: DashboardResponse, onOpenWeather: () -> Unit) {
    val p = LocalVoxPalette.current
    val cur = dashboard.weather.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, p.weatherBorder.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .background(p.weatherBg, RoundedCornerShape(7.dp))
            .clickable { onOpenWeather() }
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(weatherIcon(cur.weatherCode), fontSize = 14.sp)
            Text("10D ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Text("${cur.temp}°C", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            .fillMaxSize()
            .border(1.dp, p.powerBorder.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.powerBg, RoundedCornerShape(7.dp))
            .clickable { onOpenPower() }
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                Text("⚡ DTEK", color = p.powerBorder, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
            Text("24H ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Text(
            text = if (pw.status == "off") "OUTAGE" else "Q${pw.queueGroup.ifBlank { "-" }} ON",
            color = p.text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1
        )
        Text(
            text = pw.nextChange ?: pw.summary,
            color = p.muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
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
            .fillMaxSize()
            .border(1.dp, p.border, RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("MONO FX", color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        if (currency == null) {
            Text("Loading...", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            Text("USD ${"%.2f".format(currency.usdBuy)}/${"%.2f".format(currency.usdSell)}", color = Color(0xFF22C55E), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
            Text("EUR ${"%.2f".format(currency.eurBuy)}/${"%.2f".format(currency.eurSell)}", color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
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
            .fillMaxSize()
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
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
            .fillMaxSize()
            .border(1.dp, aqiColor.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AIR", color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text(sensors?.label ?: "--", color = aqiColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        Text(
            text = sensors?.let { "AQI ${it.aqi}" } ?: "--",
            color = p.text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            text = sensors?.let { "PM2.5 ${it.pm25} · Dust ${it.dust}" } ?: "No data",
            color = p.muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpotifyTile(nowPlaying: NowPlayingState) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF1DB954).copy(alpha = 0.5f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("♫ ${nowPlaying.appLabel.ifBlank { "SPOTIFY / MEDIA" }}", color = Color(0xFF1DB954), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text(if (nowPlaying.isPlaying) "PLAYING" else "PAUSED", color = if (nowPlaying.isPlaying) Color(0xFF4ADE80) else p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (nowPlaying.hasSession) nowPlaying.title else "Start Spotify or YT Music",
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (nowPlaying.artist.isNotBlank()) {
                Text(nowPlaying.artist, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FullWidthMediaBtn("⏮", false, Modifier.weight(1f)) { MediaMonitor.previous() }
            FullWidthMediaBtn(if (nowPlaying.isPlaying) "⏸ PAUSE" else "▶ PLAY", nowPlaying.isPlaying, Modifier.weight(1.6f)) { MediaMonitor.togglePlayPause() }
            FullWidthMediaBtn("⏭", false, Modifier.weight(1f)) { MediaMonitor.next() }
        }
    }
}

@Composable
private fun FullWidthMediaBtn(label: String, highlighted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalVoxPalette.current
    Box(
        modifier = modifier
            .border(1.dp, if (highlighted) Color(0xFF1DB954) else p.border, RoundedCornerShape(5.dp))
            .background(if (highlighted) Color(0xFF1DB954).copy(alpha = 0.25f) else p.cardAlt, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (highlighted) Color(0xFF4ADE80) else p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun ActaTasksTile(acta: ActaPayload, onOpen: () -> Unit) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .clickable { onOpen() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ACTA // MY TASKS", color = Color(0xFF818CF8), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("${acta.count} ↗", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        if (!acta.configured) {
            Text("ACTA MCP not configured", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else if (acta.tasks.isEmpty()) {
            Text("No open tasks in ACTA", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            acta.tasks.take(5).forEach { t ->
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
            .fillMaxSize()
            .border(1.dp, p.unseenBorder.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.unseenBg, RoundedCornerShape(7.dp))
            .clickable { onOpen() }
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (item == null) {
            Text("◈ UNSEEN", color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("Quiet nearby", color = p.text.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("◈ ${item.category.uppercase()}", color = p.unseenBorder, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                Text("${dashboard.unseen.size} ↗", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Text(item.title, color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, p.border, RoundedCornerShape(7.dp))
            .background(p.card, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            Text(st.source.uppercase(), color = p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        Text(
            text = st.message,
            color = p.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun McpNotesCard(dashboard: DashboardResponse, onDismissNote: (Int) -> Unit) {
    val p = LocalVoxPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, p.mcpBorder.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
            .background(p.mcpBg, RoundedCornerShape(7.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("✦ MCP // NOTES & REMINDERS", color = p.mcpBorder, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text("${dashboard.notes.size}", color = p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        if (dashboard.notes.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text("No active notes from agents.", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        } else {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dashboard.notes.take(4).forEach { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(p.cardAlt.copy(alpha = 0.85f))
                            .clickable { onDismissNote(note.id) }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val prefix = if (note.kind == "reminder") "⏰ " else "› "
                        Text("$prefix${note.text}", color = p.text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        note.remindAt?.let {
                            Spacer(Modifier.width(5.dp))
                            Text(it, color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                        }
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
            .fillMaxSize()
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
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (listenerOn) Color(0xFF22C55E) else Color(0xFFF59E0B)))
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
                        Text(if (muted) "$src✕" else src, color = if (selected) p.bg else p.muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Quiet — no unread notifications", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        } else {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items.take(8).forEach { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, p.border, RoundedCornerShape(5.dp))
                            .background(p.cardAlt, RoundedCornerShape(5.dp))
                            .clickable { onOpen(item) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.source} · ${item.sender}", color = sourceColor(item.source), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🔇", fontSize = 9.sp, modifier = Modifier.clickable { onToggleMute(item.source) })
                                Text("✕", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.clickable { onDismiss(item.key) })
                            }
                        }
                        Text(item.text, color = p.text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
                Text(if (alertLevel == "partial") "PARTIAL ALERT" else "NIGHT DOCK · TAP TO WAKE", color = Color(0xFF3F3F46), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
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
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (highlighted) Color.White else p.text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${item.source} · ${item.sender}", color = sourceColor(item.source), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("$timeStr · UNREAD PREVIEW", color = p.muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { line ->
                    Text(line, color = p.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SmallPill("CLOSE", false, onClose)
                SmallPill("DISMISS", false, onDismiss)
                SmallPill("OPEN CHAT ↗", true, onOpenApp)
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
            diffMin > 60 -> "🌇 ${diffMin / 60}h ${diffMin % 60}m"
            diffMin > 0 -> "🌇 in ${diffMin}m"
            else -> "🌙 ${sunsetIso.substringAfter('T').take(5)}"
        }
    }.getOrDefault("☀ --")
}

private fun sourceColor(source: String): Color = when (source) {
    "TG" -> Color(0xFF38BDF8)
    "KSU24" -> Color(0xFF34D399)
    "ZOOM" -> Color(0xFF818CF8)
    else -> Color(0xFFA1A1AA)
}

private fun formatTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
