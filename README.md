spotifier
=========

Share YouTube Music tracks as Spotify links.

You're listening to something in YouTube Music and want to send it to a friend who uses Spotify. Share it to
Spotifier, and it finds the same track on Spotify and opens the share sheet with the Spotify link, ready to
send via WhatsApp, Telegram, etc.

## Using it from any browser

Open **https://spotifier-delicate.fly.dev/**, paste a YouTube Music link (or just the video ID) and tap
**Find on Spotify**. You can then copy the Spotify link, share it, or open it in Spotify. Works on any phone or
computer.

### Install it on your phone

- **Android (Chrome):** open the page, tap **⋮ → Add to Home screen → Install**. Spotifier then also shows up in
  the share menu: in YouTube Music tap **Share → Spotifier** and the lookup starts by itself.
- **iPhone (Safari):** open the page, tap **Share → Add to Home Screen**. iOS doesn't let web apps appear in the
  share menu, so copy the link in YouTube Music and paste it into Spotifier.

## Using it on your phone (Android app)

1. Install the app (see [Installing the app](#installing-the-app)).
2. In YouTube Music, open a track and tap **Share**.
3. Pick **Spotifier** from the share sheet.
4. After a second or two the share sheet opens again, this time with the Spotify link. Pick the app to send it with.

If the track can't be matched you get a "Song not found on Spotify" message. Only `music.youtube.com/watch?v=...`
links are supported.

The first share after a period of inactivity can take a few extra seconds while the server wakes up.

## Calling the API directly

Take the `v=` value from a YouTube Music link (`https://music.youtube.com/watch?v=VIDEO_ID`) and request:

```
https://spotifier-delicate.fly.dev/spotifier?videoId=VIDEO_ID
```

The response is the Spotify track URL as plain text.

| Status | Meaning                                  |
|--------|------------------------------------------|
| 200    | Spotify track URL in the body            |
| 400    | Missing or malformed `videoId`           |
| 404    | No matching track found on Spotify       |
| 502    | YouTube or search lookup failed          |

## Installing the app

The app isn't on the Play Store, so you build and install it yourself. It needs the Android SDK and a phone
with USB debugging enabled (Android 8+).

```bash
cd frontend
./gradlew installDebug
```

To build an APK you can copy to the phone instead, run `./gradlew assembleDebug` and install
`frontend/app/build/outputs/apk/debug/app-debug.apk` (allow "install unknown apps" for your file manager).

## Running the backend

```bash
cd backend
YOUTUBE_API_KEY=your-key ./gradlew run   # http://localhost:8080
./gradlew test
```

It needs a [YouTube Data API v3](https://console.cloud.google.com/apis/library/youtube.googleapis.com) key.
It's deployed to fly.io:

```bash
cd backend
fly secrets set YOUTUBE_API_KEY=your-key
fly deploy
```

To point the app at a different backend, change `BACKEND_BASE_URL` in `frontend/app/build.gradle.kts`.

## How it works

The backend looks up the video's title and artist with the YouTube Data API, then searches DuckDuckGo for
the matching `open.spotify.com/track` page. Results are cached in memory. The privacy policy is served at
`/privacy`.
