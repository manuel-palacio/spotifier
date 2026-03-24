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
