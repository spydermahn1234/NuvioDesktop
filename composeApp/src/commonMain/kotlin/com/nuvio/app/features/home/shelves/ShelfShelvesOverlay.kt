// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.KeyRouteLayer
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.keyLayer
import com.nuvio.app.core.ui.requestFocusLogged
import kotlinx.coroutines.delay

/*
 * fork:shelf-home - the Shelves overlay (mockup V2Shelves, keys in V2Flow and V2Prototype): over the dimmed
 * art, the tabs across the top and every shelf of the tab in four columns (ShelfGroups.kt), the focused
 * shelf's first posters along the bottom. The frame under it draws the art and the clock; this draws the
 * .62 dim and everything else. It takes every key and click while it is up and moves its own selection
 * (Compose Desktop moves focus only on Tab), so nothing here is a focus target but the root.
 * Fixed layout: the columns, the preview line and the posters never move as the selection does.
 */

private sealed interface ShelvesLine {
    data class OfShelf(val shelf: Shelf) : ShelvesLine
    data class OfBucket(val bucket: ShelfGroupEntry.Bucket) : ShelvesLine
    data object NewShelf : ShelvesLine
    data object AllCatalogs : ShelvesLine
}

private class ShelvesColumn(val label: String, val total: Int, val lines: List<ShelvesLine>, val catalogs: Boolean)

/** A column shows every line up to this many; past it, a window of [WINDOW] lines and "N more". */
private const val MAX_LINES = 8
private const val WINDOW = 7
private const val PREVIEW_POSTERS = 14

private val BackKeys = setOf(Key.Back, Key.Escape, Key.Backspace)
private val EnterKeys = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)
private val MenuKeys = setOf(Key.Menu, Key.V)

private fun columnsOf(tab: ShelfTab?, openBucket: String?): List<ShelvesColumn> {
    if (tab == null) return emptyList()
    val groups = ShelfGroupRules.group(tab)
    val isHome = tab.kind == ShelfTabKind.Home
    val columns = groups.map { g ->
        val catalogs = g.label == ShelfGroupRules.CATALOGS
        val bucket = if (catalogs) {
            g.entries.firstOrNull { it is ShelfGroupEntry.Bucket && it.label == openBucket } as? ShelfGroupEntry.Bucket
        } else {
            null
        }
        val lines: List<ShelvesLine> = if (bucket != null) {
            listOf(ShelvesLine.AllCatalogs) + bucket.shelves.map { ShelvesLine.OfShelf(it) }
        } else {
            g.entries.map { e ->
                when (e) {
                    is ShelfGroupEntry.Row -> ShelvesLine.OfShelf(e.shelf)
                    is ShelfGroupEntry.Bucket -> ShelvesLine.OfBucket(e)
                }
            }
        }
        ShelvesColumn(g.label, g.total, if (isHome && g.label == ShelfGroupRules.YOURS) lines + ShelvesLine.NewShelf else lines, catalogs)
    }
    // Home always offers "+ New shelf", even with nothing of the user's yet.
    return if (isHome && columns.none { it.label == ShelfGroupRules.YOURS }) {
        listOf(ShelvesColumn(ShelfGroupRules.YOURS, 0, listOf(ShelvesLine.NewShelf), false)) + columns
    } else {
        columns
    }
}

/** The bucket of [tab]'s catalogs that holds [shelfKey], if the catalogs are bucketed. */
private fun bucketHolding(tab: ShelfTab?, shelfKey: String?): String? {
    if (tab == null || shelfKey == null) return null
    return ShelfGroupRules.group(tab)
        .firstOrNull { it.label == ShelfGroupRules.CATALOGS }
        ?.entries
        ?.firstOrNull { it is ShelfGroupEntry.Bucket && it.shelves.any { s -> s.key == shelfKey } }
        ?.let { (it as ShelfGroupEntry.Bucket).label }
}

private fun locate(columns: List<ShelvesColumn>, shelfKey: String?): Pair<Int, Int>? {
    if (shelfKey == null) return null
    columns.forEachIndexed { c, column ->
        val r = column.lines.indexOfFirst { it is ShelvesLine.OfShelf && it.shelf.key == shelfKey }
        if (r >= 0) return c to r
    }
    return null
}

@Composable
private fun Modifier.onTap(action: () -> Unit): Modifier {
    val current by rememberUpdatedState(action)
    return pointerInput(Unit) { detectTapGestures { current() } }
}

