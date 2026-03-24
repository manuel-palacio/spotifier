package net.palacesoft.spotifier

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Collections

private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_\\-]{11}$")

private val PRIVACY_POLICY = """
    <!DOCTYPE html>
    <html>
    <head><meta charset="utf-8"><title>Spotifier Privacy Policy</title></head>
    <body>
    <h1>Privacy Policy</h1>
    <p>Spotifier converts YouTube Music links to Spotify links.</p>
    <h2>Data collected</h2>
    <p>Spotifier does not collect, store, or share any personal data.
    The YouTube Music video ID from the shared link is sent to this server solely to look up
    the matching Spotify track. It is not logged or retained.</p>
    <h2>Third-party services</h2>
    <p>Track lookups use the YouTube Data API and the Spotify Web API.
    Their respective privacy policies apply.</p>
    <h2>Contact</h2>
    <p>Questions? Open an issue on the project repository.</p>
    </body>
    </html>
""".trimIndent()

private fun spotifyUrlCache(): MutableMap<String, String> = Collections.synchronizedMap(
    object : LinkedHashMap<String, String>(1000, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > 1000
    }
)

fun Application.configureRoutes(youtube: YoutubeService, search: SearchService) {
    val cache = spotifyUrlCache()

    routing {
        get("/health") {
            call.respondText("OK")
        }

        get("/privacy") {
            call.respondText(PRIVACY_POLICY, ContentType.Text.Html)
        }

        get("/spotifier") {
            val videoId = call.request.queryParameters["videoId"]
            if (videoId == null || !VIDEO_ID_REGEX.matches(videoId)) {
                call.respond(HttpStatusCode.BadRequest, "Missing or invalid videoId")
                return@get
            }

            cache[videoId]?.let {
                call.respondText(it)
                return@get
            }

            try {
                val spotifyUrl = resolveSpotifyUrl(videoId, youtube, search)
                    ?: run {
                        call.respond(HttpStatusCode.NotFound, "No matching track found on Spotify")
                        return@get
                    }
                cache[videoId] = spotifyUrl
                call.respondText(spotifyUrl)
            } catch (e: Exception) {
                application.log.error("Upstream error for videoId=$videoId", e)
                call.respond(HttpStatusCode.BadGateway, "Upstream API error: ${e.message}")
            }
        }
    }
}

private fun resolveSpotifyUrl(
    videoId: String,
    youtube: YoutubeService,
    search: SearchService
): String? {
    val (track, artist, rawTitle) = youtube.getTrackAndArtist(videoId)
    return search.findSpotifyUrl(track, artist, rawTitle)
}
