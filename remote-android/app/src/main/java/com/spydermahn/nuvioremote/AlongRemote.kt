package com.spydermahn.nuvioremote
// FORK (Spyder): Nick's watch-along companion. See CLAUDE.md.

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** fork:watch-along - the film companion as the PC sends it (LibraryLines.parseAlong). Only what the film has reached. */
data class AlongInfo(
    /** "building", "ready" or "none". */
    val status: String,
    val title: String,
    val total: Int,
    val fromAi: Boolean,
    /** Oldest first. */
    val notes: List<AlongNote>,
    val people: List<AlongCastInfo>,
)

data class AlongNote(val atMs: Long, val kind: String, val title: String, val text: String)

data class AlongCastInfo(
    val character: String,
    val actor: String,
    val photo: String?,
    val firstMs: Long,
    val note: String,
    val aroundNow: Boolean,
)

/** What the status line under the title says. */
internal fun alongStatusLine(info: AlongInfo): String = when (info.status) {
    "building" -> "Getting the notes ready…"
    "none" -> "No notes for this one."
    else -> buildString {
        append(if (info.notes.isEmpty()) "Nothing yet" else "${info.notes.size} of ${info.total} so far")
        append(if (info.fromAi) " · written by your AI" else " · cast and facts")
    }
}

/** The people row: those around now first, then everyone else the film has reached, latest first. */
internal fun alongPeopleOrder(people: List<AlongCastInfo>): List<AlongCastInfo> =
    (people.filter { it.aroundNow } + people.filterNot { it.aroundNow }.sortedByDescending { it.firstMs })
        .distinctBy { it.character }

/**
 * The Along tab: who's in the scene, and every note so far, newest at the top, as the film on the TV reaches them.
 * Nothing ahead of the film is ever sent to the phone, so there is nothing here to spoil.
 */
@Composable
fun AlongScreen(state: UiState) {
    val g = LocalGlass.current
    val info = state.along
    if (info == null) {
        AlongEmpty(
            if (state.nowPlaying?.kind == "film") {
                "Turn on Watch-along on the PC (Settings, Pre-show, Watch-along) and the notes for this film follow here."
            } else {
                "Start a film on the TV with Watch-along on, and its notes turn up here at their scenes: how it was shot, " +
                    "the song that's playing, who that actor is. Never anything you haven't seen yet."
            },
        )
        return
    }
    var picked by rememberSaveable(info.title) { mutableStateOf<String?>(null) }
    val people = alongPeopleOrder(info.people)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                GlassLabel("Watch-along", icon = Icons.Rounded.AutoAwesome)
                Spacer(Modifier.height(8.dp))
                DisplayTitle(info.title.ifBlank { "This film" })
                Spacer(Modifier.height(4.dp))
                Text(alongStatusLine(info), color = g.textSecondary, fontSize = 14.sp)
            }
        }
        if (people.isNotEmpty()) {
            item { GlassLabel("Around now", color = g.textMuted, size = 11) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(people, key = { it.character }) { person ->
                        AlongPersonChip(person, selected = picked == person.character) {
                            picked = if (picked == person.character) null else person.character
                        }
                    }
                }
            }
            people.firstOrNull { it.character == picked }?.let { person ->
                item(key = "picked") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        Text(person.character, color = g.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(person.actor, color = g.accent, fontSize = 14.sp)
                        if (person.note.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(person.note, color = g.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                        if (person.firstMs >= 0) {
                            Spacer(Modifier.height(6.dp))
                            Text("First seen at ${formatTime(person.firstMs)}", color = g.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (info.notes.isNotEmpty()) {
            item { GlassLabel("So far", color = g.textMuted, size = 11) }
            val newestFirst = info.notes.reversed()
            itemsIndexed(newestFirst) { index, note -> AlongNoteCard(note, newest = index == 0) }
        } else if (info.status != "none") {
            item {
                Text(
                    "The first note turns up when the film reaches it.",
                    color = g.textMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        item {
            Text(
                "Only what you've already seen. Nothing ahead of the film is ever sent to the phone.",
                color = g.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AlongPersonChip(person: AlongCastInfo, selected: Boolean, onClick: () -> Unit) {
    val g = LocalGlass.current
    Column(
        Modifier.width(84.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected || person.aroundNow) g.accent else g.glassEdge, CircleShape),
        ) {
            RemoteImage(
                url = person.photo,
                fallback = null,
                placeholder = person.character.take(1).uppercase(),
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(person.character, color = g.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(person.actor, color = g.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlongNoteCard(note: AlongNote, newest: Boolean) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape, fill = if (newest) g.tint else g.glassFill, edge = if (newest) g.tintEdge else g.glassEdge)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(note.kind.uppercase(), color = g.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            Text(formatTime(note.atMs), color = g.textMuted, fontSize = 12.sp)
        }
        if (note.title.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(note.title, color = g.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(note.text, color = g.textSecondary, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun AlongEmpty(text: String) {
    val g = LocalGlass.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = g.accent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Watch-along", color = g.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text, color = g.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
    }
}
