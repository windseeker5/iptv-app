package com.kdresdell.iptvtv.phone

// TODO: duplicated from the TV app's UpdateChecker.kt, pointed at the phone feed.

import android.content.Context
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// The phone app has its own feed on the landing page (site/app.py
// /phone/version.json) - the TV feed would offer the TV APK.
private const val VERSION_URL = "https://omt.dresdell.com/phone/version.json"

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String
)

class UpdateChecker {
    private val client = OkHttpClient()

    // Throws instead of swallowing errors - the caller decides how to
    // surface a failure (a silent null here gave us no way to tell "no
    // update available" apart from "the request never worked").
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(VERSION_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw XtreamApiException("Update server returned HTTP ${response.code}")
            }
            val body = response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw XtreamApiException("Empty response from update server")
            val json = JSONObject(body)
            UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                url = json.getString("url"),
                notes = json.optString("notes", "")
            )
        }
    }

    suspend fun downloadApk(context: Context, update: UpdateInfo): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(update.url).build()
        val outFile = File(context.getExternalFilesDir(null), "update.apk")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw XtreamApiException("Could not download update: HTTP ${response.code}")
            }
            val body = response.body ?: throw XtreamApiException("Empty update download")
            outFile.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }
        }
        outFile
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "com.kdresdell.iptvtv.phone.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
