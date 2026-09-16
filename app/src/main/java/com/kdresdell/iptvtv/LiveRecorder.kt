package com.kdresdell.iptvtv

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

// Records a live channel by copying the raw stream bytes straight to a
// file - no transcoding, no muxing, the same .ts the provider sends is
// directly playable as-is. A live stream's response body is unbounded, so
// this needs its own client with no read timeout (XtreamApi's client is
// tuned for short API calls and must not be reused here).
class LiveRecorder {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var activeCall: Call? = null

    // Suspends for the whole recording duration - call this from a
    // coroutine the caller can cancel (e.g. tied to the player's own
    // lifecycle), and call stop() to end the recording deliberately.
    suspend fun record(streamUrl: String, outputFile: File, onProgress: (bytesWritten: Long) -> Unit) {
        withContext(Dispatchers.IO) {
            val call = client.newCall(Request.Builder().url(streamUrl).build())
            activeCall = call
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw XtreamApiException("Recording failed: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw XtreamApiException("Empty recording stream")
                    outputFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var totalBytes = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                totalBytes += read
                                onProgress(totalBytes)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // stop() cancelling the call closes the socket mid-read,
                // which surfaces here as an IOException - that's the
                // normal/expected way this ends, not a real error.
                if (!call.isCanceled()) {
                    throw e
                }
            } finally {
                activeCall = null
            }
        }
    }

    fun stop() {
        activeCall?.cancel()
    }
}
