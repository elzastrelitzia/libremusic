package app.pulse.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDownloadsTest {
    @Test
    fun `download id round-trips its file name`() {
        assertEquals("local:download:abc.webm", downloadSongId("abc.webm"))
        assertEquals("abc.webm", downloadFileName("local:download:abc.webm"))
    }

    @Test
    fun `unsafe download id returns null`() {
        assertNull(downloadFileName("local:download:../abc.webm"))
        assertNull(downloadFileName("local:download:a/b.webm"))
        assertNull(downloadFileName("local:123"))
        assertNull(downloadFileName("abc.webm"))
    }

    @Test
    fun `existing file ignores part files and non-audio extensions`() {
        val names = listOf("abc.webm.part", "abc.webp", "abc.webm")
        assertEquals("abc.webm", pickFileName(names, "abc", setOf("webm")))
    }

    @Test
    fun `file picker requires the exact base name`() {
        val names = listOf("abc.extra.webm", "abc.webm")
        assertEquals("abc.webm", pickFileName(names, "abc", setOf("webm")))
    }

    @Test
    fun `thumbnail picker ignores audio files`() {
        val names = listOf("abc.webm", "abc.webp")
        assertEquals("abc.webp", pickFileName(names, "abc", setOf("webp")))
    }

    @Test
    fun `download base name joins title and artist`() {
        assertEquals("Song - Artist", downloadBaseName("Song", "Artist"))
        assertEquals("Song", downloadBaseName("Song", null))
        assertEquals("", downloadBaseName("   ", null))
    }

    @Test
    fun `download base name strips path and reserved characters`() {
        assertEquals("AC DC Back In Black", downloadBaseName("AC/DC: Back In Black", null))
        assertEquals("a b c - c", downloadBaseName("a\\b|c?", "c"))
        assertEquals("Title", downloadBaseName("../Title...", null))
        assertEquals(60, downloadBaseName("x".repeat(300), null).length)
    }
}