package net.palacesoft.spotifier

import android.app.Activity
import android.content.Intent
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
