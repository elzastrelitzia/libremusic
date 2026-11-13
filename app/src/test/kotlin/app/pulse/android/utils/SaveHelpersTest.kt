package app.pulse.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveHelpersTest {
    @Test
    fun `base name falls back to the video id`() {
        assertEquals("abc - Artist", SaveRequest("abc", "abc", "Artist", "", null).baseName)
        assertEquals("abc", SaveRequest("abc", "   ", null, "", null).baseName)
    }

    @Test
    fun `base name strips reserved characters`() {
        val request = SaveRequest("abc", "AC/DC: Back", "In Black", "", null)
        assertEquals("AC DC Back - In Black", request.baseName)
    }

    @Test
    fun `percent is null without a total`() {
        assertNull(savePercent(10, 0))
        assertNull(savePercent(10, -1))
    }

    @Test
    fun `percent clamps to a hundred`() {
        assertEquals(50, savePercent(1, 2))
        assertEquals(100, savePercent(200, 100))
        assertEquals(0, savePercent(-5, 100))
    }

    @Test
    fun `queue rejects a key until it is removed`() {
        val queue = SaveQueue()
        assertTrue(queue.offer("song"))
        assertFalse(queue.offer("song"))
        assertTrue(queue.contains("song"))

        queue.remove("song")
        assertFalse(queue.contains("song"))
        assertTrue(queue.offer("song"))
    }

    @Test
    fun `queue clear releases every key`() {
        val queue = SaveQueue()
        assertTrue(queue.offer("a"))
        assertTrue(queue.offer("b"))

        queue.clear()
        assertFalse(queue.contains("a"))
        assertFalse(queue.contains("b"))
        assertTrue(queue.offer("a"))
    }

    @Test
    fun `throttle allows one update per interval plus a hundred`() {
        val throttle = SaveProgressThrottle()
        assertTrue(throttle.shouldEmit(10, now = 0))
        assertFalse(throttle.shouldEmit(11, now = 100))
        assertTrue(throttle.shouldEmit(12, now = 500))
        assertFalse(throttle.shouldEmit(12, now = 900))
        assertTrue(throttle.shouldEmit(100, now = 950))
    }
}