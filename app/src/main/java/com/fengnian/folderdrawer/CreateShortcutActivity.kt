package com.fengnian.folderdrawer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fengnian.folderdrawer.CollectionLauncherActivity
import com.fengnian.folderdrawer.DialerConstants
import com.fengnian.folderdrawer.data.AppDatabase
import com.fengnian.folderdrawer.data.Collection
import com.fengnian.folderdrawer.util.ShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 旧版快捷方式创建入口（ACTION_CREATE_SHORTCUT? *
 * 兼容 MyGesture、Tasker 等第三方手势/自动化软件的快捷方式选择器? * 这些软件通过 Intent.ACTION_CREATE_SHORTCUT 查找可提供快捷方式的 App? * 选中集合后返?EXTRA_SHORTCUT_INTENT 供软件在触发手势时调用? */
class CreateShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_CREATE_SHORTCUT) {
            finish()
            return
        }

        loadCollections()
    }

    private fun loadCollections() {
        lifecycleScope.launch {
            val collections = withContext(Dispatchers.IO) {
                AppDatabase.get(this@CreateShortcutActivity).collectionDao().getAll()
            }

            // 列表首项固定为「APP Dialer」（仅当「启用 APP Dialer」开关打开时），
            // 其后为各抽屉。手势/自动化软件（MyGesture 等）的快捷方式选择器只能稳定看到本 activity，
            // 故把 APP Dialer 直接并入抽屉选择器，保证它必然出现、可被创建快捷方式。
            val items = mutableListOf<Collection?>()
            val names = mutableListOf<String>()
            if (com.fengnian.folderdrawer.util.DialogSettings.isDialerEnabled(this@CreateShortcutActivity)) {
                names.add("📞 APP Dialer")
                items.add(null)
            }
            collections.forEach { c ->
                names.add(c.name)
                items.add(c)
            }

            AlertDialog.Builder(this@CreateShortcutActivity)
                .setTitle("选择抽屉 / APP Dialer")
                .setItems(names.toTypedArray()) { _, which ->
                    val sel = items[which]
                    if (sel == null) returnDialerShortcutResult() else returnShortcutResult(sel)
                }
                .setOnCancelListener {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                .show()
        }
    }

    @Suppress("DEPRECATION")
    private fun returnShortcutResult(collection: Collection) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ShortcutHelper.buildLegacyShortcutBitmap(this@CreateShortcutActivity, collection)
            }

            val launchIntent = Intent(this@CreateShortcutActivity, CollectionLauncherActivity::class.java).apply {
                action = CollectionLauncherActivity.ACTION_LAUNCH_COLLECTION
                // data URI 编码 collection.id：第三方手势/自动化软件透传 intent 时更可靠，且避免被合并
                data = Uri.parse("${CollectionLauncherActivity.SCHEME}://${CollectionLauncherActivity.DATA_HOST}/${collection.id}")
                putExtra(CollectionLauncherActivity.EXTRA_COLLECTION_ID, collection.id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resultIntent = Intent().apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, collection.name)
                putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun returnDialerShortcutResult() {
        // 与 returnShortcutResult 保持一致：图标生成放到 IO 线程，结果回主线程再 setResult/finish，
        // 并加 try/catch 兜底——任何异常都保证 finish()，绝不卡死等待手势软件。
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ShortcutHelper.getDialerShortcutIcon(this@CreateShortcutActivity)
                }
                val launchIntent = Intent(this@CreateShortcutActivity, CollectionLauncherActivity::class.java).apply {
                    action = CollectionLauncherActivity.ACTION_LAUNCH_COLLECTION
                    // data URI 编码：folderdrawer://collection/-2，复用普通抽屉稳定路由，避开 NoDisplay 假死
                    data = Uri.parse("${CollectionLauncherActivity.SCHEME}://${CollectionLauncherActivity.DATA_HOST}/${DialerConstants.DIALER_COLLECTION_ID}")
                    putExtra(CollectionLauncherActivity.EXTRA_COLLECTION_ID, DialerConstants.DIALER_COLLECTION_ID)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                val resultIntent = Intent().apply {
                    putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent)
                    putExtra(Intent.EXTRA_SHORTCUT_NAME, "APP Dialer")
                    putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }
    }
}
