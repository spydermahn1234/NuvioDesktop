/*
 * DEV ONLY - NOT FOR END USERS. Part of Nuvio Remote's Checks tab, Nick's own testing tool for this fork's TESTING.md.
 * Do not merge or cherry-pick it into UmbraProjects/NuvioDesktop or any release for other people (see CLAUDE.md).
 */
package com.spydermahn.nuvioremote

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/*
 * Checks (Nov 16): TESTING.md's open lines as one question at a time. Answers wait on the phone; Save writes them into
 * TESTING.md on Spyder with the GitHub token, or, without one, shares them as text to paste to Claude.
 */

data class ChecksUi(
    val loading: Boolean = true,
    val error: String? = null,
    val sections: List<CheckSection> = emptyList(),
    val answers: Map<String, CheckAnswer> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
    val hasToken: Boolean = false,
) {
    /** Each check once, though a Must test line is listed twice. */
    val open: Int get() = sections.flatMap { s -> s.items.map { it.key } }.toSet().size
    /** Answers that will change the file (Not tried only moves past a check). */
    val toSave: List<CheckAnswer> get() = answers.values.filter { it.verdict != Verdict.NotTried }
}

class ChecksViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ChecksStore(app)
    private val _state = MutableStateFlow(ChecksUi(answers = store.answers().associateBy { it.key }, hasToken = store.token.isNotBlank()))
    val state: StateFlow<ChecksUi> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { ChecklistGitHub.fetch(store.token) } }
            result.onSuccess { file ->
                val sections = Checks.parse(file.markdown)
                // An answer whose line is no longer open (ticked from the PC, or reworded) has nothing left to save.
                val stillOpen = sections.flatMap { s -> s.items.map { it.key } }.toSet()
                val kept = _state.value.answers.filterKeys { it in stillOpen }
                store.saveAnswers(kept.values.toList())
                _state.update { it.copy(loading = false, sections = sections, answers = kept) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't reach GitHub.") }
            }
        }
    }

    fun answer(item: CheckItem, verdict: Verdict, note: String = "") {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val answer = CheckAnswer(item.section, item.text, verdict, note.trim(), time)
        val next = _state.value.answers + (answer.key to answer)
        store.saveAnswers(next.values.toList())
        _state.update { it.copy(answers = next) }
    }

    fun forget(item: CheckItem) {
        val next = _state.value.answers - item.key
        store.saveAnswers(next.values.toList())
        _state.update { it.copy(answers = next) }
    }

    fun setToken(token: String) {
        store.token = token
        _state.update { it.copy(hasToken = token.isNotBlank(), message = if (token.isBlank()) "Token removed." else "Token saved. Save now writes to GitHub.") }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun shareText(): String = Checks.shareText(_state.value.toSave, today())

    /** Writes every waiting answer into TESTING.md on Spyder, in one commit; tries again if the file moved meanwhile. */
    fun save() {
        val answers = _state.value.toSave
        if (answers.isEmpty() || _state.value.saving) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    var last: Exception? = null
                    repeat(3) {
                        val file = ChecklistGitHub.fetch(store.token)
                        val out = Checks.apply(file.markdown, answers, today())
                        if (out.applied.isEmpty()) return@runCatching out
                        val n = out.applied.size
                        try {
                            ChecklistGitHub.write(store.token, out.markdown, file.sha, "TESTING: ${if (n == 1) "1 result" else "$n results"} from the phone (${today()})")
                            return@runCatching out
                        } catch (e: GitHubError) {
                            if (e.code != 409 && e.code != 422) throw e
                            last = e
                        }
                    }
                    throw last ?: IllegalStateException("Couldn't save.")
                }
            }
            outcome.onSuccess { out ->
                val done = out.applied.map { it.key }.toSet() + out.missing.map { it.key }
                val left = _state.value.answers.filterKeys { it !in done }
                store.saveAnswers(left.values.toList())
                val saved = out.applied.size
                val msg = buildString {
                    append(if (saved == 1) "Saved 1 answer to TESTING.md." else "Saved $saved answers to TESTING.md.")
                    if (out.missing.isNotEmpty()) append(" ${out.missing.size} had changed on GitHub and were dropped.")
                }
                _state.update { it.copy(saving = false, answers = left, message = msg) }
                refresh()
            }.onFailure { e ->
                _state.update { it.copy(saving = false, message = e.message ?: "Couldn't save.") }
            }
        }
    }

    private fun today() = LocalDate.now().toString()
}

