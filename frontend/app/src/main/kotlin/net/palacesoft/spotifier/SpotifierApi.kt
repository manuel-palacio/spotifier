package net.palacesoft.spotifier

import retrofit2.http.GET
import retrofit2.http.Query

interface SpotifierApi {
    @GET("spotifier")
    suspend fun getSpotifyUrl(
        @Query("videoId") videoId: String
    ): String
}
