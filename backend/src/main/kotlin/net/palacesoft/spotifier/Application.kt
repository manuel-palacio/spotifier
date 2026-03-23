package net.palacesoft.spotifier

import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val youtubeApiKey = System.getenv("YOUTUBE_API_KEY")
        ?: error("YOUTUBE_API_KEY environment variable not set")
    val spotifyClientId = System.getenv("SPOTIFY_CLIENT_ID")
        ?: error("SPOTIFY_CLIENT_ID environment variable not set")
    val spotifyClientSecret = System.getenv("SPOTIFY_CLIENT_SECRET")
        ?: error("SPOTIFY_CLIENT_SECRET environment variable not set")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val youtube = YoutubeService(youtubeApiKey)
    val spotify = SpotifyService(spotifyClientId, spotifyClientSecret)

    embeddedServer(Netty, port = port) {
        configureRoutes(youtube, spotify)
    }.start(wait = true)
}
