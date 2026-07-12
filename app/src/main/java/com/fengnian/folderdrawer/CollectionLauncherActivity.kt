package com.fengnian.folderdrawer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.fengnian.folderdrawer.util.AutoDismissOnLock
import com.fengnian.folderdrawer.viewmodel.CollectionViewModel

/**
 * 透明路由 Activity：桌面图标 / 第三方 App 的统一入口
 *
 * - 桌面图标点击（无 collection_id extra）：查数据库，有抽屉弹出第一个，无抽屉打开 MainActivity
 * - 第三方 Intent / 快捷方式（带 collection_id extra）：直接弹出指定抽屉
 * - 使用 NoDisplay 主题，零 UI 路由，无白屏闪烁
 */
class CollectionLauncherActivity : AppCompatActivity() {

    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var autoDismiss: AutoDismissOnLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        autoDismiss = AutoDismissOnLock(this).apply { install() }

        val collectionId = resolveCollectionId(intent)

        if (collectionId != -1L) {
            launchCollection(collectionId)
            return
        }

        // 桌面图标点击 → 查询数据库，取 sortOrder 最小的抽屉
        viewModel.collections.observe(this) { list ->
            if (list.isNotEmpty()) {
                val first = list.minByOrNull { it.sortOrder } ?: list.first()
                launchCollection(first.id)
            } else {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun launchCollection(collectionId: Long) {
        val launchIntent = Intent(this, QuickLaunchDialogActivity::class.java).apply {
            putExtra(QuickLaunchDialogActivity.EXTRA_COLLECTION_ID, collectionId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(launchIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismiss.onActivityDestroyed()
        autoDismiss.uninstall()
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collection_id"
        const val ACTION_LAUNCH_COLLECTION = "com.fengnian.folderdrawer.LAUNCH_COLLECTION"

        /**
         * 抽屉定位专用 data scheme/host。
         * 形如 folderdrawer://collection/<id>
         *
         * 为什么用 data 而不是只靠 extra：
         * 1. 部分启动器（澎湃/HyperOS/ColorOS 等）在从桌面触发 shortcut 时会丢弃 Intent 的 extra，
         *    但会原样保留 data URI；
         * 2. Intent.filterEquals() 比较 shortcut 是否重复时【忽略 extra】，
         *    多个抽屉若只用 extra 区分，其 Intent 会被判定为相同而被合并/错乱，
         *    把 data 编入 id 可让每个 shortcut 的 Intent 结构唯一。
         */
        const val SCHEME = "folderdrawer"
        const val DATA_HOST = "collection"
    }
}

/**
 * 解析要打开的抽屉 id。
 * 优先从 data URI 取（更可靠、可区分），extra 仅作兜底兼容。
 */
private fun resolveCollectionId(intent: Intent?): Long {
    intent?.data?.let { uri ->
        if (uri.scheme == CollectionLauncherActivity.SCHEME &&
            uri.host == CollectionLauncherActivity.DATA_HOST
        ) {
            uri.lastPathSegment?.toLongOrNull()?.let { return it }
        }
    }
    return intent?.getLongExtra(CollectionLauncherActivity.EXTRA_COLLECTION_ID, -1L) ?: -1L
}
