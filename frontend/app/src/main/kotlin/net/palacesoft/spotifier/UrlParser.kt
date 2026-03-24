package net.palacesoft.spotifier

import java.net.URI

object UrlParser {

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
