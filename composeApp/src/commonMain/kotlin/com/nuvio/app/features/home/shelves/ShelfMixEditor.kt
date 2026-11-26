// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.KeyRouteLayer
import com.nuvio.app.core.ui.keyLayer
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.requestFocusLogged
import com.nuvio.app.core.ui.trackTextInputFocus
import com.nuvio.app.features.tonight.TonightSettingsRepository
import com.nuvio.app.features.tonight.clockLabel

/*
 * fork:shelf-home - phase 10: the shelf builder (mockup "Shelf builder - The Hang"). A full-screen
 * dark overlay: the shelf's name in big type, PULL FROM (the home's rows, ticked) on the left, ONLY
 * SHOW (max runtime, hide watched, must finish before bedtime) on the right, Save / Cancel / Delete.
 *
 * Remote-first: every control is focusable and the arrows are routed by hand between them (as the
 * view picker does), so focus never wanders to the home behind. The name field is focused when a
 * new shelf opens (the on-screen keyboard follows TextInputFocusTracker); when editing, the first
 * row is, so the keyboard does not jump up. Down or Enter leaves the field; Back in the field leaves
 * it, Back anywhere else closes without saving. Fixed sizes: nothing moves as rows are ticked.
 */

private val EditorFill = Color(0xF20B0B0F)
private val PanelFill = Color.White.copy(alpha = 0.04f)
private val RowShape = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(24.dp)
private val CloseKeys = setOf(Key.Back, Key.Escape)
private const val TITLE_MAX = 40

/** Where focus can be in the editor. */
private sealed interface Slot {
    data object Title : Slot
    data class Source(val index: Int) : Slot
    data class Runtime(val index: Int) : Slot
    data object HideWatched : Slot
    data object Bedtime : Slot
    data class Button(val index: Int) : Slot
}

