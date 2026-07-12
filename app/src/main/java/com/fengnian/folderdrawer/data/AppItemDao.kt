package com.fengnian.folderdrawer.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AppItemDao {

    @Query("SELECT * FROM app_items WHERE collectionId = :collectionId ORDER BY sortOrder ASC, id ASC")
    fun observeByCollection(collectionId: Long): LiveData<List<AppItem>>

    @Query("SELECT * FROM app_items WHERE collectionId = :collectionId ORDER BY sortOrder ASC, id ASC")
    suspend fun getByCollection(collectionId: Long): List<AppItem>

    @Query("SELECT COUNT(*) FROM app_items WHERE collectionId = :collectionId")
    suspend fun countByCollection(collectionId: Long): Int

    @Query("SELECT * FROM app_items WHERE collectionId = :collectionId AND packageName = :pkg AND activityClassName = :activity LIMIT 1")
    suspend fun findByComponent(collectionId: Long, pkg: String, activity: String): AppItem?

    @Insert
    suspend fun insert(appItem: AppItem): Long

    @Update
    suspend fun update(appItem: AppItem)

    @Delete
    suspend fun delete(appItem: AppItem)

    @Query("DELETE FROM app_items WHERE collectionId = :collectionId")
    suspend fun deleteByCollection(collectionId: Long)
}
