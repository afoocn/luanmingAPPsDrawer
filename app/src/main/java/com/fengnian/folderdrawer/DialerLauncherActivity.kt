package com.fengnian.folderdrawer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fengnian.folderdrawer.util.ShortcutHelper

/**
 * APP Dialer 的透明路由入口（NoDisplay）。
 *
 * 桌面快捷方式 / 第三方手势软件统一经此打开拨号盘弹窗：
 * - 使用 NoDisplay 主题，零 UI 路由、无白屏闪烁（与 CollectionLauncherActivity 一致）
 * - data URI 编码为 folderdrawer://dialer，避免部分启动器丢弃 extra 的问题
 * - 同时响应 ACTION_CREATE_SHORTCUT：手势/自动化软件（MyGesture、Tasker 等）的快捷方式
 *   选择器能发现「APP Dialer」，选中后返回一个指向本路由的快捷方式 Intent，触发即打开弹窗。
 */
class DialerLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_CREATE_SHORTCUT) {
            // 手势/自动化软件来「创建快捷方式」：返回指向本路由的 shortcut Intent
            returnShortcutResult()
            return
        }

        val launchIntent = Intent(this, AppDialerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(launchIntent)
        finish()
    }

    private fun returnShortcutResult() {
        // 触发时用的 Intent：指向本路由（action=LAUNCH_DIALER），再由本路由转到 AppDialerActivity
        val launchIntent = Intent(this, DialerLauncherActivity::class.java).apply {
            action = ACTION_LAUNCH_DIALER
            data = Uri.parse("$SCHEME://$DATA_HOST")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val iconBitmap = ShortcutHelper.getDialerShortcutIcon(this)

        val resultIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, "APP Dialer")
            putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        const val ACTION_LAUNCH_DIALER = "com.fengnian.folderdrawer.LAUNCH_DIALER"

        /**
         * 拨号盘专用 data scheme/host：folderdrawer://dialer
         * 与抽屉定位同理，用 data URI 编码语义，启动器不会丢弃、filterEquals 可区分。
         */
        const val SCHEME = "folderdrawer"
        const val DATA_HOST = "dialer"
    }
}
