package com.fengnian.folderdrawer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DialerCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<DialerAppCacheEntry>)

    @Query("DELETE FROM dialer_app_cache")
    suspend fun clear()

    @Query("SELECT * FROM dialer_app_cache")
    suspend fun getAll(): List<DialerAppCacheEntry>

    @Query("SELECT COUNT(*) FROM dialer_app_cache")
    suspend fun count(): Int

    /** 取缓存时记录的图标包 id（用于与当前激活图标包比对判断失效） */
    @Query("SELECT DISTINCT icon_pack_id FROM dialer_app_cache LIMIT 1")
    suspend fun storedIconPackId(): String?
}
