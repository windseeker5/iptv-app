package com.kdresdell.iptvtv

import android.content.Context
import java.io.File

// Finds a place to write recordings on an attached USB/removable drive.
// Index 0 of getExternalFilesDirs is always internal storage (far too
// small/unsuitable for TV recordings on most boxes); any further non-null
// entry is a mounted secondary volume (USB SSD via the hub, in our case).
// App-private directories on removable media need no manifest permission
// and no Storage Access Framework picker - this is the whole reason v1
// can skip SAF entirely.
object RecordingStorage {
    fun findRecordingDirectory(context: Context): File? {
        val volumes = context.getExternalFilesDirs(null)
        val usbVolume = volumes.drop(1).firstOrNull { it != null } ?: return null
        val recordingsDir = File(usbVolume, "Recordings")
        return if (recordingsDir.exists() || recordingsDir.mkdirs()) recordingsDir else null
    }

    fun listRecordings(context: Context): List<File> {
        val dir = findRecordingDirectory(context) ?: return emptyList()
        return dir.listFiles { file -> file.extension == "ts" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteRecording(file: File): Boolean = file.delete()

    // Recording filenames are "<channel>_yyyyMMdd_HHmmss.ts" (see
    // PlayerScreen.startRecording) - strip the timestamp suffix and turn
    // the sanitized channel name back into something readable.
    private val timestampSuffix = Regex("_\\d{8}_\\d{6}$")

    fun displayName(file: File): String =
        file.nameWithoutExtension.replace(timestampSuffix, "").replace('_', ' ')
}
