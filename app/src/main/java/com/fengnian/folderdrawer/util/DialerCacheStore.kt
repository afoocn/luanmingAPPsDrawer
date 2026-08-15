package com.fengnian.folderdrawer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.fengnian.folderdrawer.data.AppDatabase
import com.fengnian.folderdrawer.data.DialerAppCacheEntry
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.DialogSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * APP Dialer 已装应用的持久化缓存管理。
 *
 * 与 AppDialerActivity 里的进程内存缓存 [com.fengnian.folderdrawer.AppDialerActivity] 不同，
 * 这里把「包名/activity/名称/拼音/图标位图」落盘到 Room 表 dialer_app_cache，
 * 进程被杀后下次打开仍可秒显，免去逐个读 PackageManager、套图标包、算拼音的耗时。
 */
object DialerCacheStore {

    /** 图标缓存尺寸（dp），足够清晰且体积可控 */
    private const val ICON_CACHE_DP = 96

    /**
     * 全量构建可搜索条目：读取已装应用、套用当前激活图标包、计算拼音键。
     * 与 AppDialerActivity.loadApps 后台刷新逻辑一致，集中在此便于复用。
     */
    suspend fun buildEntries(context: Context): List<AppSearchEntry> = withContext(Dispatchers.IO) {
        val iconPm = IconPackManager.getInstance(context)
        AppUtils.getInstalledApps(context).map { ia ->
            val keys = T9KeyboardHelper.buildSearchKeys(ia.label)
            val themedIcon = iconPm.getIcon(ia.packageName, ia.activityName, ia.label) ?: ia.icon
            AppSearchEntry(
                ia.packageName, ia.activityName, ia.label, themedIcon,
                keys.first, keys.second, keys.third
            )
        }
    }

    /** 把内存条目写盘：清空旧表后整表插入，并记录当前激活图标包 id 用于失效判断 */
    suspend fun save(context: Context, entries: List<AppSearchEntry>) = withContext(Dispatchers.IO) {
        val activePack = IconPackManager.getInstance(context).getActiveIconPack() ?: ""
        val rows = entries.map { e ->
            DialerAppCacheEntry(
                packageName = e.packageName,
                activityName = e.activityName,
                label = e.label,
                pinyinFull = e.pinyinFull,
                pinyinInitials = e.pinyinInitials,
                labelLower = e.labelLower,
                // 优先复用已落盘的字节（来自上次 load 的 iconBlob），避免对已解码的图标重复栅格化；
                // 实时构建的条目则把其 Drawable 栅格化为字节。
                iconBlob = e.iconBlob ?: (e.icon?.let { drawableToBytes(context, it) } ?: ByteArray(0)),
                iconPackId = activePack
            )
        }
        val dao = AppDatabase.get(context).dialerCacheDao()
        dao.clear()
        dao.insertAll(rows)
    }

    /**
     * 读盘还原条目。返回 null 表示缓存为空（需实时构建）。
     * 注意：此处**不做图标解码**——仅把 Room BLOB 原样挂到 [AppSearchEntry.iconBlob]，
     * 图标由渲染层按需懒解码（打开时只解码「上次结果」命中的几个，而非全部），实现随开随展示。
     */
    suspend fun load(context: Context): List<AppSearchEntry>? = withContext(Dispatchers.IO) {
        val rows = AppDatabase.get(context).dialerCacheDao().getAll()
        if (rows.isEmpty()) return@withContext null
        rows.map { r ->
            AppSearchEntry(
                r.packageName, r.activityName, r.label,
                null,
                r.pinyinFull, r.pinyinInitials, r.labelLower,
                iconBlob = r.iconBlob
            )
        }
    }

    /** 清空磁盘缓存 */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        AppDatabase.get(context).dialerCacheDao().clear()
    }

    /**
     * 缓存是否失效：空表，或记录时的图标包与当前激活图标包不一致（换包后旧图标应刷新）。
     */
    suspend fun isStale(context: Context): Boolean = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).dialerCacheDao()
        if (dao.count() == 0) return@withContext true
        val storedPack = dao.storedIconPackId() ?: ""
        val activePack = IconPackManager.getInstance(context).getActiveIconPack() ?: ""
        storedPack != activePack
    }

    /** 便捷：全量重建并写盘（供「更新缓存」按钮与系统包变化广播调用） */
    suspend fun rebuildAndSave(context: Context) {
        val entries = buildEntries(context)
        save(context, entries)
    }

    private fun drawableToBytes(context: Context, drawable: Drawable): ByteArray {
        val density = context.resources.displayMetrics.density
        // 按用户设置的图标展示尺寸生成缓存位图（向上取整到 96dp 保证清晰度，封顶 192dp 控制体积），
        // 这样缓存图标与当前「图标大小」设置一致，显示时 1:1 不失真、不放大。
        val cfgDp = DialogSettings.getDialerIconSize(context)
            .toFloat().coerceAtLeast(ICON_CACHE_DP.toFloat()).coerceAtMost(192f)
        val size = (cfgDp * density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        bmp.recycle()
        return stream.toByteArray()
    }

    internal fun bytesToDrawable(context: Context, bytes: ByteArray): Drawable {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        // 修正位图密度，使 BitmapDrawable 与设备密度 1:1，显示缩放像素精确、不被二次拉伸
        bmp.density = context.resources.displayMetrics.densityDpi
        return BitmapDrawable(context.resources, bmp)
    }
}
