package com.fengnian.folderdrawer.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY isDefault DESC, sortOrder ASC, createdAt ASC")
    fun observeAll(): LiveData<List<Collection>>

    @Query("SELECT * FROM collections ORDER BY isDefault DESC, sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<Collection>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: Long): Collection?

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observeById(id: Long): LiveData<Collection?>

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun count(): Int

    @Query("SELECT * FROM collections WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Collection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: Collection): Long

    @Update
    suspend fun update(collection: Collection)

    @Delete
    suspend fun delete(collection: Collection)

    @Query("DELETE FROM collections WHERE id = :id AND isDefault = 0")
    suspend fun deleteById(id: Long): Int
}
