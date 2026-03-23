# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spotifier converts Google Play Music track links to Spotify URIs. It has two components:
- **Backend**: Spring Boot REST API written in Groovy
- **Frontend**: Android app (Java, legacy Ant build system)

## Commands

### Backend

The backend supports both Maven and Gradle — prefer Gradle.

```bash
cd backend
./gradlew build       # Build + run tests
./gradlew bootRun     # Start server on port 8080
./gradlew test        # Run tests only

# Maven equivalents
mvn clean install
mvn spring-boot:run
mvn test
```

Run a single test class:
```bash
./gradlew test --tests "net.palacesoft.gmusic.SpotifierServiceIT"
```

### Frontend (Android)

Requires Android SDK (API 19+) installed.

```bash
cd frontend
android update project --path .   # Only needed once after SDK setup
ant debug                          # Build debug APK
ant install                        # Build and install on connected device
```

## Architecture

### Backend Data Flow

```
GET /resources/spotifier/{googleMusicId}?country=US
  → Application.groovy (REST controller + LRU cache)
    → cache hit: return cached Spotify URI
    → cache miss: SpotifierService.getSongId()
      → JSoup scrapes Google Play Music page for track name
      → Queries Spotify API (XML endpoint) for matching track
      → Returns Spotify track URI
```

**Key files:**
- `backend/src/main/groovy/net/palacesoft/gmusic/Application.groovy` — REST controller, LRU cache (1000-entry `LinkedHashMap`)
- `backend/src/main/groovy/net/palacesoft/gmusic/SpotifierService.groovy` — scraping + Spotify lookup logic
- `backend/src/test/groovy/net/palacesoft/gmusic/SpotifierServiceIT.groovy` — integration tests (verify Spotify IDs are 22 chars)

### Frontend Data Flow

1. Android registers an `ACTION_SEND` intent handler for `text/plain`
2. User shares a Google Music URL from the Play Music app
3. `Spotifier.java` extracts the song ID from the URL
4. `AsyncTask` calls backend at `http://spotifier.palace.eu.cloudbees.net`
5. Returned Spotify URL is copied to clipboard

**Key file:** `frontend/src/net/palacesoft/spotifier/Spotifier.java`

## Notes

- The backend port defaults to 8080, overridable via `-Dapp.port=<port>`
- The frontend hardcodes the backend URL (CloudBees deployment)
- Tests are integration tests — they make real HTTP requests to external APIs
