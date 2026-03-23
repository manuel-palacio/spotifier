# Spotifier Modernization Design

**Date:** 2026-03-24
**Status:** Approved

## Overview

Modernize Spotifier as a Kotlin Android app that intercepts YouTube Music share intents, finds the equivalent Spotify track via a backend API, and presents the Spotify link in the Android share sheet — ready to forward via WhatsApp, Telegram, or any other app.

## Architecture

```
YouTube Music app
  └─ Share (text/plain)
       └─ Spotifier (Android, Kotlin)
            ├─ Validate + extract video ID from URL (?v=VIDEO_ID)
            ├─ GET /spotifier?videoId=XXX  →  fly.io backend (Kotlin + Ktor)
            │     ├─ YouTube Data API v3 → get title + artist from video ID
            │     ├─ Spotify API (client credentials) → search track
            │     └─ Return Spotify track URL (plain text)
            └─ Android share sheet  →  WhatsApp / Telegram / etc.
```

## Components

### Android App

- **Language:** Kotlin
- **Min SDK:** API 26 (Android 8)
- **Target SDK:** API 34 (Android 14)
- **Build system:** Gradle (Kotlin DSL) with version catalog (`gradle/libs.versions.toml`)
- **Single component:** A transparent `Activity` declared with `android:exported="true"` (required for API 31+)
- **Intent filter:** `ACTION_SEND` / `text/plain` — makes Spotifier appear in the YouTube Music share sheet
- **HTTP:** Retrofit + OkHttp with coroutines; connect timeout 10s, read timeout 15s, no retries
- **Backend base URL:** hardcoded as a `BuildConfig` constant (e.g. `https://spotifier.fly.dev`)
- **On success:** Fires a new `ACTION_SEND` intent to open the Android share sheet with the Spotify URL
- **On error:** Shows a user-friendly `Toast` (e.g. "Song not found on Spotify" or "Could not reach server — try again"), then finishes

**Flow:**
1. Receive `ACTION_SEND` intent with YouTube Music URL
2. Parse the URL and extract the `v` query parameter using `Uri.parse(url).getQueryParameter("v")`
3. If the URL is not a `music.youtube.com/watch` URL or the `v` parameter is absent, show Toast "Could not read YouTube Music link" and finish — no network call is made
4. Call backend `GET /spotifier?videoId=VIDEO_ID`
5. Receive Spotify URL
6. Launch Android share chooser with the Spotify URL

**Accepted trade-off:** `Toast` is not visible if the screen is off during the network call. This is acceptable for a personal tool.

### Backend

- **Language:** Kotlin
- **Framework:** Ktor
- **Deployment:** fly.io (Docker container)
- **Single endpoint:** `GET /spotifier?videoId=VIDEO_ID`
- **Health check endpoint:** `GET /health` — returns `200 OK`, used by fly.io `[checks]` in `fly.toml`

**Input validation:**
- `videoId` must match `[A-Za-z0-9_\-]{11}` exactly
- Return `400 Bad Request` if absent or malformed (no upstream API call is made)

**Request flow:**
1. Check in-memory LRU cache (capacity: 1000) — return cached result if hit
2. Call YouTube Data API v3 `videos?id=VIDEO_ID&part=snippet` to retrieve `snippet.title` and `snippet.channelTitle`
3. Parse title and artist (see Title Parsing below)
4. Obtain a Spotify access token (see Token Lifecycle below)
5. Call Spotify Web API `GET /v1/search?q=track:TITLE+artist:ARTIST&type=track&limit=1`
6. Return the first result's `external_urls.spotify` value as plain text
7. Cache the result

**Error responses:**

| Code | Meaning |
|------|---------|
| `400` | Missing or malformed `videoId` parameter |
| `404` | No matching track found on Spotify |
| `500` | Unexpected internal error |
| `502` | Upstream API failure (YouTube or Spotify unreachable / returned error) |

