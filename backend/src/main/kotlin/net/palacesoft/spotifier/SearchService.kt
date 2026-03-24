package net.palacesoft.spotifier

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

open class SearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    open fun findSpotifyUrl(track: String, artist: String, rawTitle: String = ""): String? {
        val query = buildQuery(track, artist, rawTitle)
        if (query.isBlank()) return null
        val encoded = URLEncoder.encode("$query site:open.spotify.com/track", StandardCharsets.UTF_8)
        val body = fetchSearchResults(encoded)
        return SPOTIFY_TRACK_REGEX.find(body)?.value
    }

    private fun fetchSearchResults(encodedQuery: String): String {
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encodedQuery")
            .header("User-Agent", "Mozilla/5.0 (compatible; Spotifier/2.0)")
            .build()
        return client.newCall(request).execute().use { it.body?.string() ?: "" }
    }

    private fun buildQuery(track: String, artist: String, rawTitle: String): String = when {
        track.isNotBlank() && artist.isNotBlank() -> "$artist $track"
        track.isNotBlank() -> track
        artist.isNotBlank() -> artist
        rawTitle.isNotBlank() -> rawTitle
        else -> ""
    }

    companion object {
        private val SPOTIFY_TRACK_REGEX = Regex("https://open\\.spotify\\.com/track/[A-Za-z0-9]+")
    }
}
