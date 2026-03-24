package net.palacesoft.spotifier

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpotifierRouteTest {

    private fun makeYoutube(result: Triple<String, String, String>) = object : YoutubeService("fake-key") {
        override fun getTrackAndArtist(videoId: String) = result
    }

    private fun makeSearch(result: String?) = object : SearchService() {
        override fun findSpotifyUrl(track: String, artist: String, rawTitle: String) = result
    }

    @Test
    fun `health endpoint returns 200 OK`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `missing videoId returns 400`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/spotifier")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `invalid videoId (too short) returns 400`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/spotifier?videoId=short")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `valid videoId returns spotify url`() = testApplication {
        val expected = "https://open.spotify.com/track/6UelLqGlWMcVH1E5c4H7lY"
        application {
            configureRoutes(
                makeYoutube(Triple("Blinding Lights", "The Weeknd", "Blinding Lights \u2013 The Weeknd")),
                makeSearch(expected)
            )
        }
        val response = client.get("/spotifier?videoId=4NRXx6U8ABQ")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, response.bodyAsText())
    }

    @Test
    fun `search not found returns 404`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch(null)) }
        val response = client.get("/spotifier?videoId=4NRXx6U8ABQ")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `youtube error returns 502`() = testApplication {
        val failingYoutube = object : YoutubeService("fake-key") {
            override fun getTrackAndArtist(videoId: String): Triple<String, String, String> = error("API error")
        }
        application { configureRoutes(failingYoutube, makeSearch("url")) }
        val response = client.get("/spotifier?videoId=4NRXx6U8ABQ")
        assertEquals(HttpStatusCode.BadGateway, response.status)
    }
}
