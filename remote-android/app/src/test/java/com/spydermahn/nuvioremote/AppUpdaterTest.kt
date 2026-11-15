package com.spydermahn.nuvioremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun readsTheBuildFromTheReleaseNotes() {
        assertEquals(57, releaseBuild("Android remote for Nuvio HTPC. Build 57. Download NuvioRemote.apk"))
        assertEquals(3, releaseBuild("Build 3."))
        assertNull(releaseBuild("Android remote for Nuvio HTPC."))
        assertNull(releaseBuild(null))
        assertNull(releaseBuild("Rebuild 12"))
    }

    @Test
    fun onlyALaterBuildIsAnUpdate() {
        assertTrue(isNewerBuild(58, 57))
        assertFalse(isNewerBuild(57, 57))
        assertFalse(isNewerBuild(40, 57))
        assertFalse(isNewerBuild(null, 57))
    }

    @Test
    fun takesTheShaFromGitHubsDigest() {
        val hex = "a".repeat(64)
        assertEquals(hex, sha256Of("sha256:${hex.uppercase()}"))
        assertNull(sha256Of("md5:abc"))
        assertNull(sha256Of("sha256:short"))
        assertNull(sha256Of(null))
    }
}
