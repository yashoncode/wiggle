package io.wiggle.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {

    @Test
    fun `a higher tag is an update`() {
        assertTrue(Updates.isNewer("v1.2", "1.1"))
        assertTrue(Updates.isNewer("1.2.1", "1.2"))
        assertTrue(Updates.isNewer("2.0", "1.9"))
    }

    @Test
    fun `the same version is not an update`() {
        assertFalse(Updates.isNewer("v1.2", "1.2"))
        assertFalse(Updates.isNewer("1.2.0", "1.2"))
    }

    @Test
    fun `an older tag is never offered`() {
        assertFalse(Updates.isNewer("v1.1", "1.2"))
        assertFalse(Updates.isNewer("1.2", "1.2.1"))
    }

    @Test
    fun `version parts are numbers, not text`() {
        // The string comparison every version check gets wrong the first time.
        assertTrue(Updates.isNewer("1.10", "1.9"))
        assertFalse(Updates.isNewer("1.9", "1.10"))
    }

    @Test
    fun `a debug build compares as its release version`() {
        assertTrue(Updates.isNewer("v1.3", "1.2-debug"))
        assertFalse(Updates.isNewer("v1.2", "1.2-debug"))
    }

    @Test
    fun `nonsense from the API is not an update`() {
        assertFalse(Updates.isNewer("", "1.2"))
        assertFalse(Updates.isNewer("latest", "1.2"))
    }
}
