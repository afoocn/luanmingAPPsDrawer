package com.fengnian.folderdrawer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
 entities = [Collection::class, AppItem::class, DialerAppCacheEntry::class],
 version = 14,
 exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

 abstract fun collectionDao(): CollectionDao
 abstract fun appItemDao(): AppItemDao
 abstract fun dialerCacheDao(): DialerCacheDao

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

 private val MIGRATION_13_14 = object : Migration(13, 14) {
     override fun migrate(db: SupportSQLiteDatabase) {
         db.execSQL(
             """CREATE TABLE IF NOT EXISTS dialer_app_cache (
                 id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                 package_name TEXT NOT NULL,
                 activity_name TEXT NOT NULL,
                 label TEXT NOT NULL,
                 pinyin_full TEXT NOT NULL,
                 pinyin_initials TEXT NOT NULL,
                 label_lower TEXT NOT NULL,
                 icon_blob BLOB NOT NULL,
                 icon_pack_id TEXT NOT NULL
             )"""
         )
     }
 }

 fun get(context: Context): AppDatabase {
 return instance ?: synchronized(this) {
 instance ?: Room.databaseBuilder(
 context.applicationContext,
 AppDatabase::class.java,
 "collection_drawer.db"
 )
 .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
 .fallbackToDestructiveMigration()
 .build()
 .also { instance = it }
 }
 }
 }
}
