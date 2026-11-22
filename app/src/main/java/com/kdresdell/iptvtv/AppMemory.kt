package com.kdresdell.iptvtv

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache

// Memory diet, 0.23 (2026-11-22). The Fire TV built into the bedroom TV kept
// closing the app when resuming an episode, while the Chromecast never did.
// Fire TVs built into TVs have little memory, so the app now:
// - records every crash, and why Android closed it last time, in the
//   Settings log (CrashCatcher / logLastExit), so the next report is a fact;
// - keeps a smaller video buffer (buildPlayer), smaller still on weak boxes;
// - keeps fewer posters/logos in memory (installImageLoader), and empties
//   that cache when Android says memory is running low (onTrimMemory).
object AppMemory {

    // Weak box: Android's own "low RAM" flag, or under 2.5 GB in total.
    // Fire TVs built into TVs and the Chromecast (2 GB) both count.
    fun isWeakDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return am.isLowRamDevice || info.totalMem < 2_500L * 1024 * 1024
    }

    // One line at each start, so a log read later says what box it came from.
    fun logDevice(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val weak = if (isWeakDevice(context)) ", light mode" else ""
        AppLog.log(
            "Started ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL} " +
                "(Android ${Build.VERSION.RELEASE}): ${info.totalMem / MB} MB memory, " +
                "${info.availMem / MB} MB free, app limit ${am.memoryClass} MB$weak"
        )
    }

    // The only way an ExoPlayer is made in this app. ExoPlayer's default
    // buffer can grow past 130 MB on a high-bitrate movie - too much for a
    // weak box. A byte cap wins over the time targets, so a 4K episode just
    // buffers fewer seconds ahead instead of using more memory.
    @OptIn(UnstableApi::class)
    fun buildPlayer(context: Context): ExoPlayer {
        val weak = isWeakDevice(context)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ if (weak) 10_000 else 15_000,
                /* maxBufferMs = */ if (weak) 30_000 else 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .setTargetBufferBytes((if (weak) 40 else 80) * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        return ExoPlayer.Builder(context).setLoadControl(loadControl).build()
    }

    // Posters and channel logos kept in memory: 10% of the app's memory on a
    // weak box, 20% otherwise (Coil's default is 25%). Pictures saved on disk
    // are untouched, so scrolling back just reloads them from there.
    fun installImageLoader(context: Context) {
        val percent = if (isWeakDevice(context)) 0.10 else 0.20
        Coil.setImageLoader {
            ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder(context).maxSizePercent(percent).build()
                }
                .build()
        }
    }

    // Called from MainActivity.onTrimMemory. Only the "running low while you
    // use the app" levels are logged - the background ones are routine.
    @Suppress("DEPRECATION")
    fun onTrimMemory(context: Context, level: Int) {
        val label = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "low"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "critically low"
            else -> null
        }
        if (label != null || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            Coil.imageLoader(context).memoryCache?.clear()
        }
        if (label != null) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            AppLog.log("Android says memory is $label (${info.availMem / MB} MB free) - pictures cleared from memory")
        }
    }

    // Why Android closed the app last time, when it wasn't a normal exit -
    // the case a crash catcher can't see (killed for memory, a crash inside
    // the video decoder, a freeze). Android 11+ only; Fire OS 7 is Android 9.
    fun logLastExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val last = try {
            am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
        } catch (e: Exception) {
            null
        } ?: return
        val prefs = context.getSharedPreferences("app_memory", Context.MODE_PRIVATE)
        if (last.timestamp <= prefs.getLong("last_exit_seen", 0L)) return
        prefs.edit().putLong("last_exit_seen", last.timestamp).apply()
        val why = when (last.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "Android ran out of memory"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash in the video/system code"
            ApplicationExitInfo.REASON_ANR -> "the app froze"
            ApplicationExitInfo.REASON_SIGNALED -> "Android killed it"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "it used too much"
            else -> return // normal exit, update, force stop, Java crash (logged already)
        }
        val visible = last.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        AppLog.log(
            "Last close was not normal: $why" +
                (if (visible) " while on screen" else " while in the background") +
                ", using ${last.pss / 1024} MB" +
                (last.description?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
        )
    }

    private const val MB = 1024L * 1024
}

// Writes any crash to the Settings log before the app closes, then lets
// Android handle it as before. Without it a crash left no trace on boxes
// nobody can plug a cable into.
object CrashCatcher {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                var root: Throwable = error
                while (root.cause != null && root.cause !== root) root = root.cause!!
                val where = root.stackTrace.firstOrNull { it.className.startsWith("com.kdresdell") }
                    ?: root.stackTrace.firstOrNull()
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
                AppLog.log(
                    "App crashed: ${root.javaClass.simpleName}: ${root.message ?: "no message"}" +
                        (where?.let { " at ${it.fileName}:${it.lineNumber}" } ?: "") +
                        " (thread ${thread.name}, app using $heapMb MB, ${info.availMem / (1024 * 1024)} MB free)"
                )
            } catch (ignored: Throwable) {
                // Never let the logging itself hide the real crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
