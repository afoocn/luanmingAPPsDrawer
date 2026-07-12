package com.fengnian.folderdrawer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
 entities = [Collection::class, AppItem::class],
 version = 13,
 exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

 abstract fun collectionDao(): CollectionDao
 abstract fun appItemDao(): AppItemDao

 companion object {
 @Volatile
 private var instance: AppDatabase? = null

 private val MIGRATION_9_10 = object : Migration(9, 10) {
     override fun migrate(db: SupportSQLiteDatabase) {
         db.execSQL("DELETE FROM app_items WHERE collectionId IN (SELECT id FROM collections WHERE isDefault = 1)")
         db.execSQL("DELETE FROM collections WHERE isDefault = 1")
     }
 }

 private val MIGRATION_10_11 = object : Migration(10, 11) {
     override fun migrate(db: SupportSQLiteDatabase) {
         db.execSQL("UPDATE collections SET blurRadius = 10, backgroundAlpha = 120")
     }
 }

 private val MIGRATION_11_12 = object : Migration(11, 12) {
     override fun migrate(db: SupportSQLiteDatabase) {
         db.execSQL("ALTER TABLE collections ADD COLUMN showInDialog INTEGER NOT NULL DEFAULT 1")
     }
 }

 private val MIGRATION_12_13 = object : Migration(12, 13) {
     override fun migrate(db: SupportSQLiteDatabase) {
         db.execSQL("ALTER TABLE collections DROP COLUMN showDialogTitle")
     }
 }

 fun get(context: Context): AppDatabase {
 return instance ?: synchronized(this) {
 instance ?: Room.databaseBuilder(
 context.applicationContext,
 AppDatabase::class.java,
 "collection_drawer.db"
 )
 .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
 .fallbackToDestructiveMigration()
 .build()
 .also { instance = it }
 }
 }
 }
}
