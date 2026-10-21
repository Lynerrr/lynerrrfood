package com.lynerrr.food.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleasesTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(GitHubReleases.isNewer("1.1", "1.0"))
        assertTrue(GitHubReleases.isNewer("v1.10", "1.9"))
        assertTrue(GitHubReleases.isNewer("2.0", "1.9.9"))
        assertTrue(GitHubReleases.isNewer("1.0.1", "1.0"))
        assertFalse(GitHubReleases.isNewer("1.0", "1.0"))
        assertFalse(GitHubReleases.isNewer("1.0.0", "1.0"))
        assertFalse(GitHubReleases.isNewer("0.9", "1.0"))
        assertFalse(GitHubReleases.isNewer("1.1-beta", "1.1"))
    }
}
