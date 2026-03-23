package net.palacesoft.spotifier

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Collections

private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_\\-]{11}$")

/** Thread-safe LRU cache: videoId -> Spotify URL */
private val cache: MutableMap<String, String> = Collections.synchronizedMap(
    object : LinkedHashMap<String, String>(1000, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > 1000
    }
)

fun Application.configureRoutes(youtube: YoutubeService, spotify: SpotifyService) {
    routing {
        get("/health") {
            call.respondText("OK")
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
                val (track, artist, rawTitle) = youtube.getTrackAndArtist(videoId)
                val spotifyUrl = spotify.findTrackUrl(track, artist, rawTitle)
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