**Cache:** Keyed on `videoId`. In-memory LRU only — intentionally not persisted across restarts. fly.io machine restarts cold-start the cache. Accepted trade-off for a personal tool.

**Backend outbound HTTP timeouts:** Connect timeout 5s, read timeout 10s for both YouTube Data API and Spotify API calls.

### Title Parsing

YouTube Music video titles follow several patterns. The backend applies these rules in order:

1. **Primary:** Split `snippet.title` on the en-dash character U+2013 (`–`). Left side = track name, right side = artist. Trim whitespace from both sides.
2. **Fallback 1:** If no en-dash is present, use the full `snippet.title` as the track name and derive the artist from `snippet.channelTitle`. Strip the YouTube-generated " - Topic" suffix from `channelTitle` if present (e.g. `"The Weeknd - Topic"` → `"The Weeknd"`).
3. **Fallback 2:** If both the parsed track name and artist are empty strings, search Spotify using the raw `snippet.title` as a free-text query (no `track:`/`artist:` field qualifiers).
4. If no Spotify result is returned for any strategy, return `404`.

Common title variants handled by rule 1: `"Blinding Lights – The Weeknd"`, `"Song (feat. Artist) – Main Artist"`.

### Spotify Token Lifecycle

- On first request (or after expiry), POST to `https://accounts.spotify.com/api/token` with `grant_type=client_credentials` using Basic auth (client ID + secret)
- The response includes `access_token` and `expires_in` (typically 3600 seconds)
- The backend stores the token in memory with its expiry timestamp
- Before each Spotify API call, check if the token expires within 60 seconds; if so, refresh it proactively
- On a `401` response from Spotify, force-refresh the token and retry once

**Configuration (environment variables on fly.io):**
- `YOUTUBE_API_KEY` — YouTube Data API v3 key
- `SPOTIFY_CLIENT_ID` — Spotify developer app client ID
- `SPOTIFY_CLIENT_SECRET` — Spotify developer app client secret

## Data Flow Example

1. User plays "Blinding Lights" in YouTube Music and taps Share
2. Picks Spotifier from the share sheet
3. App parses `v=4NRXx6U8ABQ` from the URL (`music.youtube.com/watch?v=4NRXx6U8ABQ`)
4. Calls `https://spotifier.fly.dev/spotifier?videoId=4NRXx6U8ABQ`
5. Backend returns `https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b` (22-char Spotify track ID)
6. Android share sheet opens — user picks WhatsApp or Telegram

## Project Structure

```
spotifier/
├── frontend/                          # New Kotlin Android app (replaces legacy Java/Ant)
│   ├── settings.gradle.kts
│   ├── gradle/
│   │   └── libs.versions.toml
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   └── kotlin/net/palacesoft/spotifier/
│   │   │       └── SpotifierActivity.kt
│   │   └── build.gradle.kts
│   └── build.gradle.kts
└── backend/                           # New Kotlin/Ktor backend (replaces Groovy/Spring Boot)
    ├── settings.gradle.kts
    ├── gradle/
    │   └── libs.versions.toml
    ├── src/main/kotlin/net/palacesoft/spotifier/
    │   ├── Application.kt             # Ktor setup, routes registration (includes GET /health)
    │   ├── SpotifierRoute.kt          # GET /spotifier handler, LRU cache
    │   ├── YoutubeService.kt          # YouTube Data API calls + title parsing
    │   └── SpotifyService.kt          # Spotify token lifecycle + search
    ├── Dockerfile
    ├── fly.toml                       # includes [checks] targeting GET /health
    └── build.gradle.kts
```

## APIs Required

| API | Purpose | Auth |
|-----|---------|------|
| YouTube Data API v3 | Get song title/artist from video ID | API key (free quota: 10,000 units/day) |
| Spotify Web API | Search for track + token endpoint | Client credentials (no user login) |

Both free tiers are sufficient for personal use.

## Out of Scope

- User accounts or preferences
- History of converted links
- Support for other music services
- Offline mode
- Playlist or album URL handling (only `/watch?v=` URLs are supported)
