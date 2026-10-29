# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spotifier converts YouTube Music track links to Spotify track links. It has two components, both in Kotlin:
- **Backend** (`backend/`): Ktor REST API, deployed to fly.io as a Docker container
- **Frontend** (`frontend/`): Android app (min SDK 26, target SDK 34) that hooks into the share sheet

The root `settings.gradle.kts` includes both as composite builds; each also has its own Gradle wrapper.

## Commands

### Backend

```bash
cd backend
./gradlew build       # Build + run tests
./gradlew test        # Run tests only
YOUTUBE_API_KEY=... ./gradlew run   # Start server on port 8080 (override with PORT)
```

Run a single test class:
```bash
./gradlew test --tests "net.palacesoft.spotifier.SpotifierRouteTest"
```

Deploy (from `backend/`, app `spotifier-delicate`):
```bash
fly deploy
fly secrets set YOUTUBE_API_KEY=...
```

### Frontend (Android)

Requires the Android SDK (`local.properties` points to it).

```bash
cd frontend
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Build and install on connected device
./gradlew testDebugUnitTest    # Run unit tests
./gradlew assembleRelease      # Signed release build — needs ~/spotifier-release.jks,
                               # STORE_PASSWORD and KEY_PASSWORD env vars
```

## Architecture

### Backend Data Flow

```
GET /spotifier?videoId=XXXXXXXXXXX
  → SpotifierRoute.kt (validates 11-char videoId, else 400; LRU cache)
    → cache hit: return cached Spotify URL
    → cache miss:
      → YoutubeService: YouTube Data API v3 → title + channel → (track, artist)
      → SearchService: DuckDuckGo HTML search "artist track site:open.spotify.com/track"
        → regex-extracts the first Spotify track ID
      → 200 Spotify URL (plain text) | 404 not found | 502 upstream error
```

Other endpoints: `GET /` (installable lookup web app: `backend/src/main/resources/static/` — page, manifest with share target, service worker, icons), `GET /health` (fly.io health check), `GET /privacy` (privacy policy page for the Play Store).

**Key files:**
- `backend/src/main/kotlin/net/palacesoft/spotifier/Application.kt` — entry point, reads env vars, starts Netty
- `backend/src/main/kotlin/net/palacesoft/spotifier/SpotifierRoute.kt` — routes, validation, LRU cache (1000 entries, per `Application` instance), privacy policy HTML
- `backend/src/main/kotlin/net/palacesoft/spotifier/YoutubeService.kt` — YouTube lookup + title parsing (split on en-dash, else strip " - Topic" from channel name)
- `backend/src/main/kotlin/net/palacesoft/spotifier/SearchService.kt` — DuckDuckGo scraping for the Spotify link
- `backend/src/test/kotlin/net/palacesoft/spotifier/` — route tests (Ktor `testApplication` with stubbed services) and title-parsing tests

### Frontend Data Flow

1. `SpotifierActivity` (translucent) registers an `ACTION_SEND` intent handler for `text/plain`
2. User shares a track from the YouTube Music app
3. `UrlParser` extracts the `v` parameter from a `music.youtube.com/watch` URL (otherwise shows a Toast)
4. Retrofit (`SpotifierApi`) calls the backend from a coroutine
5. The returned Spotify URL is passed to the Android share chooser; errors show a Toast

**Key files:** `frontend/app/src/main/kotlin/net/palacesoft/spotifier/` (`SpotifierActivity.kt`, `SpotifierApi.kt`, `UrlParser.kt`)

## Notes

- Backend env vars: `YOUTUBE_API_KEY` (required), `PORT` (default 8080)
- The frontend's backend URL is the `BACKEND_BASE_URL` BuildConfig field in `frontend/app/build.gradle.kts` (`https://spotifier-delicate.fly.dev`)
- fly.io stops idle machines, so the first request after idle is a slow cold start
- Tests are unit tests with stubbed services — nothing hits YouTube or DuckDuckGo. The DuckDuckGo scraping can break if its HTML changes
- The design docs in `docs/superpowers/` describe the original Spotify Web API approach, which was later replaced with DuckDuckGo search

## Code Style: Clean Code Principles

This project follows **Uncle Bob's Clean Code** principles. When writing or modifying code:

### Naming

- **Use intention-revealing names** — Names should answer: why it exists, what it does, how it's used
- **Avoid encodings** — No Hungarian notation, no prefixes like `m_` or `I` for interfaces
- **Use pronounceable, searchable names** — `customerAddress` not `custAddr`

### Functions

- **Small** — Functions should be small, then smaller than that
- **Do one thing** — A function should do one thing, do it well, do it only
- **One level of abstraction** — Statements within a function should all be at the same level of abstraction
- **Descriptive names** — A long descriptive name is better than a short enigmatic one

### Comments

- **Don't comment bad code — rewrite it** — Comments are a failure to express yourself in code
- **Express yourself in code** — Create functions with descriptive names instead of adding comments
- **Acceptable comments** — Legal comments, explanation of intent, clarification of external APIs, TODOs

### Formatting

- **The newspaper metaphor** — Code should read like a newspaper: headline (class name), synopsis (public functions),
  details (private functions)
- **Vertical openness** — Separate concepts with blank lines
- **Keep related code together** — Caller should be above callee

### Error Handling

- **Use exceptions, not return codes**
- **Write try-catch-finally first** — Define scope with exceptions
- **Don't return or pass null** — Consider throwing exceptions or using Kotlin's null-safe types

### Classes

- **Small** — Classes should be small, measured by responsibilities (Single Responsibility Principle)
- **Organized** — Public functions first, then private functions called by them (step-down rule)

### Tests

- **Clean tests** — Test code is just as important as production code
- **One assert per test** — Or at least minimize assertions per test
- **F.I.R.S.T.** — Fast, Independent, Repeatable, Self-validating, Timely
