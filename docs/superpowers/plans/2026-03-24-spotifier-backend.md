# Spotifier Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and deploy a Ktor REST API on fly.io that accepts a YouTube Music video ID and returns the matching Spotify track URL.

**Architecture:** Single Ktor app with three service classes — `YoutubeService` fetches the video title/artist via YouTube Data API v3, `SpotifyService` manages the client-credentials token lifecycle and searches the Spotify catalog, and `SpotifierRoute` wires them together with an in-memory LRU cache. One additional `GET /health` endpoint for fly.io health checks.

**Tech Stack:** Kotlin 1.9, Ktor 2.x, OkHttp 4.x, Gson, Gradle (Kotlin DSL) with version catalog, Docker, fly.io

---

## File Map

| File | Responsibility |
|------|---------------|
| `backend/settings.gradle.kts` | Module name declaration |
| `backend/build.gradle.kts` | Dependencies, shadow JAR plugin for fat jar |
| `backend/gradle/libs.versions.toml` | Centralized version catalog |
| `backend/src/main/kotlin/net/palacesoft/spotifier/Application.kt` | Ktor engine setup, route registration |
| `backend/src/main/kotlin/net/palacesoft/spotifier/SpotifierRoute.kt` | `GET /spotifier` handler + LRU cache |
| `backend/src/main/kotlin/net/palacesoft/spotifier/YoutubeService.kt` | YouTube Data API v3 call + title/artist parsing |
| `backend/src/main/kotlin/net/palacesoft/spotifier/SpotifyService.kt` | Token lifecycle + Spotify search |
| `backend/src/test/kotlin/net/palacesoft/spotifier/YoutubeServiceTest.kt` | Unit tests for title parsing logic |
| `backend/src/test/kotlin/net/palacesoft/spotifier/SpotifierRouteTest.kt` | Ktor test-engine tests for route behavior |
| `backend/Dockerfile` | Multi-stage Docker build |
| `backend/fly.toml` | fly.io app config with health check |

---

## Task 1: Gradle project scaffold

**Files:**
- Create: `backend/settings.gradle.kts`
- Create: `backend/build.gradle.kts`
- Create: `backend/gradle/libs.versions.toml`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "spotifier-backend"
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "1.9.23"
ktor = "2.3.9"
okhttp = "4.12.0"
gson = "2.10.1"
logback = "1.4.14"
junit = "5.10.2"

[libraries]
ktor-server-netty         = { module = "io.ktor:ktor-server-netty",          version.ref = "ktor" }
ktor-server-core          = { module = "io.ktor:ktor-server-core",           version.ref = "ktor" }
ktor-server-content-nego  = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-serialization-gson   = { module = "io.ktor:ktor-serialization-gson",    version.ref = "ktor" }
ktor-server-test-host     = { module = "io.ktor:ktor-server-test-host",      version.ref = "ktor" }
okhttp                    = { module = "com.squareup.okhttp3:okhttp",         version.ref = "okhttp" }
gson                      = { module = "com.google.code.gson:gson",           version.ref = "gson" }
logback                   = { module = "ch.qos.logback:logback-classic",      version.ref = "logback" }
junit-jupiter             = { module = "org.junit.jupiter:junit-jupiter",     version.ref = "junit" }
kotlin-test               = { module = "org.jetbrains.kotlin:kotlin-test",    version.ref = "kotlin" }

[plugins]
kotlin-jvm    = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
shadow        = { id = "com.github.johnrengelman.shadow",  version = "8.1.1" }
```

- [ ] **Step 3: Create `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

group = "net.palacesoft.spotifier"
version = "1.0.0"