/** The mockup's focus mark: a 3 dp accent bar 4 dp under the text, drawn outside it so nothing moves. */
private fun Modifier.underline(on: Boolean, color: Color): Modifier = drawBehind {
    if (on) {
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height + 4.dp.toPx()),
            size = Size(size.width, 3.dp.toPx()),
        )
    }
}

@Composable
internal fun ShelfShelvesOverlay(
    model: ShelfHomeModel,
    currentTabId: String,
    currentShelfKey: String?,
    previewFor: (Shelf) -> List<ShelfCard>,
    onPick: (tabId: String, shelfKey: String) -> Unit,
    onOpenLibrary: (() -> Unit)?,
    onViewAs: (Shelf) -> Unit,
    onNewShelf: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Up from the tab line: on to the app's top bar (Nick, Nov 27). Null where there is none to go to. */
    onUpToTopBar: (() -> Unit)? = null,
) {
    KeyRouteLayer("ShelfShelves")
    val palette = LocalShelfPalette.current
    val ink = palette.ink
    val accent = palette.accentInk

    val contentTabs = model.tabs.filter { it.kind != ShelfTabKind.Library }
    val startTab = contentTabs.firstOrNull { it.id == currentTabId } ?: contentTabs.firstOrNull()

    var shownTabId by remember { mutableStateOf(startTab?.id.orEmpty()) }
    val shownTab = contentTabs.firstOrNull { it.id == shownTabId } ?: startTab
    var openBucket by remember { mutableStateOf(bucketHolding(startTab, currentShelfKey)) }
    val columns = remember(shownTab, openBucket) { columnsOf(shownTab, openBucket) }

    val startAt = remember { locate(columnsOf(startTab, openBucket), currentShelfKey) }
    var col by remember { mutableIntStateOf(startAt?.first ?: 0) }
    var row by remember { mutableIntStateOf(startAt?.second ?: 0) }
    var onTabLine by remember { mutableStateOf(false) }
    var tabFocus by remember { mutableIntStateOf(model.tabs.indexOfFirst { it.id == startTab?.id }.coerceAtLeast(0)) }
    // Each column's first shown line, for columns longer than MAX_LINES.
    val offsets = remember { mutableStateMapOf<Int, Int>() }

    val c = col.coerceIn(0, (columns.size - 1).coerceAtLeast(0))
    val r = row.coerceIn(0, ((columns.getOrNull(c)?.lines?.size ?: 1) - 1).coerceAtLeast(0))
    val tabLine = onTabLine || columns.isEmpty()
    val focusedLine = if (tabLine) null else columns.getOrNull(c)?.lines?.getOrNull(r)

    fun keepVisible(column: Int, line: Int) {
        val size = columns.getOrNull(column)?.lines?.size ?: return
        if (size <= MAX_LINES) {
            offsets[column] = 0
            return
        }
        var first = offsets[column] ?: 0
        if (line < first) first = line
        if (line > first + WINDOW - 1) first = line - (WINDOW - 1)
        offsets[column] = first.coerceIn(0, size - WINDOW)
    }

    fun focus(column: Int, line: Int) {
        col = column
        row = line
        keepVisible(column, line)
    }

    // The start (and a new tab, or a bucket opened or closed): the selected row's column scrolled to it.
    LaunchedEffect(shownTabId, openBucket) { keepVisible(c, r) }

    fun showTab(id: String) {
        if (id == shownTabId) return
        val tab = contentTabs.firstOrNull { it.id == id } ?: return
        shownTabId = id
        offsets.clear()
        val bucket = if (id == currentTabId) bucketHolding(tab, currentShelfKey) else null
        openBucket = bucket
        val at = if (id == currentTabId) locate(columnsOf(tab, bucket), currentShelfKey) else null
        col = at?.first ?: 0
        row = at?.second ?: 0
    }

    fun closeBucket() {
        val label = openBucket ?: return
        openBucket = null
        val catalogsCol = columns.indexOfFirst { it.catalogs }
        offsets.remove(catalogsCol)
        if (catalogsCol >= 0 && c == catalogsCol) {
            val back = columnsOf(shownTab, null).getOrNull(catalogsCol)?.lines
                ?.indexOfFirst { it is ShelvesLine.OfBucket && it.bucket.label == label } ?: 0
            focus(catalogsCol, back.coerceAtLeast(0))
        }
    }

    fun openBucketAt(label: String, column: Int) {
        openBucket = label
        offsets.remove(column)
        // The bucket's first shelf, just under "‹ All catalogs".
        col = column
        row = 1
    }

    fun activate(line: ShelvesLine, column: Int) {
        when (line) {
            is ShelvesLine.OfShelf -> onPick(shownTabId, line.shelf.key)
            is ShelvesLine.OfBucket -> openBucketAt(line.bucket.label, column)
            ShelvesLine.AllCatalogs -> closeBucket()
            ShelvesLine.NewShelf -> onNewShelf()
        }
    }

    fun move(key: Key) {
        if (tabLine) {
            when (key) {
                Key.DirectionLeft, Key.DirectionRight -> {
                    val to = (tabFocus + if (key == Key.DirectionLeft) -1 else 1).coerceIn(0, (model.tabs.size - 1).coerceAtLeast(0))
                    tabFocus = to
                    model.tabs.getOrNull(to)?.takeIf { it.kind != ShelfTabKind.Library }?.let { showTab(it.id) }
                }
                Key.DirectionDown -> if (columns.isNotEmpty()) {
                    onTabLine = false
                    tabFocus = model.tabs.indexOfFirst { it.id == shownTabId }.coerceAtLeast(0)
                }
                Key.DirectionUp -> onUpToTopBar?.invoke()
                else -> Unit
            }
            return
        }
        when (key) {
            Key.DirectionLeft -> if (c > 0) focus(c - 1, r.coerceAtMost(columns[c - 1].lines.size - 1))
            Key.DirectionRight -> if (c < columns.size - 1) focus(c + 1, r.coerceAtMost(columns[c + 1].lines.size - 1))
            Key.DirectionUp -> if (r > 0) {
                focus(c, r - 1)
            } else {
                onTabLine = true
                tabFocus = model.tabs.indexOfFirst { it.id == shownTabId }.coerceAtLeast(0)
            }
            Key.DirectionDown -> if (r < columns[c].lines.size - 1) focus(c, r + 1)
            else -> Unit
        }
    }

    fun enter() {
        if (tabLine) {
            val t = model.tabs.getOrNull(tabFocus) ?: return
            if (t.kind == ShelfTabKind.Library) {
                onOpenLibrary?.invoke()
            } else if (columns.isNotEmpty()) {
                onTabLine = false
            }
            return
        }
        focusedLine?.let { activate(it, c) }
    }

    fun back() {
        if (openBucket != null && columns.any { it.catalogs }) closeBucket() else onDismiss()
    }

    fun viewAs() {
        (focusedLine as? ShelvesLine.OfShelf)?.let { onViewAs(it.shelf) }
    }

    // A key acts on its release only when its press came here too: the Back, Menu or OK that opened this
    // overlay must not also close it, open a menu or pick.
    val pressed = remember { mutableSetOf<Key>() }
    fun onKey(event: KeyEvent): Boolean {
        val key = event.key
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp
        fun onRelease(action: () -> Unit): Boolean {
            if (down) pressed += key
            if (up && pressed.remove(key)) action()
            return true
        }
        return when (key) {
            in BackKeys -> onRelease(::back)
            in EnterKeys -> onRelease(::enter)
            in MenuKeys -> onRelease(::viewAs)
            Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                if (down) move(key)
                true
            }
            // Tab would move focus to the frame underneath.
            Key.Tab -> true
            else -> false
        }
    }

    val root = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        for (attempt in 0 until 6) {
            if (root.requestFocusLogged()) break
            delay(32)
        }
    }

    // The dim and the words come up over 200 ms; a new tab's groups fade in together.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(200)) }
    val groupsIn = remember(shownTabId) { Animatable(0f) }
    LaunchedEffect(shownTabId) { groupsIn.animateTo(1f, tween(200)) }

    val dimBase = if (palette.backdropAlpha > 0f) Color(0xFF06090A) else palette.page
    val currentMark = lerp(accent, ink, 0.5f)
    val labelSpacing = 4.8.sp
    val lineSpacing = 4.2.sp

    Box(
        modifier
            .fillMaxSize()
            // Every click lands here: nothing under the overlay can be clicked.
            .pointerInput(Unit) { detectTapGestures { } }
            .keyLayer("ShelfShelves") { root.requestFocusLogged() }
            .focusRequester(root)
            .onPreviewKeyEvent { onKey(it) }
            .focusable()
            .graphicsLayer { alpha = appear.value },
    ) {
        Box(Modifier.fillMaxSize().background(dimBase.copy(alpha = 0.62f)))

        // The tabs, Library last, accent slashes between them.
        Row(
            Modifier.padding(start = 96.dp, top = 72.dp, end = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            model.tabs.forEachIndexed { index, t ->
                if (index > 0) {
                    Text("/", fontSize = 40.sp, fontWeight = FontWeight.Light, color = accent, maxLines = 1, modifier = Modifier.alignByBaseline())
                }
                val current = t.id == shownTab?.id
                val focusedTab = tabLine && index == tabFocus
                Text(
                    t.title,
                    fontSize = 40.sp,
                    fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Light,
                    color = if (current || focusedTab) ink else ink.copy(alpha = 0.7f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .alignByBaseline()
                        .underline(focusedTab, accent)
                        .onTap {
                            if (t.kind == ShelfTabKind.Library) {
                                onOpenLibrary?.invoke()
                            } else {
                                tabFocus = index
                                showTab(t.id)
                            }
                        },
                )
            }
        }

        // The four groups.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 96.dp, end = 96.dp, top = 220.dp)
                .graphicsLayer { alpha = groupsIn.value },
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            for (slot in 0 until 4) {
                val column = columns.getOrNull(slot)
                Column(Modifier.weight(1f)) {
                    if (column == null) {
                        if (slot == 0 && columns.isEmpty()) {
                            Text("Nothing here yet", fontSize = 28.sp, fontWeight = FontWeight.Light, color = palette.inkDim, maxLines = 1)
                        }
                        return@Column
                    }
                    Text(
                        "${column.label} · ${column.total}",
                        fontSize = 16.sp,
                        letterSpacing = labelSpacing,
                        color = accent,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(22.dp))
                    val lines = column.lines
                    val first = if (lines.size <= MAX_LINES) 0 else (offsets[slot] ?: 0).coerceIn(0, lines.size - WINDOW)
                    val count = if (lines.size <= MAX_LINES) lines.size else WINDOW
                    val more = lines.size - first - count
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in first until first + count) {
                            val line = lines[i]
                            val on = !tabLine && slot == c && i == r
                            val isCurrent = line is ShelvesLine.OfShelf && shownTabId == currentTabId && line.shelf.key == currentShelfKey
                            val text = when (line) {
                                is ShelvesLine.OfShelf -> line.shelf.title
                                is ShelvesLine.OfBucket -> line.bucket.label
                                ShelvesLine.NewShelf -> "+ New shelf"
                                ShelvesLine.AllCatalogs -> "‹ All catalogs"
                            }
                            Text(
                                text,
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Light,
                                color = when {
                                    on -> ink
                                    line == ShelvesLine.AllCatalogs -> palette.inkSoft
                                    isCurrent -> currentMark
                                    else -> ink
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .underline(on, accent)
                                    .onTap {
                                        onTabLine = false
                                        focus(slot, i)
                                        activate(line, slot)
                                    },
                            )
                        }
                        if (more > 0) {
                            Text(
                                "$more more",
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.Light,
                                color = palette.inkDim,
                                maxLines = 1,
                                modifier = Modifier.onTap {
                                    offsets[slot] = (first + WINDOW).coerceAtMost(lines.size - WINDOW)
                                },
                            )
                        }
                    }
                }
            }
        }

        // The preview: the focused shelf's name and size, then its first posters.
        val previewShelf = (focusedLine as? ShelvesLine.OfShelf)?.shelf
        val preview = previewShelf?.let(previewFor).orEmpty()
        val previewText = when {
            previewShelf != null -> {
                val n = preview.size
                "PREVIEW · ${previewShelf.title.uppercase()} · $n ${if (n == 1) "TITLE" else "TITLES"}"
            }
            focusedLine is ShelvesLine.OfBucket -> {
                val n = focusedLine.bucket.shelves.size
                "${focusedLine.bucket.label} · $n ${if (n == 1) "SHELF" else "SHELVES"}"
            }
            else -> ""
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 96.dp, end = 96.dp, bottom = 150.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                previewText,
                fontSize = 15.sp,
                letterSpacing = lineSpacing,
                color = ink.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline().weight(1f, fill = false).padding(end = 40.dp),
            )
            Text(
                "OK OPENS · BACK RETURNS · MENU: VIEW AS",
                fontSize = 15.sp,
                letterSpacing = lineSpacing,
                color = palette.inkDim,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 96.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            preview.take(PREVIEW_POSTERS).forEach { card ->
                Box(Modifier.size(60.dp, 90.dp).background(palette.placeholder)) {
                    val url = card.posterUrl()
                    if (url != null) {
                        NuvioAsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
