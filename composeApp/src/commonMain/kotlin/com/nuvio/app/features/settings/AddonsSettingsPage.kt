package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.nuvio.app.features.addons.AddonsSettingsPageContent

internal fun LazyListScope.addonsSettingsContent(isTablet: Boolean = true) {
    item {
        AddonsSettingsPageContent(
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // fork:local-addons - AIOStreams and AIOMetadata run by Nuvio itself.
    localAddonsSettingsContent(isTablet)
}