application {
    mainClass.set("net.palacesoft.spotifier.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.nego)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("spotifier-backend")
    archiveClassifier.set("")
    archiveVersion.set("")
}
```

- [ ] **Step 4: Verify the project builds (no sources yet — just check Gradle resolves)**

```bash
cd backend && ./gradlew dependencies --configuration runtimeClasspath
```
Expected: dependency tree printed, no errors.

- [ ] **Step 5: Commit**

```bash
git add backend/
git commit -m "chore: scaffold Ktor backend Gradle project"
```

---

## Task 2: YoutubeService — title parsing unit tests + implementation

**Files:**
- Create: `backend/src/main/kotlin/net/palacesoft/spotifier/YoutubeService.kt`
- Create: `backend/src/test/kotlin/net/palacesoft/spotifier/YoutubeServiceTest.kt`

This task covers only the parsing logic (pure functions). The HTTP call to YouTube is tested via integration in Task 4.

- [ ] **Step 1: Create the test file**

```kotlin
package net.palacesoft.spotifier

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoutubeServiceTest {

    private fun parse(title: String, channelTitle: String) =
        YoutubeService.parseTrackAndArtist(title, channelTitle)

    @Test
    fun `primary - en-dash splits title and artist`() {
        val (track, artist) = parse("Blinding Lights \u2013 The Weeknd", "The Weeknd - Topic")
        assertEquals("Blinding Lights", track)
        assertEquals("The Weeknd", artist)
    }

    @Test
    fun `primary - feat variant still splits on en-dash`() {
        val (track, artist) = parse("Song (feat. Other) \u2013 Main Artist", "Main Artist - Topic")
        assertEquals("Song (feat. Other)", track)
        assertEquals("Main Artist", artist)
    }

    @Test
    fun `fallback1 - no en-dash uses channelTitle, strips Topic suffix`() {
        val (track, artist) = parse("Blinding Lights (Official Video)", "The Weeknd - Topic")
        assertEquals("Blinding Lights (Official Video)", track)
        assertEquals("The Weeknd", artist)
    }

    @Test
    fun `fallback1 - channelTitle without Topic suffix is used as-is`() {
        val (track, artist) = parse("Some Song", "ArtistChannel")
        assertEquals("Some Song", track)
        assertEquals("ArtistChannel", artist)
    }

    @Test
    fun `fallback2 - empty artist returns empty string for artist`() {
        val (track, artist) = parse("", "")
        assertEquals("", track)
        assertEquals("", artist)
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure (no source yet)**

```bash
cd backend && ./gradlew test 2>&1 | tail -20
```
Expected: compilation error — `YoutubeService` not found.

- [ ] **Step 3: Create `YoutubeService.kt`**

```kotlin
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
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
cd backend && ./gradlew test --tests "net.palacesoft.spotifier.YoutubeServiceTest" -i 2>&1 | tail -20
```
Expected: `5 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/
git commit -m "feat: add YoutubeService with title parsing"
```

---

## Task 3: SpotifyService — token lifecycle + search

**Files:**
- Create: `backend/src/main/kotlin/net/palacesoft/spotifier/SpotifyService.kt`

No unit tests for this class — token fetch and HTTP search are both external I/O; they are covered by the route integration test in Task 5.

- [ ] **Step 1: Create `SpotifyService.kt`**

```kotlin
package net.palacesoft.spotifier

import com.google.gson.JsonParser
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

class SpotifyService(
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
```

- [ ] **Step 2: Verify it compiles**

```bash
cd backend && ./gradlew compileKotlin 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/kotlin/net/palacesoft/spotifier/SpotifyService.kt
git commit -m "feat: add SpotifyService with token lifecycle"
```

---

## Task 4: Application entry point + routes

**Files:**
- Create: `backend/src/main/kotlin/net/palacesoft/spotifier/Application.kt`
- Create: `backend/src/main/kotlin/net/palacesoft/spotifier/SpotifierRoute.kt`

- [ ] **Step 1: Create `SpotifierRoute.kt`**

```kotlin
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
```

- [ ] **Step 2: Create `Application.kt`**

```kotlin
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
```

- [ ] **Step 3: Verify build produces fat JAR**

```bash
cd backend && ./gradlew shadowJar 2>&1 | tail -10
ls -lh build/libs/spotifier-backend.jar
```
Expected: `BUILD SUCCESSFUL` and a JAR file of several MB.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/net/palacesoft/spotifier/
git commit -m "feat: add Ktor routes and application entry point"
```

---

## Task 5: Route unit tests

**Files:**
- Create: `backend/src/test/kotlin/net/palacesoft/spotifier/SpotifierRouteTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package net.palacesoft.spotifier

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpotifierRouteTest {

    private fun makeYoutube(result: Triple<String, String, String>) = object : YoutubeService("fake-key") {
        override fun getTrackAndArtist(videoId: String) = result
    }

    private fun makeSpotify(result: String?) = object : SpotifyService("id", "secret") {
        override fun findTrackUrl(track: String, artist: String, rawTitle: String) = result
    }

    @Test
    fun `health endpoint returns 200 OK`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSpotify("url")) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `missing videoId returns 400`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSpotify("url")) }
        val response = client.get("/spotifier")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `invalid videoId (too short) returns 400`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSpotify("url")) }
        val response = client.get("/spotifier?videoId=short")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `valid videoId returns spotify url`() = testApplication {
        val expected = "https://open.spotify.com/track/6UelLqGlWMcVH1E5c4H7lY"
        application {
            configureRoutes(
                makeYoutube(Triple("Blinding Lights", "The Weeknd", "Blinding Lights \u2013 The Weeknd")),
                makeSpotify(expected)
            )
        }
        val response = client.get("/spotifier?videoId=4NRXx6U8ABQ")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, response.bodyAsText())
    }

    @Test
    fun `spotify not found returns 404`() = testApplication {
        application { configureRoutes(makeYoutube(Triple("t", "a", "t")), makeSpotify(null)) }
        val response = client.get("/spotifier?videoId=4NRXx6U8ABQ")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `youtube error returns 502`() = testApplication {
        val failingYoutube = object : YoutubeService("fake-key") {
            override fun getTrackAndArtist(videoId: String): Triple<String, String, String> = error("API error")
        }
        application { configureRoutes(failingYoutube, makeSpotify("url")) }
        val response = client.get("/spotifier?videoId=4NRXx6U8ABQ")
        assertEquals(HttpStatusCode.BadGateway, response.status)
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure (classes not yet `open`)**

```bash
cd backend && ./gradlew test --tests "net.palacesoft.spotifier.SpotifierRouteTest" 2>&1 | tail -20
```
Expected: compile error — `YoutubeService` and `SpotifyService` cannot be subclassed (Kotlin classes are `final` by default).

- [ ] **Step 3: Make service classes open for testability**

In `YoutubeService.kt`, change:
```kotlin
class YoutubeService(private val apiKey: String) {
```
to:
```kotlin
open class YoutubeService(private val apiKey: String) {
```

In `SpotifyService.kt`, change:
```kotlin
class SpotifyService(
```
to:
```kotlin
open class SpotifyService(
```

- [ ] **Step 4: Run all tests — expect all pass**

```bash
cd backend && ./gradlew test 2>&1 | tail -20
```
Expected: all tests pass (YoutubeServiceTest + SpotifierRouteTest).

- [ ] **Step 5: Commit**

```bash
git add backend/src/
git commit -m "test: add route tests for SpotifierRoute"
```

---

## Task 6: Docker + fly.io deployment

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/fly.toml`

- [ ] **Step 1: Create `Dockerfile`**

```dockerfile
# Build stage
FROM gradle:8.6-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/spotifier-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Create `fly.toml`**

```toml
app = "spotifier"
primary_region = "ams"

[build]

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 0

[[vm]]
  memory = "256mb"
  cpu_kind = "shared"
  cpus = 1

[[checks]]
  name = "health"
  type = "http"
  interval = "30s"
  timeout = "5s"
  method = "GET"
  path = "/health"
  grace_period = "10s"
```

- [ ] **Step 3: Build and verify Docker image locally**

```bash
cd backend && docker build -t spotifier-backend .
docker run --rm -e YOUTUBE_API_KEY=test -e SPOTIFY_CLIENT_ID=test -e SPOTIFY_CLIENT_SECRET=test -p 8080:8080 spotifier-backend &
sleep 3 && curl http://localhost:8080/health
```
Expected: `OK`

Kill the container after testing:
```bash
docker ps | grep spotifier-backend | awk '{print $1}' | xargs docker stop
```

- [ ] **Step 4: Launch app on fly.io (first deploy only)**

```bash
cd backend
fly launch --no-deploy --name spotifier --region ams
```
This creates the fly.io app without deploying. Accept the existing `fly.toml` when prompted.

- [ ] **Step 5: Set secrets on fly.io**

```bash
fly secrets set \
  YOUTUBE_API_KEY=<your-youtube-api-key> \
  SPOTIFY_CLIENT_ID=<your-spotify-client-id> \
  SPOTIFY_CLIENT_SECRET=<your-spotify-client-secret>
```

- [ ] **Step 6: Deploy**

```bash
cd backend && fly deploy
```
Expected: deployment succeeds, health check passes.

- [ ] **Step 7: Smoke test the live deployment**

```bash
# Replace 4NRXx6U8ABQ with any real YouTube Music video ID
curl "https://spotifier.fly.dev/spotifier?videoId=4NRXx6U8ABQ"
```
Expected: a `https://open.spotify.com/track/...` URL returned.

- [ ] **Step 8: Commit**

```bash
git add backend/Dockerfile backend/fly.toml
git commit -m "feat: add Dockerfile and fly.toml for backend deployment"
```
