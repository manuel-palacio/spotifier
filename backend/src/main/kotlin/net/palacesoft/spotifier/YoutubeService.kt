package net.palacesoft.spotifier

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class YoutubeService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Returns (trackName, artistName, rawTitle) or throws on HTTP/API error. */
    open fun getTrackAndArtist(videoId: String): Triple<String, String, String> {
        val url = "https://www.googleapis.com/youtube/v3/videos" +
                "?id=$videoId&part=snippet&key=$apiKey"
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val body = response.use { it.body?.string() ?: "" }
        val items = JsonParser.parseString(body).asJsonObject
            .getAsJsonArray("items")
        if (items == null || items.size() == 0) error("Video not found: $videoId")
        val snippet = items[0].asJsonObject.getAsJsonObject("snippet")
        val title = snippet.get("title").asString
        val channelTitle = snippet.get("channelTitle").asString
        val (track, artist) = parseTrackAndArtist(title, channelTitle)
        return Triple(track, artist, title)
    }

    companion object {
        /** Pure parsing logic — extracted for unit testability. */
        fun parseTrackAndArtist(title: String, channelTitle: String): Pair<String, String> {
            // Primary: split on en-dash U+2013
            val enDash = '\u2013'
            if (title.contains(enDash)) {
                val parts = title.split(enDash, limit = 2)
                return parts[0].trim() to parts[1].trim()
            }
            // Fallback 1: use channelTitle, strip " - Topic" suffix
            val artist = channelTitle.removeSuffix(" - Topic").trim()
            return title.trim() to artist
        }
    }
}
