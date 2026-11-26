package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * fork:shelf-home - the Kodi-style home (plan in design-notes/home-shelves.md).
 *
 * Phase 1: still a placeholder, but it lists the tabs and shelves the model built from the classic
 * home's own rows, so the mapping can be checked on the TV before any of the real UI exists.
 * Phase 2 replaces this with the slash menu, the shelf list and the focused shelf.
 */
@Composable
fun ShelfHome(
    model: ShelfHomeModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 72.dp, vertical = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Shelf home",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Coming together: these are the tabs and shelves it will show. " +
                "Turn it off in Settings > Home screen > Shelf home to get the classic home back.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Text(
            text = model.tabs.joinToString("  /  ") { it.title },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        for (tab in model.tabs) {
            if (tab.kind == ShelfTabKind.Library) continue
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${tab.title} · ${tab.shelves.size} shelves",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                for (shelf in tab.shelves) {
                    Text(
                        text = "    ${shelf.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
