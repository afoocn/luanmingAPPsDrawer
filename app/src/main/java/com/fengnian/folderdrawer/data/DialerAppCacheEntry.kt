package com.fengnian.folderdrawer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * APP Dialer 已装应用的持久化缓存条目。
 * 与进程内存缓存相比，这张表在磁盘上，进程被杀后下次打开仍可秒显，
 * 免去逐个读取 PackageManager、套图标包、算拼音的开销。
 */
@Entity(tableName = "dialer_app_cache")
data class DialerAppCacheEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "activity_name") val activityName: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "pinyin_full") val pinyinFull: String,
    @ColumnInfo(name = "pinyin_initials") val pinyinInitials: String,
    @ColumnInfo(name = "label_lower") val labelLower: String,
    @ColumnInfo(name = "icon_blob") val iconBlob: ByteArray,
    /** 生成缓存时激活的图标包 id（空串=系统默认图标），用于判断缓存是否因换包而失效 */
    @ColumnInfo(name = "icon_pack_id") val iconPackId: String
)
