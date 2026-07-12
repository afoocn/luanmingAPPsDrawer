package com.fengnian.folderdrawer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
/**
 * ?App 
 * packageName + activityClassName 
 *
 * customIconPath ?Icon Pack ?
 * customIconSource ?"system" / "iconpack:<drawableName>" / "image"
 */
@Entity(
 tableName = "app_items",
 foreignKeys = [
 ForeignKey(
 entity = Collection::class,
 parentColumns = ["id"],
 childColumns = ["collectionId"],
 onDelete = ForeignKey.CASCADE
 )
 ],
 indices = [Index("collectionId")]
)
data class AppItem(
 @PrimaryKey(autoGenerate = true) val id: Long = 0,
 val collectionId: Long,
 val packageName: String,
 val activityClassName: String,
 val displayName: String,
 val sortOrder: Int = 0,
 /** null ?Icon Pack / */
 val customIconPath: String? = null,
 /** null= , "system"= , "iconpack"=Icon Pack, "image"= */
 val customIconSource: String? = null
)
