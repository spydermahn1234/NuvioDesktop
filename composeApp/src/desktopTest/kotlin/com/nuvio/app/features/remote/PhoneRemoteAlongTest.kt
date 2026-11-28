package com.nuvio.app.features.remote
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import com.nuvio.app.features.watchalong.AlongKind
import com.nuvio.app.features.watchalong.AlongMoment
import com.nuvio.app.features.watchalong.AlongPerson
import com.nuvio.app.features.watchalong.AlongStatus
import com.nuvio.app.features.watchalong.RemoteWatchAlong
import kotlin.test.Test
import kotlin.test.assertEquals

// fork:watch-along. The same line is read on the phone by AlongLineTest (remote-android).
class PhoneRemoteAlongTest {
    @Test
    fun theCompanionTravelsAsOneLine() {
        val line = PhoneRemoteLibraryLines.alongLine(
            RemoteWatchAlong(
                title = "Jurassic Park",
                status = AlongStatus.Ready,
                positionMs = 0,
                total = 30,
                moments = listOf(AlongMoment(600_000, AlongKind.Music, "Theme", "John Williams, 1993.")),
                people = listOf(AlongPerson("Ellie Sattler", "Laura Dern", "https://x/p.jpg", 750_000, "A palaeobotanist.")),
                aroundNow = setOf("Ellie Sattler"),
                fromAi = true,
            ),
        )
        assertEquals(
            "ALONG 1 ready Jurassic%20Park 30 1 600000,Music,Theme,John%20Williams%2C%201993. " +
                "Ellie%20Sattler,Laura%20Dern,https%3A%2F%2Fx%2Fp.jpg,750000,A%20palaeobotanist.,1",
            line,
        )
        assertEquals("ALONG 0", PhoneRemoteLibraryLines.alongLine(null))
    }
}
