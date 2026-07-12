package com.fengnian.folderdrawer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ?Shortcut Maker ?Collection ? * ? *
 *
 * - backgroundColor: 0 surface
 * - backgroundAlpha: ?0-255
 * - blurRadius: 0-25 ? = API 31+ ? *
 * ? * - titleTextColor: ? = ? * - appNameTextColor: APP ? = ? *
 * ? * - gridColumns:
 * - iconSizeDp:
 * - showAppName: APP
 * - compactAppName:
 * - appNameSizeSp:
 * - dialogMarginHorizontal:
 * - dialogPosition: (center / bottom)
 * - dialogRowSpacing: ? * - dialogColumnSpacing: ? * - showDialogTitle:
 *
 * ? * - dialogAnimDirection: (bottom / left / right / center)
 * - dialogAnimStyle: (slide / scale / spring)
 * - dialogAnimDuration: ms (150-500)
 */
@Entity(tableName = "collections")
data class Collection(
 @PrimaryKey(autoGenerate = true)
 val id: Long = 0,

 val name: String,

 /** ?*/
 val iconColor: Int = 0xFFC4704A.toInt(),

 /** ?Icon Pack ?drawable null = */
 val shortcutIconDrawable: String? = null,

 val isDefault: Boolean = false,

 val sortOrder: Int = 0,

 val createdAt: Long = System.currentTimeMillis(),

 // ===== ?=====
 /** ? surface ?*/
 val backgroundColor: Int = 0,

 /** ?0-255 ?120 ≈47% ?iOS ???*/
 val backgroundAlpha: Int = 120,

 /** 0-25 ? = API 31+ ???10 ?*/
 val blurRadius: Int = 10,

 /** : "gaussian" / "motion" / "radial" API 31+ ?*/
 val blurType: String = "gaussian",

 // ===== =====
 /** ? = */
 val titleTextColor: Int = 0,

 /** APP ? = */
 val appNameTextColor: Int = 0,

 // ===== =====
 /** (3-8) */
 val gridColumns: Int = 5,

 /** dp (36-72) */
 val iconSizeDp: Int = 48,

 /** APP */
 val showAppName: Boolean = true,

 /** */
 val compactAppName: Boolean = false,

 /** sp (8-16) */
 val appNameSizeSp: Int = 11,

 // ===== ?=====
 /** dp (8-64) */
 val dialogMarginHorizontal: Int = 28,

 /** : "center" ?"bottom" */
 val dialogPosition: String = "center",

 /** ?dp (0-120) dialogPosition="bottom" */
 val bottomMarginDp: Int = 16,

 /** ?dp (0-32) */
 val dialogRowSpacing: Int = 8,

 /** ?dp (0-32) */
 val dialogColumnSpacing: Int = 8,

 // ===== =====
 /** : "bottom" / "left" / "right" / "center" */
 val dialogAnimDirection: String = "bottom",

 /** : "slide" / "scale" / "spring" */
 val dialogAnimStyle: String = "spring",

 /** ms (150-500) */
 val dialogAnimDuration: Int = 300,

 val showInDialog: Boolean = true
)
