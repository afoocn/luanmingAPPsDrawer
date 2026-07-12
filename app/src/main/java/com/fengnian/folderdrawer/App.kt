package com.fengnian.folderdrawer

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                Log.e("CrashHandler", stackTrace)

                val dir = getExternalFilesDir(null) ?: filesDir
                val logFile = File(dir, "crash_${System.currentTimeMillis()}.txt")
                logFile.writeText(
                    "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n" +
                    "Stack:\n$stackTrace"
                )
            } catch (_: Exception) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
