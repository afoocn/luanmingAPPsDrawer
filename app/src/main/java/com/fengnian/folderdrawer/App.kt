package com.fengnian.folderdrawer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.fengnian.folderdrawer.util.DialogSettings
import com.fengnian.folderdrawer.util.DialerCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 系统包变化广播：安装/卸载/更新 App 后自动重建 APP Dialer 的磁盘缓存，
     * 使下次打开拨号盘即可见最新应用列表，无需手动「更新缓存」。
     */
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != Intent.ACTION_PACKAGE_ADDED &&
                action != Intent.ACTION_PACKAGE_REMOVED &&
                action != Intent.ACTION_PACKAGE_CHANGED
            ) return
            val ctx = context ?: return
            if (!DialogSettings.isDialerCacheEnabled(ctx)) return
            // 包安装/卸载过程常多次回调，去抖：延后 1500ms 重建一次磁盘缓存，避免重复全量扫描
            handler.removeCallbacks(rebuildTask)
            handler.postDelayed(rebuildTask, 1500L)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val rebuildTask = Runnable {
        val ctx = instance.applicationContext
        scope.launch {
            try {
                DialerCacheStore.rebuildAndSave(ctx)
            } catch (_: Exception) {
                // 缓存重建失败不应影响主流程
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashHandler()
        registerPackageChangeReceiver()
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, filter)
        }
    }

    private fun installCrashHandler() {
        // 崩溃报告开关：关闭时不注册自定义 handler，沿用系统默认行为、不写本地文件
        if (!DialogSettings.isCrashReportEnabled(this)) return
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