@Composable
internal fun ShelfMixEditor(
    existing: ShelfMix?,
    availableRows: List<Pair<String, String>>,
    onSave: (ShelfMix) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    KeyRouteLayer("ShelfMixEditor")
    val tokens = MaterialTheme.nuvio
    val accent = tokens.colors.accent
    remember { TonightSettingsRepository.ensureLoaded() }
    val tonight by TonightSettingsRepository.state.collectAsState()

    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var sources by remember { mutableStateOf(existing?.sourceKeys.orEmpty()) }
    var maxRuntime by remember { mutableStateOf(existing?.maxRuntime) }
    var hideWatched by remember { mutableStateOf(existing?.hideWatched ?: false) }
    var fitsBeforeBed by remember { mutableStateOf(existing?.fitsBeforeBed ?: false) }

    // The home's rows, then any row the mix already pulls from that is not on Home now (kept, so it can be unticked).
    val rows: List<Pair<String, String>> = remember(availableRows, existing) {
        val known = availableRows.map { it.first }.toSet()
        availableRows + existing?.sourceKeys.orEmpty().filter { it !in known }.map { it to "$it (not on Home now)" }
    }
    val runtimeChoices = ShelfMixRules.RUNTIME_CHOICES
    val buttons: List<String> = remember(onDelete != null) {
        if (onDelete != null) listOf("Save", "Cancel", "Delete") else listOf("Save", "Cancel")
    }

    val draft = ShelfMix(
        id = existing?.id.orEmpty(),
        title = title,
        // Saved in the home's order, so the merge follows the rows as Home lists them.
        sourceKeys = rows.map { it.first }.filter { it in sources },
        maxRuntime = maxRuntime,
        hideWatched = hideWatched,
        fitsBeforeBed = fitsBeforeBed,
        genres = existing?.genres.orEmpty(),
    )
    val canSave = ShelfMixRules.canSave(draft)

    val titleRequester = remember { FocusRequester() }
    val sourceRequesters = remember(rows.size) { List(rows.size) { FocusRequester() } }
    val runtimeRequesters = remember { List(runtimeChoices.size) { FocusRequester() } }
    val hideRequester = remember { FocusRequester() }
    val bedRequester = remember { FocusRequester() }
    val buttonRequesters = remember(buttons.size) { List(buttons.size) { FocusRequester() } }

    var focused by remember { mutableStateOf<Slot>(Slot.Title) }
    var lastSource by remember { mutableStateOf(0) }
    var lastRight by remember { mutableStateOf<Slot>(Slot.Runtime(0)) }

    fun requesterOf(slot: Slot): FocusRequester? = when (slot) {
        Slot.Title -> titleRequester
        is Slot.Source -> sourceRequesters.getOrNull(slot.index)
        is Slot.Runtime -> runtimeRequesters.getOrNull(slot.index)
        Slot.HideWatched -> hideRequester
        Slot.Bedtime -> bedRequester
        is Slot.Button -> buttonRequesters.getOrNull(slot.index)
    }
    fun go(slot: Slot) {
        requesterOf(slot)?.requestFocusLogged()
    }
    fun firstSourceOr(fallback: Slot): Slot = if (rows.isNotEmpty()) Slot.Source(lastSource.coerceIn(0, rows.lastIndex)) else fallback
    fun selectedRuntime(): Slot = Slot.Runtime(runtimeChoices.indexOf(maxRuntime).coerceAtLeast(0))

    /** Where an arrow goes from [from]; null stays put. */
    fun target(from: Slot, key: Key): Slot? = when (from) {
        Slot.Title -> when (key) {
            Key.DirectionDown -> firstSourceOr(Slot.Runtime(0))
            else -> null
        }
        is Slot.Source -> when (key) {
            Key.DirectionUp -> if (from.index > 0) Slot.Source(from.index - 1) else Slot.Title
            Key.DirectionDown -> if (from.index < rows.lastIndex) Slot.Source(from.index + 1) else null
            Key.DirectionRight -> lastRight
            else -> null
        }
        is Slot.Runtime -> when (key) {
            Key.DirectionLeft -> if (from.index > 0) Slot.Runtime(from.index - 1) else firstSourceOr(from)
            Key.DirectionRight -> if (from.index < runtimeChoices.lastIndex) Slot.Runtime(from.index + 1) else null
            Key.DirectionUp -> Slot.Title
            Key.DirectionDown -> Slot.HideWatched
            else -> null
        }
        Slot.HideWatched -> when (key) {
            Key.DirectionUp -> selectedRuntime()
            Key.DirectionDown -> Slot.Bedtime
            Key.DirectionLeft -> firstSourceOr(from)
            else -> null
        }
        Slot.Bedtime -> when (key) {
            Key.DirectionUp -> Slot.HideWatched
            Key.DirectionDown -> Slot.Button(0)
            Key.DirectionLeft -> firstSourceOr(from)
            else -> null
        }
        is Slot.Button -> when (key) {
            Key.DirectionUp -> Slot.Bedtime
            Key.DirectionLeft -> if (from.index > 0) Slot.Button(from.index - 1) else firstSourceOr(from)
            Key.DirectionRight -> if (from.index < buttons.lastIndex) Slot.Button(from.index + 1) else null
            else -> null
        }
    }

    fun onFocused(slot: Slot) {
        focused = slot
        when (slot) {
            is Slot.Source -> lastSource = slot.index
            is Slot.Runtime, Slot.HideWatched, Slot.Bedtime, is Slot.Button -> lastRight = slot
            Slot.Title -> Unit
        }
    }

    val dismiss by rememberUpdatedState(onDismiss)
    fun save() {
        if (canSave) onSave(draft)
    }
    fun press(index: Int) {
        when (buttons[index]) {
            "Save" -> save()
            "Cancel" -> dismiss()
            "Delete" -> onDelete?.invoke()
        }
    }

    LaunchedEffect(Unit) {
        if (existing == null || rows.isEmpty()) titleRequester.requestFocusLogged() else go(Slot.Source(0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorFill)
            // Takes every pointer event so nothing behind can be clicked.
            .pointerInput(Unit) { detectTapGestures { } }
            .keyLayer("ShelfMixEditor") { requesterOf(focused)?.requestFocusLogged() ?: false }
            .onPreviewKeyEvent { event ->
                val inTitle = focused == Slot.Title
                when {
                    event.key in CloseKeys -> {
                        if (event.type == KeyEventType.KeyUp) {
                            // Back in the name field ends typing; anywhere else it closes without saving.
                            if (inTitle) go(firstSourceOr(Slot.Runtime(0))) else dismiss()
                        }
                        true
                    }
                    event.type != KeyEventType.KeyDown -> false
                    inTitle && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                        go(firstSourceOr(Slot.Runtime(0)))
                        true
                    }
                    // Left/Right in the field move its cursor.
                    inTitle && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> false
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> {
                        target(focused, event.key)?.let(::go)
                        true
                    }
                    else -> false
                }
            }
            // An arrow nothing used (the field at its first or last letter) must not take focus off the editor.
            .onKeyEvent { event ->
                event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.DirectionUp || event.key == Key.DirectionDown
            }
            .padding(horizontal = 96.dp, vertical = 64.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                if (existing == null) "SHELF BUILDER  ·  NEW SHELF" else "SHELF BUILDER  ·  EDIT",
                fontSize = 16.sp,
                letterSpacing = 4.sp,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            TitleField(
                value = title,
                onValueChange = { title = it.take(TITLE_MAX) },
                requester = titleRequester,
                onFocused = { onFocused(Slot.Title) },
            )
            Spacer(Modifier.height(36.dp))

            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                // PULL FROM
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel("PULL FROM")
                        SectionLabel("${draft.sourceKeys.size} OF ${rows.size}")
                    }
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(PanelFill, RowShape)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (rows.isEmpty()) {
                            Text("No rows on Home yet", fontSize = 20.sp, color = tokens.colors.textMuted, modifier = Modifier.padding(12.dp))
                        }
                        rows.forEachIndexed { index, (key, name) ->
                            val ticked = key in sources
                            EditorRow(
                                title = name,
                                ticked = ticked,
                                requester = sourceRequesters[index],
                                onFocused = { onFocused(Slot.Source(index)) },
                                onClick = { sources = if (ticked) sources - key else sources + key },
                            )
                        }
                    }
                }

                // ONLY SHOW
                Column(Modifier.width(620.dp).fillMaxHeight()) {
                    SectionLabel("ONLY SHOW")
                    Spacer(Modifier.height(14.dp))
                    Text("Max runtime", fontSize = 20.sp, color = tokens.colors.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        runtimeChoices.forEachIndexed { index, minutes ->
                            Chip(
                                label = minutes?.let { "$it min" } ?: "None",
                                selected = maxRuntime == minutes,
                                requester = runtimeRequesters[index],
                                onFocused = { onFocused(Slot.Runtime(index)) },
                                onClick = { maxRuntime = minutes },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    EditorRow(
                        title = "Hide watched",
                        description = "Leave out what you have seen",
                        ticked = hideWatched,
                        switch = true,
                        requester = hideRequester,
                        onFocused = { onFocused(Slot.HideWatched) },
                        onClick = { hideWatched = !hideWatched },
                    )
                    Spacer(Modifier.height(10.dp))
                    EditorRow(
                        title = "Must finish before bedtime",
                        description = "Bedtime ${clockLabel(tonight.bedMinuteOfDay)} (What to watch settings)",
                        ticked = fitsBeforeBed,
                        switch = true,
                        requester = bedRequester,
                        onFocused = { onFocused(Slot.Bedtime) },
                        onClick = { fitsBeforeBed = !fitsBeforeBed },
                    )
                    Spacer(Modifier.weight(1f))
                    // Always there, so the buttons never move.
                    Text(
                        if (canSave) "" else "Name the shelf and pick at least one row",
                        fontSize = 17.sp,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        modifier = Modifier.height(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        buttons.forEachIndexed { index, label ->
                            EditorButton(
                                label = label,
                                primary = index == 0,
                                enabled = label != "Save" || canSave,
                                danger = label == "Delete",
                                requester = buttonRequesters[index],
                                onFocused = { onFocused(Slot.Button(index)) },
                                onClick = { press(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 15.sp, letterSpacing = 4.sp, color = Color.White.copy(alpha = 0.78f), maxLines = 1)
}

@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    requester: FocusRequester,
    onFocused: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Color.White),
        cursorBrush = SolidColor(tokens.colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .focusRequester(requester)
            // The app-wide typing lock (shortcut letters off) and the on-screen keyboard follow this.
            .trackTextInputFocus()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        decorationBox = { inner ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "Name this shelf",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Light,
                            color = Color.White.copy(alpha = 0.35f),
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (focused) tokens.colors.accent else Color.White.copy(alpha = 0.18f)),
                )
            }
        },
    )
}

/** A row with a tick (sources) or an on/off switch (rules). Fixed height; the tick column is always there. */
@Composable
private fun EditorRow(
    title: String,
    ticked: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    description: String? = null,
    switch: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (description != null) 72.dp else 58.dp)
            .background(
                when {
                    focused -> tokens.colors.accent.copy(alpha = 0.42f)
                    hovered -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.05f)
                },
                RowShape,
            )
            .border(1.dp, if (focused) tokens.colors.accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.10f), RowShape)
            .clip(RowShape)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!switch) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                if (ticked) Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tokens.colors.accent)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 21.sp,
                fontWeight = if (ticked && !switch) FontWeight.SemiBold else FontWeight.Normal,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(description, fontSize = 15.sp, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (switch) {
            Box(
                Modifier
                    .width(64.dp)
                    .height(32.dp)
                    .background(if (ticked) tokens.colors.accent else Color.White.copy(alpha = 0.15f), ChipShape),
                contentAlignment = if (ticked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(4.dp)
                        .width(24.dp)
                        .height(24.dp)
                        .background(Color.White, ChipShape),
                )
            }
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(130.dp)
            .height(52.dp)
            .background(
                when {
                    selected -> tokens.colors.accent
                    focused -> tokens.colors.accent.copy(alpha = 0.35f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                ChipShape,
            )
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.14f), ChipShape)
            .clip(ChipShape)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 19.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) tokens.colors.onAccent else tokens.colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditorButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    danger: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused && danger -> Color(0xFFB3261E)
        focused -> tokens.colors.accent
        primary && enabled -> tokens.colors.accent.copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.07f)
    }
    Box(
        modifier = Modifier
            .width(170.dp)
            .height(58.dp)
            .background(fill, RowShape)
            .border(1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.14f), RowShape)
            .clip(RowShape)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // Save stays focusable when it cannot save yet (so the arrows still reach it); a press then does nothing.
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.35f)
                focused && !danger -> tokens.colors.onAccent
                else -> Color.White
            },
            maxLines = 1,
        )
    }
}
