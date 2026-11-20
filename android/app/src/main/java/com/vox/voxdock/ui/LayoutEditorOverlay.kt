package com.vox.voxdock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vox.voxdock.data.DockPage
import com.vox.voxdock.data.DockTile
import com.vox.voxdock.data.TileKind
import com.vox.voxdock.data.defaultDockPages
import com.vox.voxdock.ui.theme.LocalVoxPalette

@Composable
fun LayoutEditorOverlay(
    initialPages: List<DockPage>,
    onSave: (List<DockPage>) -> Unit,
    onClose: () -> Unit
) {
    val p = LocalVoxPalette.current
    var pages by remember { mutableStateOf(initialPages.ifEmpty { defaultDockPages() }) }
    var selectedPageIdx by remember { mutableIntStateOf(0) }

    val safeIdx = selectedPageIdx.coerceIn(0, pages.lastIndex)
    val currentPage = pages[safeIdx]

    fun updateCurrentPage(transform: (DockPage) -> DockPage) {
        pages = pages.mapIndexed { idx, page ->
            if (idx == safeIdx) transform(page) else page
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .safeDrawingPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SCREENS & TILES LAYOUT",
                color = p.text,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallPill(label = "RESET", highlighted = false, onClick = {
                    pages = defaultDockPages()
                    selectedPageIdx = 0
                })
                SmallPill(label = "CANCEL", highlighted = false, onClick = onClose)
                SmallPill(label = "SAVE", highlighted = true, onClick = {
                    onSave(pages)
                    onClose()
                })
            }
        }

        // Screen tabs + Add/Remove screen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.forEachIndexed { idx, page ->
                SmallPill(
                    label = "${idx + 1}. ${page.name}",
                    highlighted = idx == safeIdx,
                    onClick = { selectedPageIdx = idx }
                )
            }
            if (pages.size < 5) {
                SmallPill(label = "+ SCREEN", highlighted = false, onClick = {
                    val nextNum = pages.size + 1
                    pages = pages + DockPage(
                        id = "page_${System.currentTimeMillis()}",
                        name = "Screen $nextNum",
                        tiles = listOf(DockTile(TileKind.CLOCK, 2))
                    )
                    selectedPageIdx = pages.lastIndex
                })
            }
            if (pages.size > 1) {
                SmallPill(label = "DEL", highlighted = false, onClick = {
                    pages = pages.filterIndexed { i, _ -> i != safeIdx }
                    selectedPageIdx = (safeIdx - 1).coerceAtLeast(0)
                })
            }
        }

        // Split: Left = active tiles on this screen, Right = available tiles to add
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Active tiles
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .border(1.dp, p.border, RoundedCornerShape(7.dp))
                    .background(p.card, RoundedCornerShape(7.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ON THIS SCREEN (${currentPage.tiles.size})",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(currentPage.tiles) { idx, tile ->
                        val canResize = tile.kind.minSpan != tile.kind.maxSpan
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, p.border, RoundedCornerShape(5.dp))
                                .background(p.cardAlt, RoundedCornerShape(5.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = tile.kind.title,
                                    color = p.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "width ${tile.span}/4 (allowed ${tile.kind.minSpan}..${tile.kind.maxSpan})",
                                    color = p.muted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (canResize) {
                                    MiniBtn("↔${tile.span}") {
                                        val nextSpan = if (tile.span >= tile.kind.maxSpan) {
                                            tile.kind.minSpan
                                        } else {
                                            (tile.span + 1).coerceAtMost(tile.kind.maxSpan)
                                        }
                                        // Only allow 1, 2, or 4 (avoid awkward 3-col span)
                                        val cleanSpan = if (nextSpan == 3) 4.coerceAtMost(tile.kind.maxSpan) else nextSpan
                                        updateCurrentPage { pg ->
                                            pg.copy(tiles = pg.tiles.mapIndexed { i, t ->
                                                if (i == idx) t.copy(span = cleanSpan) else t
                                            })
                                        }
                                    }
                                }
                                if (idx > 0) {
                                    MiniBtn("▲") {
                                        updateCurrentPage { pg ->
                                            val list = pg.tiles.toMutableList()
                                            val tmp = list[idx - 1]
                                            list[idx - 1] = list[idx]
                                            list[idx] = tmp
                                            pg.copy(tiles = list)
                                        }
                                    }
                                }
                                if (idx < currentPage.tiles.lastIndex) {
                                    MiniBtn("▼") {
                                        updateCurrentPage { pg ->
                                            val list = pg.tiles.toMutableList()
                                            val tmp = list[idx + 1]
                                            list[idx + 1] = list[idx]
                                            list[idx] = tmp
                                            pg.copy(tiles = list)
                                        }
                                    }
                                }
                                MiniBtn("✕", Color(0xFFEF4444)) {
                                    updateCurrentPage { pg ->
                                        pg.copy(tiles = pg.tiles.filterIndexed { i, _ -> i != idx })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Available widgets catalog
            val existingKinds = currentPage.tiles.map { it.kind }.toSet()
            val available = TileKind.entries.filterNot { existingKinds.contains(it) }

            Column(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxHeight()
                    .border(1.dp, p.border, RoundedCornerShape(7.dp))
                    .background(p.card, RoundedCornerShape(7.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ADD TILE (+)",
                    color = p.muted,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(available) { _, kind ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, p.border, RoundedCornerShape(5.dp))
                                .background(p.cardAlt, RoundedCornerShape(5.dp))
                                .clickable {
                                    updateCurrentPage { pg ->
                                        pg.copy(tiles = pg.tiles + DockTile(kind, kind.defaultSpan))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+ ${kind.title}",
                                color = p.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "${kind.minSpan}..${kind.maxSpan}",
                                color = p.muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBtn(label: String, tint: Color? = null, onClick: () -> Unit) {
    val p = LocalVoxPalette.current
    Box(
        modifier = Modifier
            .border(1.dp, (tint ?: p.border).copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .background(p.card, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = tint ?: p.text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}
