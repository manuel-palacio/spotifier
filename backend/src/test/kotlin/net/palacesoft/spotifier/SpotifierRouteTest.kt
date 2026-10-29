package net.palacesoft.spotifier

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `root serves the lookup page as HTML`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/")
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
    }

    @Test
    fun `lookup page contains the video input field`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/")
        assertTrue(response.bodyAsText().contains("id=\"video-input\""))
    }

    @Test
    fun `lookup page links the web app manifest`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/")
        assertTrue(response.bodyAsText().contains("rel=\"manifest\" href=\"/manifest.webmanifest\""))
    }

    @Test
    fun `manifest is served as a web app manifest`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/manifest.webmanifest")
        assertEquals(ContentType("application", "manifest+json"), response.contentType()?.withoutParameters())
    }

    @Test
    fun `manifest declares a share target`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/manifest.webmanifest")
        assertTrue(response.bodyAsText().contains("\"share_target\""))
    }

    @Test
    fun `service worker is served as JavaScript`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val response = client.get("/sw.js")
        assertEquals(ContentType.Application.JavaScript, response.contentType()?.withoutParameters())
    }

    @Test
    fun `app icons are served as PNG`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val statuses = listOf("/icon-192.png", "/icon-512.png", "/apple-touch-icon.png")
            .map { client.get(it).contentType()?.withoutParameters() }
        assertEquals(List(3) { ContentType.Image.PNG }, statuses)
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
    fun `lookups beyond the per-visitor limit return 429`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val statuses = (1..11).map { lookUpFrom("1.1.1.1").status }
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `lookups within the per-visitor limit succeed`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val statuses = (1..10).map { lookUpFrom("1.1.1.1").status }
        assertEquals(List(10) { HttpStatusCode.OK }, statuses)
    }

    @Test
    fun `per-visitor limit is counted separately for each visitor`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        repeat(11) { lookUpFrom("1.1.1.1") }
        assertEquals(HttpStatusCode.OK, lookUpFrom("2.2.2.2").status)
    }

    @Test
    fun `lookups beyond the overall hourly limit return 429`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val statuses = (1..301).map { lookUpFrom("10.0.${it / 10}.${it % 10}").status }
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `lookups within the overall hourly limit succeed`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        val statuses = (1..300).map { lookUpFrom("10.0.${it / 10}.${it % 10}").status }
        assertTrue(statuses.all { it == HttpStatusCode.OK })
    }

    @Test
    fun `lookup page is not rate limited`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSearch("url")) }
        repeat(11) { lookUpFrom("1.1.1.1") }
        val response = client.get("/") { header("Fly-Client-IP", "1.1.1.1") }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private suspend fun ApplicationTestBuilder.lookUpFrom(clientIp: String) =
        client.get("/spotifier?videoId=4NRXx6U8ABQ") { header("Fly-Client-IP", clientIp) }

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
