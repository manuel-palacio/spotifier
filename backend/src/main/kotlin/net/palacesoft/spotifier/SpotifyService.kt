package net.palacesoft.spotifier

import com.google.gson.JsonParser
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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

    private val tokenLock = Any()
    private var token: String = ""
    private var tokenExpiresAt: Instant = Instant.EPOCH

    open fun findTrackUrl(track: String, artist: String, rawTitle: String = ""): String? {
        val query = buildQuery(track, artist, rawTitle)
        if (query.isBlank()) return null
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "https://api.spotify.com/v1/search?q=$encodedQuery&type=track&limit=1"
        val responseBody = executeWithFreshToken(url)
        val items = JsonParser.parseString(responseBody)
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
        rawTitle.isNotBlank() -> rawTitle
        else -> ""
    }

    private fun executeWithFreshToken(url: String): String {
        val accessToken = validToken()
        val response = client.newCall(requestWithToken(url, accessToken)).execute()
        if (response.code == 401) {
            response.close()
            val refreshedToken = forceRefreshToken()
            return client.newCall(requestWithToken(url, refreshedToken)).execute()
                .use { readBody(it) }
        }
        return response.use { readBody(it) }
    }

    private fun requestWithToken(url: String, accessToken: String) =
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()

    private fun readBody(response: okhttp3.Response): String {
        check(response.isSuccessful) { "Spotify API error ${response.code}" }
        return response.body?.string() ?: error("Empty response body from Spotify")
    }

    private fun validToken(): String = synchronized(tokenLock) {
        if (tokenNeedsRefresh()) refreshToken()
        token
    }

    private fun forceRefreshToken(): String = synchronized(tokenLock) {
        refreshToken()
        token
    }

    private fun tokenNeedsRefresh() = Instant.now().isAfter(tokenExpiresAt.minusSeconds(60))

    private fun refreshToken() {
        val body = FormBody.Builder().add("grant_type", "client_credentials").build()
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", Credentials.basic(clientId, clientSecret))
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.use { res ->
            check(res.isSuccessful) { "Spotify token endpoint error ${res.code}" }
            res.body?.string() ?: error("Empty token response from Spotify")
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        token = json.get("access_token").asString
        tokenExpiresAt = Instant.now().plusSeconds(json.get("expires_in").asLong)
    }
}
