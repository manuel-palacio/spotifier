# Spotifier Android Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Kotlin Android app (no visible UI) that receives a YouTube Music share intent, calls the Spotifier backend, and opens the Android share sheet with the resulting Spotify track URL.

**Architecture:** A single transparent `Activity` with no layout. It handles `ACTION_SEND` / `text/plain` intents, extracts the YouTube Music video ID from the URL, calls the backend via Retrofit + coroutines, then fires a new `ACTION_SEND` intent with the Spotify URL. On error it shows a Toast and finishes.

**Tech Stack:** Kotlin 1.9, Android API 26 min / 34 target, Retrofit 2 + OkHttp 4, Kotlin Coroutines, Gradle (Kotlin DSL) with version catalog

---

## File Map

| File | Responsibility |
|------|---------------|
| `frontend/settings.gradle.kts` | Module name declaration |
| `frontend/build.gradle.kts` | Root build config |
| `frontend/gradle/libs.versions.toml` | Centralized version catalog |
| `frontend/app/build.gradle.kts` | App module dependencies, BuildConfig fields |
| `frontend/app/src/main/AndroidManifest.xml` | Activity declaration, intent filter, permissions |
| `frontend/app/src/main/kotlin/net/palacesoft/spotifier/SpotifierActivity.kt` | Entire app logic |
| `frontend/app/src/main/kotlin/net/palacesoft/spotifier/SpotifierApi.kt` | Retrofit interface |
| `frontend/app/src/test/kotlin/net/palacesoft/spotifier/UrlParserTest.kt` | Unit tests for video ID extraction |

---

## Task 1: Gradle project scaffold

**Files:**
- Create: `frontend/settings.gradle.kts`
- Create: `frontend/build.gradle.kts`
- Create: `frontend/gradle/libs.versions.toml`
- Create: `frontend/app/build.gradle.kts`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "spotifier"
include(":app")
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
kotlin           = "1.9.23"
agp              = "8.3.1"
retrofit         = "2.9.0"
okhttp           = "4.12.0"
coroutines       = "1.7.3"
junit            = "4.13.2"

