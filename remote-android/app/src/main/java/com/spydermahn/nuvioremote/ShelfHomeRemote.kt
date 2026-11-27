@file:OptIn(ExperimentalMaterial3Api::class)

package com.spydermahn.nuvioremote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The TV's shelf home on the phone (v3 phase 9, board V3Phone). The TV's backdrop at the top with the focused title and
 * its line, then the row's name and place, the row's posters (swipe along; a tap shows that one on the TV), and four
 * buttons: Play, Info, Shelves and the row's view. Shelves is the phone's own list of every row, grouped as on the TV,
 * with a search box; a tap jumps the TV to that row (`HOME_DO`).
 */

@Composable
fun ShelfHomeBody(home: ShelfHomeInfo, shelves: List<ShelfRowInfo>, viewModel: RemoteViewModel) {
    val g = LocalGlass.current
    var shelvesOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 12.dp)) {
        // The TV's backdrop, fading into the ground, the title and its line over its foot.
        Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(24.dp))) {
            RemoteImage(url = home.art, fallback = home.cards.firstOrNull { it.index == home.index }?.poster, placeholder = "", modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to g.ground.copy(alpha = 0.6f), 0.18f to Color.Transparent, 0.4f to Color.Transparent, 1f to g.ground),
                ),
            )
            Text(
                "ON THE TV · ${home.tabTitle.ifBlank { "Home" }.uppercase()}",
                color = g.text.copy(alpha = 0.75f),
                fontSize = 11.sp,
                letterSpacing = 2.6.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    home.title,
                    color = g.text,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Always one line high, so the title never moves when a card has no line.
                Text(
                    home.facts,
                    color = g.text.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    letterSpacing = 2.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp).height(16.dp),
                )
            }
        }

        // The row's name and place, with the rows above and below.
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    home.rowTitle.uppercase().ifBlank { null },
                    if (home.count > 0) "${home.index + 1} OF ${home.count}" else null,
                ).joinToString(" · "),
                color = g.text.copy(alpha = 0.6f),
                fontSize = 11.sp,
                letterSpacing = 2.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.homeRow(-1) }, enabled = home.row > 0) {
                Icon(Icons.Rounded.ExpandLess, contentDescription = "The row above", tint = if (home.row > 0) g.textSecondary else g.track)
            }
            IconButton(onClick = { viewModel.homeRow(1) }, enabled = home.row < home.rows - 1) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "The row below", tint = if (home.row < home.rows - 1) g.textSecondary else g.track)
            }
        }
        ShelfPosterRow(home) { card ->
            // A tap on the one already shown opens it, as a click on the TV does.
            if (card.index == home.index) viewModel.home("play") else viewModel.homePick(card.index)
        }
        Text(
            "Swipe the row; tap a poster to show it on the TV",
            color = g.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )

        // The four buttons, two by two, all the same size.
        Column(Modifier.fillMaxWidth().padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeButton("Play", Icons.Rounded.PlayArrow, accent = true, modifier = Modifier.weight(1f)) { viewModel.home("play") }
                HomeButton("Info", Icons.Rounded.Info, accent = false, modifier = Modifier.weight(1f)) { viewModel.home("info") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeButton("Shelves", Icons.AutoMirrored.Rounded.List, accent = false, modifier = Modifier.weight(1f)) { shelvesOpen = true }
                HomeButton("View: ${home.view}", Icons.Rounded.ViewCarousel, accent = false, modifier = Modifier.weight(1f)) { viewModel.home("view") }
            }
        }
    }
    if (shelvesOpen) {
        ShelvesSheet(
            home = home,
            rows = shelves,
            onPick = { row ->
                viewModel.homeShelf(row.tabId, row.key)
                shelvesOpen = false
            },
            onShowOnTv = {
                viewModel.home("shelves")
                shelvesOpen = false
            },
            onDismiss = { shelvesOpen = false },
        )
    }
}

