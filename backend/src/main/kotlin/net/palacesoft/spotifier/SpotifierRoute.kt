package net.palacesoft.spotifier

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Collections
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_\\-]{11}$")
private val PER_VISITOR_LIMIT = RateLimitName("per-visitor")
private val OVERALL_LIMIT = RateLimitName("overall")

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
    <p>Track lookups use the YouTube Data API to read the track title and artist,
    and DuckDuckGo search to find the matching Spotify link. Only the video ID is sent
    to YouTube, and only the track title and artist are sent to DuckDuckGo.
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
    installLookupRateLimits()

    routing {
        staticResources("/", "static")

        get("/health") {
            call.respondText("OK")
        }

        get("/privacy") {
            call.respondText(PRIVACY_POLICY, ContentType.Text.Html)
        }

        rateLimit(PER_VISITOR_LIMIT) {
            rateLimit(OVERALL_LIMIT) {
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
    }
}

// The overall limit keeps lookups under the YouTube Data API's free daily quota (10,000 units, 1 per lookup).
private fun Application.installLookupRateLimits() {
    install(RateLimit) {
        register(PER_VISITOR_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
        }
        register(OVERALL_LIMIT) {
            rateLimiter(limit = 300, refillPeriod = 1.hours)
        }
    }
}

// fly.io's proxy sets Fly-Client-IP to the real visitor address; remoteHost would be the proxy.
private fun ApplicationCall.clientIp(): String =
    request.headers["Fly-Client-IP"] ?: request.origin.remoteHost

private fun resolveSpotifyUrl(
    videoId: String,
    youtube: YoutubeService,
    search: SearchService
): String? {
    val (track, artist, rawTitle) = youtube.getTrackAndArtist(videoId)
    return search.findSpotifyUrl(track, artist, rawTitle)
}
