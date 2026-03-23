package net.palacesoft.spotifier

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoutubeServiceTest {

    private fun parse(title: String, channelTitle: String) =
        YoutubeService.parseTrackAndArtist(title, channelTitle)

    @Test
    fun `primary - en-dash splits title and artist`() {
        val (track, artist) = parse("Blinding Lights \u2013 The Weeknd", "The Weeknd - Topic")
        assertEquals("Blinding Lights", track)
        assertEquals("The Weeknd", artist)
    }

    @Test
    fun `primary - feat variant still splits on en-dash`() {
        val (track, artist) = parse("Song (feat. Other) \u2013 Main Artist", "Main Artist - Topic")
        assertEquals("Song (feat. Other)", track)
        assertEquals("Main Artist", artist)
    }

    @Test
    fun `fallback1 - no en-dash uses channelTitle, strips Topic suffix`() {
        val (track, artist) = parse("Blinding Lights (Official Video)", "The Weeknd - Topic")
        assertEquals("Blinding Lights (Official Video)", track)
        assertEquals("The Weeknd", artist)
    }

    @Test
    fun `fallback1 - channelTitle without Topic suffix is used as-is`() {
        val (track, artist) = parse("Some Song", "ArtistChannel")
        assertEquals("Some Song", track)
        assertEquals("ArtistChannel", artist)
    }

    @Test
    fun `fallback2 - empty artist returns empty string for artist`() {
        val (track, artist) = parse("", "")
        assertEquals("", track)
        assertEquals("", artist)
    }
}
