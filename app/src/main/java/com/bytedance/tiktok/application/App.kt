package com.bytedance.tiktok.application

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * create by libo
 * create on 2020-05-19
 * description 全局Application
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Global crash handler — writes stack trace to file + logcat for debugging
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val crashLog = "=== JELLYTOK CRASH at ${java.util.Date()} ===\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n\n" +
                    stackTrace + "\n\n"

                // Write to logcat
                Log.e("JellyTok_CRASH", crashLog)

                // Write to internal storage (always available)
                val dir = filesDir
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "crash_log.txt")
                file.appendText(crashLog)

                // Also try external storage
                try {
                    val extDir = getExternalFilesDir(null)
                    if (extDir != null) {
                        if (!extDir.exists()) extDir.mkdirs()
                        File(extDir, "crash_log.txt").appendText(crashLog)
                    }
                } catch (_: Exception) {}
            } catch (_: Exception) {}

            // Let the system handle the crash
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }
}
