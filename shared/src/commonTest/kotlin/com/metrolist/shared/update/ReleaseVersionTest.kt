package com.metrolist.shared.update

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseVersionTest {
    @Test
    fun recoveryOffersNewerReleaseCandidatesAndStableReleasesInOrder() {
        val versions = listOf("14.0.0-beta2", "14.0.0-rc6", "14.0.0-rc7", "14.0.0-rc10", "14.0.0", "14.0.1")
        versions.zipWithNext().forEach { (older, newer) ->
            assertTrue(requireNotNull(ReleaseVersion.parse(newer)) > requireNotNull(ReleaseVersion.parse(older)), "$newer > $older")
        }
        assertNull(ReleaseVersion.parse("not a release"))
    }

    /** Fork releases are "<upstream>+<custom>"; the custom part must order releases that share an upstream version. */
    @Test
    fun forkBuildMetadataOrdersReleasesOfTheSameUpstreamVersion() {
        val versions = listOf("14.0.0", "14.0.0+0.1.0", "14.0.0+0.1.9", "14.0.0+0.2.4", "14.0.0+0.10.0", "14.0.1+0.1.0")
        versions.zipWithNext().forEach { (older, newer) ->
            assertTrue(requireNotNull(ReleaseVersion.parse(newer)) > requireNotNull(ReleaseVersion.parse(older)), "$newer > $older")
        }
        // A later upstream release beats any custom number on an earlier one.
        assertTrue(requireNotNull(ReleaseVersion.parse("14.0.1+0.1.0")) > requireNotNull(ReleaseVersion.parse("14.0.0+9.9.9")))
        // A prerelease is still below its release regardless of build metadata.
        assertTrue(requireNotNull(ReleaseVersion.parse("14.0.0-rc1+0.5.0")) < requireNotNull(ReleaseVersion.parse("14.0.0+0.1.0")))
    }
}
