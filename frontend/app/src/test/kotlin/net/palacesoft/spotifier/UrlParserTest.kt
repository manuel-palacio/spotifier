package net.palacesoft.spotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlParserTest {

    @Test
    fun `extracts video ID from standard YouTube Music URL`() {
        val url = "https://music.youtube.com/watch?v=4NRXx6U8ABQ"
        assertEquals("4NRXx6U8ABQ", UrlParser.extractVideoId(url))
    }

    @Test
    fun `extracts video ID when extra query params present`() {
        val url = "https://music.youtube.com/watch?v=4NRXx6U8ABQ&list=PLsome&si=abc"
        assertEquals("4NRXx6U8ABQ", UrlParser.extractVideoId(url))
    }

    @Test
    fun `returns null for non-watch URL (playlist)`() {
        val url = "https://music.youtube.com/playlist?list=PLsomething"
        assertNull(UrlParser.extractVideoId(url))
    }

    @Test
    fun `returns null when v param missing`() {
        val url = "https://music.youtube.com/watch"
        assertNull(UrlParser.extractVideoId(url))
    }

    @Test
    fun `returns null for unrelated URL`() {
        val url = "https://www.google.com"
        assertNull(UrlParser.extractVideoId(url))
    }
}