[libraries]
retrofit                 = { module = "com.squareup.retrofit2:retrofit",                    version.ref = "retrofit" }
retrofit-converter-scalar = { module = "com.squareup.retrofit2:converter-scalars",          version.ref = "retrofit" }
okhttp                   = { module = "com.squareup.okhttp3:okhttp",                        version.ref = "okhttp" }
coroutines-android       = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android",   version.ref = "coroutines" }
junit                    = { module = "junit:junit",                                         version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 3: Create root `build.gradle.kts`**

```kotlin
// Top-level build file — configuration for subprojects goes in app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

- [ ] **Step 4: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.palacesoft.spotifier"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.palacesoft.spotifier"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0"

        buildConfigField("String", "BACKEND_BASE_URL", "\"https://spotifier.fly.dev\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalar)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
```

- [ ] **Step 5: Verify the project syncs**

Open in Android Studio and sync, or run:
```bash
cd frontend && ./gradlew assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL` (even with no sources, the empty project should compile).

- [ ] **Step 6: Commit**

```bash
git add frontend/
git commit -m "chore: scaffold Android Gradle project"
```

---

## Task 2: URL parsing — unit tests + implementation

**Files:**
- Create: `frontend/app/src/main/kotlin/net/palacesoft/spotifier/UrlParser.kt`
- Create: `frontend/app/src/test/kotlin/net/palacesoft/spotifier/UrlParserTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package net.palacesoft.spotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlParserTest {

    @Test
    fun `extracts video ID from standard YouTube Music URL`() {
        val url = "https://music.youtube.com/watch?v=4NRXx6U8ABQ"
        assertEquals("4NRXx6U8ABQ", UrlParser.extractVideoId(url))
    }

    @Test
    fun `extracts video ID when extra query params present`() {
        val url = "https://music.youtube.com/watch?v=4NRXx6U8ABQ&list=PLsome&si=abc"
        assertEquals("4NRXx6U8ABQ", UrlParser.extractVideoId(url))
    }

    @Test
    fun `returns null for non-watch URL (playlist)`() {
        val url = "https://music.youtube.com/playlist?list=PLsomething"
        assertNull(UrlParser.extractVideoId(url))
    }

    @Test
    fun `returns null when v param missing`() {
        val url = "https://music.youtube.com/watch"
        assertNull(UrlParser.extractVideoId(url))
    }

    @Test
    fun `returns null for unrelated URL`() {
        val url = "https://www.google.com"
        assertNull(UrlParser.extractVideoId(url))
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
cd frontend && ./gradlew test 2>&1 | tail -10
```
Expected: `UrlParser` not found.

- [ ] **Step 3: Create `UrlParser.kt`**

Note: This file uses `android.net.Uri` which is available in unit tests via the AGP test setup. If the test runner complains, use `java.net.URI` with `URI(url).rawQuery` parsing instead. The implementation below uses the standard library approach that works in JVM unit tests:

```kotlin
package net.palacesoft.spotifier

import java.net.URI

object UrlParser {
    /**
     * Extracts the YouTube Music video ID from a music.youtube.com/watch?v=... URL.
     * Returns null if the URL is not a recognized YouTube Music watch URL or has no v param.
     */
    fun extractVideoId(url: String): String? {
        return try {
            val uri = URI(url)
            if (uri.host != "music.youtube.com") return null
            if (!uri.path.startsWith("/watch")) return null
            uri.rawQuery
                ?.split("&")
                ?.firstOrNull { it.startsWith("v=") }
                ?.removePrefix("v=")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
cd frontend && ./gradlew test --tests "net.palacesoft.spotifier.UrlParserTest" 2>&1 | tail -10
```
Expected: `5 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add frontend/app/src/
git commit -m "feat: add UrlParser with YouTube Music video ID extraction"
```

---

## Task 3: Retrofit API interface

**Files:**
- Create: `frontend/app/src/main/kotlin/net/palacesoft/spotifier/SpotifierApi.kt`

- [ ] **Step 1: Create `SpotifierApi.kt`**

```kotlin
package net.palacesoft.spotifier

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifierApi {
    @GET("spotifier")
    suspend fun getSpotifyUrl(
        @Query("videoId") videoId: String
    ): String
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd frontend && ./gradlew compileDebugKotlin 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add frontend/app/src/main/kotlin/net/palacesoft/spotifier/SpotifierApi.kt
git commit -m "feat: add Retrofit API interface"
```

---

## Task 4: SpotifierActivity

**Files:**
- Create: `frontend/app/src/main/kotlin/net/palacesoft/spotifier/SpotifierActivity.kt`
- Create: `frontend/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `SpotifierActivity.kt`**

```kotlin
package net.palacesoft.spotifier

import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class SpotifierActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val api: SpotifierApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL + "/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(SpotifierApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText == null) {
            finish()
            return
        }

        val videoId = UrlParser.extractVideoId(sharedText)
        if (videoId == null) {
            showToast("Could not read YouTube Music link")
            finish()
            return
        }

        scope.launch {
            try {
                val spotifyUrl = withContext(Dispatchers.IO) { api.getSpotifyUrl(videoId) }
                shareUrl(spotifyUrl)
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    404 -> "Song not found on Spotify"
                    else -> "Could not reach server — try again"
                }
                showToast(message)
            } catch (e: Exception) {
                showToast("Could not reach server — try again")
            } finally {
                finish()
            }
        }
    }

    private fun shareUrl(url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Spotify link via"))
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
```

- [ ] **Step 2: Create `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:label="Spotifier"
        android:theme="@android:style/Theme.Translucent.NoTitleBar">

        <activity
            android:name=".SpotifierActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar">

            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>

        </activity>

    </application>

</manifest>
```

- [ ] **Step 3: Build debug APK**

```bash
cd frontend && ./gradlew assembleDebug 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit**

```bash
git add frontend/app/src/main/
git commit -m "feat: add SpotifierActivity and AndroidManifest"
```

---

## Task 5: Install and smoke test on device

- [ ] **Step 1: Connect Android device via USB with USB debugging enabled**

Verify device is detected:
```bash
adb devices
```
Expected: your device listed with `device` status.

- [ ] **Step 2: Install APK**

```bash
cd frontend && ./gradlew installDebug
```
Expected: `Installed on 1 device`.

- [ ] **Step 3: Smoke test the full flow**

1. Open YouTube Music on your device
2. Play any song
3. Tap the share button (⋮ menu → Share)
4. **Spotifier** should appear in the share sheet
5. Tap Spotifier
6. Wait ~2 seconds
7. The Android share sheet should reappear — this time with the Spotify URL ready to send
8. Tap WhatsApp or Telegram to share the Spotify link

- [ ] **Step 4: Test error case**

Share any non-YouTube-Music URL (e.g. from a browser) to Spotifier.
Expected: Toast "Could not read YouTube Music link" appears briefly, no share sheet.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "chore: finalize Android app — smoke tested on device"
```
