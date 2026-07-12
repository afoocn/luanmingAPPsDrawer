package com.fengnian.folderdrawer.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * 锁屏时自动关闭弹窗 Activity 并结束进程，避免后台残留。
 *
 * 用法：弹窗 Activity 在 onCreate 中 install()，onDestroy 中 uninstall()。
 */
class AutoDismissOnLock(private val activity: Activity) {

    private val keyguardManager: KeyguardManager? =
        activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                dismissAndKill()
            }
        }
    }

    private var installed = false

    fun install() {
        if (installed) return
        installed = true

        if (isLockedOrScreenOff()) {
            // 已经在锁屏状态：直接 finish，稍后杀进程
            pendingKill = true
            activity.finish()
            return
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, filter)
        }
    }

    fun uninstall() {
        if (!installed) return
        installed = false
        try {
            activity.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    /** 在 Activity.onDestroy() 时调用，处理 pending kill */
    fun onActivityDestroyed() {
        if (pendingKill) {
            pendingKill = false
            Handler(Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
            }, 100)
        }
    }

    private fun isLockedOrScreenOff(): Boolean {
        val km = keyguardManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            km.isKeyguardLocked
        } else {
            @Suppress("DEPRECATION")
            km.inKeyguardRestrictedInputMode()
        }
    }

    private fun dismissAndKill() {
        if (activity.isFinishing || activity.isDestroyed) return
        pendingKill = true
        activity.finish()
    }

    companion object {
        @Volatile
        private var pendingKill: Boolean = false
    }
}
