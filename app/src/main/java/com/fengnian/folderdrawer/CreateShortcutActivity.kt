package com.fengnian.folderdrawer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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

            if (collections.isEmpty()) {
                Toast.makeText(this@CreateShortcutActivity, "没有可用的抽屉", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@launch
            }

            val names = collections.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@CreateShortcutActivity)
                .setTitle("选择抽屉")
                .setItems(names) { _, which ->
                    returnShortcutResult(collections[which])
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
}
