package net.palacesoft.spotifier

import com.google.gson.JsonParser
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

open class SpotifyService(
    private val clientId: String,
    private val clientSecret: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var token: String = ""
    @Volatile private var tokenExpiresAt: Instant = Instant.EPOCH

    /**
     * Returns Spotify track URL or null if not found. Throws on upstream error.
     * [rawTitle] is used as a free-text fallback query when both track and artist are blank (Fallback 2).
     */
    open fun findTrackUrl(track: String, artist: String, rawTitle: String = ""): String? {
        val query = buildQuery(track, artist, rawTitle)
        if (query.isBlank()) return null
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=1"
        val response = executeWithToken(url)
        val items = JsonParser.parseString(response)
            .asJsonObject
            .getAsJsonObject("tracks")
            .getAsJsonArray("items")
        if (items.size() == 0) return null
        return items[0].asJsonObject
            .getAsJsonObject("external_urls")
            .get("spotify").asString
    }

    private fun buildQuery(track: String, artist: String, rawTitle: String): String = when {
        track.isNotBlank() && artist.isNotBlank() -> "track:$track artist:$artist"
        track.isNotBlank() -> track
        artist.isNotBlank() -> artist
        rawTitle.isNotBlank() -> rawTitle  // Fallback 2: free-text search
        else -> ""
    }

    private fun executeWithToken(url: String): String {
        ensureValidToken()
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token").build()
        val response = client.newCall(request).execute()
        // On 401, force-refresh and retry once
        if (response.code == 401) {
            response.close()
            refreshToken()
            val retry = Request.Builder().url(url)
                .header("Authorization", "Bearer $token").build()
            return client.newCall(retry).execute().use { it.body?.string() ?: "" }
        }
        return response.use { it.body?.string() ?: "" }
    }

    private fun ensureValidToken() {
        if (Instant.now().isAfter(tokenExpiresAt.minusSeconds(60))) refreshToken()
    }

    private fun refreshToken() {
        val body = FormBody.Builder()
            .add("grant_type", "client_credentials").build()
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", Credentials.basic(clientId, clientSecret))
            .post(body).build()
        val responseBody = client.newCall(request).execute().use { it.body?.string() ?: "" }
        val json = JsonParser.parseString(responseBody).asJsonObject
        token = json.get("access_token").asString
        val expiresIn = json.get("expires_in").asLong
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn)
    }
}