@Composable
fun ChecksScreen(vm: ChecksViewModel = viewModel()) {
    val g = LocalGlass.current
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var openSection by rememberSaveable { mutableStateOf<String?>(null) }
    var tokenSheet by remember { mutableStateOf(false) }

    val section = state.sections.firstOrNull { it.title == openSection }
    if (openSection != null) BackHandler { openSection = null }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(4_000)
            vm.clearMessage()
        }
    }

    val share = {
        val text = vm.shareText()
        if (text.isNotEmpty()) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, "Send the answers to Claude"))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 14.dp)) {
        GlassLabel("Checks")
        DisplayTitle(section?.title ?: "What's left to try", Modifier.padding(top = 4.dp), size = 22, maxLines = 1)
        val waiting = state.toSave.size
        Text(
            when {
                state.loading && state.sections.isEmpty() -> "Reading TESTING.md…"
                else -> "${state.open} open" + if (waiting > 0) " · $waiting answered, not saved yet" else ""
            },
            color = g.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        // The same three tags on every view, so nothing jumps.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassTag(
                if (state.hasToken) "Save $waiting" else "Share $waiting",
                icon = if (state.hasToken) Icons.Rounded.CloudUpload else Icons.Rounded.Share,
                accent = waiting > 0,
                onClick = { if (state.hasToken) vm.save() else share() },
                onLongClick = share,
            )
            GlassTag("Refresh", icon = Icons.Rounded.Refresh, onClick = vm::refresh)
            GlassTag("GitHub", icon = Icons.Rounded.Key, accent = state.hasToken, onClick = { tokenSheet = true })
            if (state.saving || state.loading) CircularProgressIndicator(Modifier.size(18.dp), color = g.accent, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(12.dp))

        when {
            state.error != null && state.sections.isEmpty() -> InfoCard(state.error!! + "\n\nTap Refresh to try again.")
            section != null -> CheckCards(section, state, vm, onDone = { openSection = null })
            !state.loading && state.sections.isEmpty() -> InfoCard("Nothing open in TESTING.md. Everything has been tried.")
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.sections, key = { it.title }) { s ->
                    SectionRow(s, answered = s.items.count { it.key in state.answers }) { openSection = s.title }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    state.message?.let { msg ->
        Box(Modifier.fillMaxSize().padding(bottom = 12.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                msg,
                color = g.text,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .glass(RoundedCornerShape(16.dp), fill = Color(0xF01B1C22))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (tokenSheet) TokenSheet(state.hasToken, onSave = { vm.setToken(it); tokenSheet = false }, onDismiss = { tokenSheet = false })
}

@Composable
private fun InfoCard(text: String) {
    val g = LocalGlass.current
    Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text(text, color = g.textSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun SectionRow(section: CheckSection, answered: Int, onTap: () -> Unit) {
    val g = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().height(76.dp).glass(RoundedCornerShape(18.dp)).pressable { onTap() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(section.title, color = g.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                section.intro.ifEmpty { Checks.plain(section.items.first().text) },
                color = g.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${section.items.size}", color = g.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(if (answered > 0) "$answered done" else "open", color = g.textMuted, fontSize = 11.sp)
        }
    }
}

/** One check at a time: the card keeps its size whatever the text, the four answers sit under it. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CheckCards(section: CheckSection, state: ChecksUi, vm: ChecksViewModel, onDone: () -> Unit) {
    val g = LocalGlass.current
    // Opens on the first check not answered yet.
    var index by rememberSaveable(section.title) {
        mutableStateOf(section.items.indexOfFirst { it.key !in state.answers }.takeIf { it >= 0 } ?: 0)
    }
    if (index >= section.items.size) index = section.items.size - 1
    val item = section.items[index]
    val given = state.answers[item.key]
    var noteFor by remember { mutableStateOf<Verdict?>(null) }

    fun advance() {
        if (index + 1 < section.items.size) index++ else onDone()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassLabel("${index + 1} of ${section.items.size}", Modifier.weight(1f))
        IconButton(onClick = { if (index > 0) index-- }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous check", tint = if (index > 0) g.text else g.textMuted)
        }
        IconButton(onClick = { advance() }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next check", tint = g.text)
        }
    }
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (item.section != section.title) GlassLabel(item.section, color = g.accent, size = 11)
        item.parent?.let { parent ->
            Column {
                GlassLabel("Re-check of", color = g.warning, size = 11)
                Text(parent, color = g.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Text(Checks.plain(item.text), color = g.text, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        if (item.intro.isNotEmpty()) {
            Column {
                GlassLabel("Getting there", color = g.textMuted, size = 11)
                Text(item.intro, color = g.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    // The answer given so far, in a line of its own that is always there.
    Text(
        given?.let { a -> "Answered: ${a.verdict.label}" + if (a.note.isNotEmpty()) " · ${a.note}" else "" } ?: " ",
        color = when (given?.verdict) {
            Verdict.Works -> g.success
            Verdict.Broken -> g.danger
            Verdict.Followup -> g.warning
            else -> g.textMuted
        },
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).pressable { if (given != null) vm.forget(item) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnswerButton("Works", g.success, Modifier.weight(1f)) { vm.answer(item, Verdict.Works); advance() }
        AnswerButton("Broken", g.danger, Modifier.weight(1f)) { noteFor = Verdict.Broken }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnswerButton("Works, but…", g.warning, Modifier.weight(1f)) { noteFor = Verdict.Followup }
        AnswerButton("Not tried", g.textSecondary, Modifier.weight(1f)) { vm.answer(item, Verdict.NotTried); advance() }
    }

    noteFor?.let { verdict ->
        NoteSheet(
            verdict = verdict,
            start = given?.takeIf { it.verdict == verdict }?.note.orEmpty(),
            onSave = { note ->
                vm.answer(item, verdict, note)
                noteFor = null
                advance()
            },
            onDismiss = { noteFor = null },
        )
    }
}

@Composable
private fun AnswerButton(label: String, colour: Color, modifier: Modifier, onTap: () -> Unit) {
    val g = LocalGlass.current
    Box(
        modifier.height(56.dp).glass(RoundedCornerShape(18.dp), fill = colour.copy(alpha = 0.14f), edge = colour.copy(alpha = 0.5f)).pressable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = g.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** What went wrong, or what needs doing: typed or said. Saving with nothing typed is fine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(verdict: Verdict, start: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    var text by rememberSaveable { mutableStateOf(start) }
    val voice = rememberVoiceInput(
        if (verdict == Verdict.Broken) "What went wrong?" else "What needs doing?",
        onText = { said -> text = if (text.isBlank()) said else "$text $said" },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF14151A),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassLabel(if (verdict == Verdict.Broken) "Broken" else "Works, but…", color = if (verdict == Verdict.Broken) g.danger else g.warning)
            Text(
                if (verdict == Verdict.Broken) "What happened? The time is noted too, to find it in Nuvio's log." else "What needs doing?",
                color = g.textSecondary,
                fontSize = 13.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("A few words") },
                    shape = RoundedCornerShape(18.dp),
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = voice) { Icon(Icons.Rounded.Mic, contentDescription = "Say it", tint = g.accent) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTag("Cancel", icon = Icons.Rounded.Close, onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                GlassTag("Save answer", icon = Icons.Rounded.Check, accent = true, onClick = { onSave(text) })
            }
        }
    }
}

/** The GitHub token: with one, Save writes straight into TESTING.md; without, Save shares the answers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenSheet(hasToken: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF14151A),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassLabel("Save to GitHub")
            Text(
                "With a token, Save ticks the lines in TESTING.md on Spyder itself, as one commit. Make a fine-grained " +
                    "token for the repository spydermahn1234/NuvioDesktop only, with Contents: Read and write, and paste it here. " +
                    "Without one, Save shares the answers as text to paste into a Claude chat.",
                color = g.textSecondary,
                fontSize = 13.sp,
            )
            GlassTag("Make a token on GitHub", onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ChecklistGitHub.NEW_TOKEN_PAGE)))
                } catch (_: ActivityNotFoundException) {
                }
            })
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(if (hasToken) "A token is saved; paste to replace it" else "github_pat_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasToken) GlassTag("Remove token", onClick = { onSave("") })
                Spacer(Modifier.weight(1f))
                GlassTag("Save token", icon = Icons.Rounded.Check, accent = text.isNotBlank(), onClick = { if (text.isNotBlank()) onSave(text) })
            }
            Text("Kept only in this app's own storage on the phone.", color = g.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