/** The row's posters: the focused one larger and outlined, the rest dimmed; it follows the TV. */
@Composable
private fun ShelfPosterRow(home: ShelfHomeInfo, onTap: (ShelfCardInfo) -> Unit) {
    val list = rememberLazyListState()
    val at = home.cards.indexOfFirst { it.index == home.index }
    LaunchedEffect(home.index, home.cards.firstOrNull()?.index, home.rowTitle) {
        if (at >= 0) list.animateScrollToItem((at - 1).coerceAtLeast(0))
    }
    if (home.cards.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(141.dp).padding(top = 12.dp), contentAlignment = Alignment.CenterStart) {
            Text("This row is still loading on the TV.", color = LocalGlass.current.textSecondary, fontSize = 14.sp)
        }
        return
    }
    LazyRow(
        state = list,
        modifier = Modifier.fillMaxWidth().height(141.dp).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(home.cards, key = { it.index }) { card ->
            val focused = card.index == home.index
            Box(
                Modifier
                    .width(if (focused) 86.dp else 70.dp)
                    .height(if (focused) 129.dp else 105.dp)
                    .alpha(if (focused) 1f else 0.55f)
                    .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)).padding(3.dp) else Modifier)
                    .clip(RoundedCornerShape(4.dp))
                    // A tap on release, cancelled by a swipe: pressable fires on touch-down, so swiping the row tapped the
                    // poster under the thumb (Nick, Nov 27).
                    .clickable { onTap(card) },
            ) {
                RemoteImage(url = card.poster, fallback = null, placeholder = card.title, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun HomeButton(label: String, icon: ImageVector, accent: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val g = LocalGlass.current
    Row(
        modifier
            .height(64.dp)
            .glass(RoundedCornerShape(22.dp), fill = if (accent) g.tint else g.glassFill, edge = if (accent) g.tintEdge else g.glassEdge)
            .pressable { onTap() }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) g.text else g.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = g.text,
            fontSize = 16.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One heading of the Shelves list and what sits under it. */
internal sealed interface ShelvesLine {
    data class Heading(val text: String) : ShelvesLine
    data class Item(val row: ShelfRowInfo) : ShelvesLine

    /** A folded letter range of catalogs; [id] is what opens it (`tabId|label`). */
    data class Bucket(val id: String, val label: String, val count: Int, val open: Boolean) : ShelvesLine
}

/**
 * The lines of the Shelves list: each tab's groups in the TV's order ("YOURS · 5"; a tab other than the one on the TV
 * is named first), a catalog bucket folded unless opened, and with a [query] only the rows whose names hold every word,
 * unfolded. Pure, so it is tested (ShelfHomeLineTest).
 */
internal fun shelvesLines(rows: List<ShelfRowInfo>, currentTabId: String, query: String, openBuckets: Set<String>): List<ShelvesLine> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val shown = if (words.isEmpty()) rows else rows.filter { r -> words.all { it in r.title.lowercase() } }
    // The TV's own tab first, then the others in the order they came.
    val tabs = shown.map { it.tabId }.distinct().sortedBy { if (it == currentTabId) 0 else 1 }
    return buildList<ShelvesLine> {
        for (tab in tabs) {
            val inTab = shown.filter { it.tabId == tab }
            for ((group, groupRows) in inTab.groupBy { it.group }) {
                val name = if (tab == currentTabId) group else "${inTab.first().tabTitle.uppercase()} · $group"
                add(ShelvesLine.Heading("$name · ${groupRows.size}"))
                var lastBucket: String? = null
                for (row in groupRows) {
                    val bucket = row.bucket
                    if (bucket == null || words.isNotEmpty()) {
                        add(ShelvesLine.Item(row))
                        continue
                    }
                    val id = "$tab|$bucket"
                    if (bucket != lastBucket) {
                        add(ShelvesLine.Bucket(id, bucket, groupRows.count { it.bucket == bucket }, id in openBuckets))
                        lastBucket = bucket
                    }
                    if (id in openBuckets) add(ShelvesLine.Item(row))
                }
            }
        }
    }
}

@Composable
private fun ShelvesSheet(
    home: ShelfHomeInfo,
    rows: List<ShelfRowInfo>,
    onPick: (ShelfRowInfo) -> Unit,
    onShowOnTv: () -> Unit,
    onDismiss: () -> Unit,
) {
    val g = LocalGlass.current
    var query by rememberSaveable { mutableStateOf("") }
    var opened by remember { mutableStateOf(setOf<String>()) }
    // A bucket holding the row on the TV starts open.
    LaunchedEffect(rows, home.shelfKey) {
        rows.firstOrNull { it.tabId == home.tabId && it.key == home.shelfKey }?.bucket?.let { opened = opened + "${home.tabId}|$it" }
    }
    val lines = remember(rows, home.tabId, query, opened) { shelvesLines(rows, home.tabId, query, opened) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF14151A),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SHELVES · ${home.tabTitle.ifBlank { "Home" }.uppercase()}",
                    color = g.text.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    letterSpacing = 2.6.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                GlassTag("Show on the TV", onClick = onShowOnTv)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).height(44.dp).glass(RoundedCornerShape(22.dp)).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = g.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search every row…", color = g.textMuted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = g.text, fontSize = 15.sp, fontFamily = NuvioSans),
                        cursorBrush = SolidColor(g.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = g.textSecondary) }
                }
            }
            when {
                rows.isEmpty() -> Text(
                    "The TV hasn't sent its rows yet.",
                    color = g.textSecondary,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                lines.isEmpty() -> Text(
                    "No row is called that.",
                    color = g.textSecondary,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(top = 10.dp)) {
                    items(lines) { line ->
                        when (line) {
                            is ShelvesLine.Heading -> Text(
                                line.text,
                                color = g.accent,
                                fontSize = 11.sp,
                                letterSpacing = 2.8.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                            is ShelvesLine.Bucket -> ShelvesRow(
                                title = "${line.label}  ·  ${line.count}",
                                trailing = if (line.open) "⌄" else "›",
                                current = false,
                            ) {
                                opened = if (line.id in opened) opened - line.id else opened + line.id
                            }
                            is ShelvesLine.Item -> {
                                val current = line.row.tabId == home.tabId && line.row.key == home.shelfKey
                                ShelvesRow(
                                    title = line.row.title,
                                    trailing = if (current) "ON TV" else "›",
                                    current = current,
                                    indent = line.row.bucket != null && query.isBlank(),
                                ) { onPick(line.row) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelvesRow(title: String, trailing: String, current: Boolean, indent: Boolean = false, onTap: () -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).clickable { onTap() }.padding(start = if (indent) 16.dp else 0.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (current) g.text else g.text.copy(alpha = 0.85f),
            fontSize = 17.sp,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(trailing, color = g.text.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1)
    }
}
