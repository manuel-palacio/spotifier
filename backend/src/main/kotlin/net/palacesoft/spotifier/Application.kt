package net.palacesoft.spotifier

import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val youtubeApiKey = System.getenv("YOUTUBE_API_KEY")
        ?: error("YOUTUBE_API_KEY environment variable not set")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    val youtube = YoutubeService(youtubeApiKey)
    val search = SearchService()

    embeddedServer(Netty, port = port) {
        configureRoutes(youtube, search)
    }.start(wait = true)
}
