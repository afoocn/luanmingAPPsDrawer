package com.fengnian.folderdrawer.data

import android.content.Context

import androidx.lifecycle.LiveData

import com.fengnian.folderdrawer.iconpack.IconPackManager

import com.fengnian.folderdrawer.util.AppUtils

import com.fengnian.folderdrawer.util.DialogSettings

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext

import org.json.JSONArray

import org.json.JSONObject

/**
 * 集合仓库：协调数据库、AppUtils、IconPackManager
 */
class CollectionRepository(

    private val context: Context,

    private val collectionDao: CollectionDao,

    private val appItemDao: AppItemDao,

    private val iconPackManager: IconPackManager

) {

    val allCollections: LiveData<List<Collection>> = collectionDao.observeAll()

    fun observeCollection(id: Long) = collectionDao.observeById(id)

    fun observeApps(collectionId: Long) = appItemDao.observeByCollection(collectionId)

    suspend fun getCollection(id: Long) = collectionDao.getById(id)

    suspend fun getAllCollections() = collectionDao.getAll()

    suspend fun getDefaultCollection() = collectionDao.getDefault()

    suspend fun countCollections() = collectionDao.count()

    suspend fun getApps(collectionId: Long) = appItemDao.getByCollection(collectionId)

    suspend fun getAppCount(collectionId: Long) = appItemDao.countByCollection(collectionId)

    /**
     * 创建集合
     */
    suspend fun createCollection(

        name: String,

        color: Int = 0xFFC4704A.toInt(),

        isDefault: Boolean = false,

        shortcutIconDrawable: String? = null,

        backgroundColor: Int = 0,

        backgroundAlpha: Int = 120,

        blurRadius: Int = 10,

        blurType: String = "gaussian",

        titleTextColor: Int = 0,

        appNameTextColor: Int = 0,

        gridColumns: Int = 5,

        iconSizeDp: Int = 48,

        showAppName: Boolean = true,

        compactAppName: Boolean = false,

        appNameSizeSp: Int = 11,

        dialogMarginHorizontal: Int = 28,

        dialogPosition: String = "center",

        bottomMarginDp: Int = 16,

        dialogRowSpacing: Int = 8,

        dialogColumnSpacing: Int = 8,

        dialogAnimDirection: String = "bottom",

        dialogAnimStyle: String = "spring",

        dialogAnimDuration: Int = 300,
        showInDialog: Boolean = true

    ): Long {

        val maxOrder = collectionDao.getAll().maxOfOrNull { it.sortOrder } ?: -1

        return collectionDao.insert(

            Collection(

                name = name,

                iconColor = color,

                shortcutIconDrawable = shortcutIconDrawable,

                isDefault = isDefault,

                sortOrder = maxOrder + 1,

                backgroundColor = backgroundColor,

                backgroundAlpha = backgroundAlpha,

                blurRadius = blurRadius,

                blurType = blurType,

                titleTextColor = titleTextColor,

                appNameTextColor = appNameTextColor,

                gridColumns = gridColumns,

                iconSizeDp = iconSizeDp,

                showAppName = showAppName,

                compactAppName = compactAppName,

                appNameSizeSp = appNameSizeSp,

                dialogMarginHorizontal = dialogMarginHorizontal,

                dialogPosition = dialogPosition,

                bottomMarginDp = bottomMarginDp,

                dialogRowSpacing = dialogRowSpacing,

                dialogColumnSpacing = dialogColumnSpacing,

                dialogAnimDirection = dialogAnimDirection,

                dialogAnimStyle = dialogAnimStyle,

                dialogAnimDuration = dialogAnimDuration,
                showInDialog = showInDialog

            )

        )

    }

    suspend fun updateCollection(collection: Collection) {

        collectionDao.update(collection)

    }

    suspend fun updateSortOrders(collections: List<Collection>) {
        collections.forEachIndexed { index, c ->
            collectionDao.update(c.copy(sortOrder = index))
        }
    }

    suspend fun deleteCollection(id: Long): Boolean {
        collectionDao.getById(id) ?: return false
        // 先删应用
        appItemDao.deleteByCollection(id)
        // 再删集合
        return collectionDao.deleteById(id) > 0
    }

    /**
     * 添加应用到集?
     */
    suspend fun addAppToCollection(

        collectionId: Long,

        packageName: String,

        activityClassName: String,

        displayName: String,

        _sortOrder: Int = 0

    ): Long {

        // 查重

        val existing = appItemDao.findByComponent(collectionId, packageName, activityClassName)

        if (existing != null) return existing.id

        val maxOrder = appItemDao.getByCollection(collectionId).maxOfOrNull { it.sortOrder } ?: -1

        return appItemDao.insert(

            AppItem(

                collectionId = collectionId,

                packageName = packageName,

                activityClassName = activityClassName,

                displayName = displayName,

                sortOrder = maxOrder + 1

            )

        )

    }

    suspend fun removeAppFromCollection(appItem: AppItem) {

        appItemDao.delete(appItem)

        // 删除后重新索引剩余项的 sortOrder，保持连续
        val remaining = appItemDao.getByCollection(appItem.collectionId)
        remaining.forEachIndexed { index, item ->
            if (item.sortOrder != index) {
                appItemDao.update(item.copy(sortOrder = index))
            }
        }
    }

    /**
     * 更新抽屉内某?APP 的显示名?
     * （从弹窗中长按APP名修改）
     */
    suspend fun updateAppDisplayName(appItem: AppItem, newName: String) {

        appItemDao.update(appItem.copy(displayName = newName))

    }

    /**
     * 整体更新某个 AppItem（用于替换图标、修改名称等?
     */
    suspend fun updateAppItem(appItem: AppItem) {

        appItemDao.update(appItem)

    }

    /**
     * 重排集合?App
     */
    suspend fun reorderApps(items: List<AppItem>) {

        items.forEachIndexed { index, item ->

            if (item.sortOrder != index) {

                appItemDao.update(item.copy(sortOrder = index))

            }

        }

    }

    /**
     * 加载已安?App 列表（带 Icon Pack 替换?
     */
    suspend fun loadInstalledApps(): List<com.fengnian.folderdrawer.util.InstalledApp> =

        withContext(Dispatchers.IO) {

            AppUtils.getInstalledApps(context)

        }

    /**
     * 导出所有抽屉为 JSON 字符串（含抽屉配置和内含?APP?
     */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {

        val collections = collectionDao.getAll()

        val root = JSONObject()

        root.put("version", 2)

        root.put("exportedAt", System.currentTimeMillis())

        // ===== 全局设置（旧版本未导出，导致「只导入全局设置」无从谈起）=====
        val gs = JSONObject()
        gs.put(DialogSettings.KEY_BG_COLOR, DialogSettings.getBgColor(context))
        gs.put(DialogSettings.KEY_BG_ALPHA, DialogSettings.getBgAlpha(context))
        gs.put(DialogSettings.KEY_BLUR_RADIUS, DialogSettings.getBlurRadius(context))
        gs.put(DialogSettings.KEY_MARGIN_H, DialogSettings.getMarginHorizontal(context))
        gs.put(DialogSettings.KEY_POSITION, DialogSettings.getPosition(context))
        gs.put(DialogSettings.KEY_BOTTOM_MARGIN, DialogSettings.getBottomMargin(context))
        gs.put(DialogSettings.KEY_ROW_SPACING, DialogSettings.getRowSpacing(context))
        gs.put(DialogSettings.KEY_COLUMN_SPACING, DialogSettings.getColSpacing(context))
        gs.put(DialogSettings.KEY_ANIM_DIRECTION, DialogSettings.getAnimDirection(context))
        gs.put(DialogSettings.KEY_ANIM_STYLE, DialogSettings.getAnimStyle(context))
        gs.put(DialogSettings.KEY_ANIM_DURATION, DialogSettings.getAnimDuration(context))
        gs.put(DialogSettings.KEY_TAB_HEIGHT_DP, DialogSettings.getTabHeightDp(context))
        root.put("globalSettings", gs)

        val collArray = JSONArray()

        for (c in collections) {

            val apps = appItemDao.getByCollection(c.id)

            val collObj = JSONObject()

            collObj.put("name", c.name)

            collObj.put("iconColor", c.iconColor)

            collObj.put("shortcutIconDrawable", c.shortcutIconDrawable ?: JSONObject.NULL)

            collObj.put("isDefault", c.isDefault)

            collObj.put("sortOrder", c.sortOrder)

            collObj.put("backgroundColor", c.backgroundColor)

            collObj.put("backgroundAlpha", c.backgroundAlpha)

            collObj.put("blurRadius", c.blurRadius)

            collObj.put("blurType", c.blurType)

            collObj.put("titleTextColor", c.titleTextColor)

            collObj.put("appNameTextColor", c.appNameTextColor)

            collObj.put("gridColumns", c.gridColumns)

            collObj.put("iconSizeDp", c.iconSizeDp)

            collObj.put("showAppName", c.showAppName)

            collObj.put("compactAppName", c.compactAppName)

            collObj.put("appNameSizeSp", c.appNameSizeSp)

            collObj.put("dialogMarginHorizontal", c.dialogMarginHorizontal)

            collObj.put("dialogPosition", c.dialogPosition)

            collObj.put("bottomMarginDp", c.bottomMarginDp)

            collObj.put("dialogRowSpacing", c.dialogRowSpacing)

            collObj.put("dialogColumnSpacing", c.dialogColumnSpacing)

            collObj.put("dialogAnimDirection", c.dialogAnimDirection)

            collObj.put("dialogAnimStyle", c.dialogAnimStyle)

            collObj.put("dialogAnimDuration", c.dialogAnimDuration)
            collObj.put("showInDialog", c.showInDialog)
            collObj.put("createdAt", c.createdAt)

            val appArray = JSONArray()

            for (a in apps) {

                val appObj = JSONObject()

                appObj.put("packageName", a.packageName)

                appObj.put("activityClassName", a.activityClassName)

                appObj.put("displayName", a.displayName)

                appObj.put("sortOrder", a.sortOrder)

                appObj.put("customIconPath", a.customIconPath ?: JSONObject.NULL)

                appObj.put("customIconSource", a.customIconSource ?: JSONObject.NULL)

                // 把自定义图标图片（gallery/iconpack 落盘到 filesDir/app_icons 的 PNG）内联为 base64，
                // 使导出文件自包含，换设备/重装后图标不丢失
                if (!a.customIconPath.isNullOrBlank()) {
                    val iconFile = java.io.File(a.customIconPath)
                    if (iconFile.exists() && iconFile.isFile) {
                        try {
                            val bytes = iconFile.readBytes()
                            appObj.put(
                                "customIconData",
                                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

                appArray.put(appObj)

            }

            collObj.put("apps", appArray)

            collArray.put(collObj)

        }

        root.put("collections", collArray)

        root.toString(2)

    }

    /**
     * 解析备份 JSON（纯解析，不触碰数据库）。兼容 v1（无 globalSettings）与 v2。
     */
    fun parseBackup(jsonStr: String): BackupData {
        val root = JSONObject(jsonStr)
        val version = root.optInt("version", 1)
        val gsObj = root.optJSONObject("globalSettings")
        val globalSettings = gsObj?.let { obj ->
            val map = mutableMapOf<String, Any>()
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                map[k] = obj.get(k)
            }
            map
        }
        val collArray = root.optJSONArray("collections") ?: JSONArray()
        val collections = mutableListOf<ParsedCollection>()
        for (i in 0 until collArray.length()) {
            val ec = collArray.getJSONObject(i)
            val appArray = ec.optJSONArray("apps") ?: JSONArray()
            val apps = mutableListOf<ParsedApp>()
            for (j in 0 until appArray.length()) {
                val a = appArray.getJSONObject(j)
                apps.add(
                    ParsedApp(
                        packageName = a.optString("packageName", ""),
                        activityClassName = a.optString("activityClassName", ""),
                        displayName = a.optString("displayName", ""),
                        sortOrder = a.optInt("sortOrder", 0),
                        customIconPath = if (a.isNull("customIconPath")) null
                            else a.optString("customIconPath", "").takeIf { it.isNotEmpty() },
                        customIconSource = if (a.isNull("customIconSource")) null
                            else a.optString("customIconSource", "").takeIf { it.isNotEmpty() },
                        customIconData = if (a.isNull("customIconData")) null
                            else a.optString("customIconData", "").takeIf { it.isNotEmpty() }
                    )
                )
            }
            collections.add(
                ParsedCollection(
                    name = ec.optString("name", "未命名抽屉"),
                    isDefault = ec.optBoolean("isDefault", false),
                    sortOrder = ec.optInt("sortOrder", 0),
                    createdAt = ec.optLong("createdAt", System.currentTimeMillis()),
                    iconColor = ec.optInt("iconColor", 0xFFC4704A.toInt()),
                    shortcutIconDrawable = if (ec.isNull("shortcutIconDrawable")) null
                        else ec.optString("shortcutIconDrawable", "").takeIf { it.isNotEmpty() },
                    backgroundColor = ec.optInt("backgroundColor", 0),
                    backgroundAlpha = ec.optInt("backgroundAlpha", 120),
                    blurRadius = ec.optInt("blurRadius", 10),
                    blurType = ec.optString("blurType", "gaussian"),
                    titleTextColor = ec.optInt("titleTextColor", 0),
                    appNameTextColor = ec.optInt("appNameTextColor", 0),
                    gridColumns = ec.optInt("gridColumns", 5),
                    iconSizeDp = ec.optInt("iconSizeDp", 48),
                    showAppName = ec.optBoolean("showAppName", true),
                    compactAppName = ec.optBoolean("compactAppName", false),
                    appNameSizeSp = ec.optInt("appNameSizeSp", 11),
                    dialogMarginHorizontal = ec.optInt("dialogMarginHorizontal", 28),
                    dialogPosition = ec.optString("dialogPosition", "center"),
                    bottomMarginDp = ec.optInt("bottomMarginDp", 16),
                    dialogRowSpacing = ec.optInt("dialogRowSpacing", 8),
                    dialogColumnSpacing = ec.optInt("dialogColumnSpacing", 8),
                    dialogAnimDirection = ec.optString("dialogAnimDirection", "bottom"),
                    dialogAnimStyle = ec.optString("dialogAnimStyle", "spring"),
                    dialogAnimDuration = ec.optInt("dialogAnimDuration", 300),
                    showInDialog = ec.optBoolean("showInDialog", true),
                    apps = apps
                )
            )
        }
        return BackupData(version, collections, globalSettings)
    }

    /**
     * 当前已存在的抽屉名称列表（用于导入时重名检测）
     */
    suspend fun existingCollectionNames(): List<String> = withContext(Dispatchers.IO) {
        collectionDao.getAll().map { it.name }
    }

    /**
     * 选择性导入：
     * @param selectedIndices 文件内要导入的抽屉下标
     * @param nameOverrides   下标 -> 用户指定的新名称（可空，空则用原名）
     * @param applyGlobal     是否同时应用全局设置
     * @return 成功导入的抽屉数
     * 行为：追加（不删除现有抽屉）；重名自动去重；导入图标文件落盘；保护已有默认抽屉不被重复。
     */
    suspend fun importSelected(
        backup: BackupData,
        selectedIndices: List<Int>,
        nameOverrides: Map<Int, String>,
        applyGlobal: Boolean
    ): Int = withContext(Dispatchers.IO) {
        val existing = collectionDao.getAll()
        val existingNames = existing.map { it.name }.toSet()
        val hasExistingDefault = existing.any { it.isDefault }
        val usedNames = mutableSetOf<String>()
        var imported = 0
        for (idx in selectedIndices) {
            val pc = backup.collections.getOrNull(idx) ?: continue
            var base = (nameOverrides[idx] ?: pc.name).trim()
            if (base.isBlank()) base = pc.name
            var candidate = base
            var n = 1
            while (existingNames.contains(candidate) || usedNames.contains(candidate)) {
                candidate = "$base ($n)"
                n++
            }
            usedNames.add(candidate)
            val newId = collectionDao.insert(
                Collection(
                    name = candidate,
                    iconColor = pc.iconColor,
                    shortcutIconDrawable = pc.shortcutIconDrawable,
                    isDefault = if (pc.isDefault && hasExistingDefault) false else pc.isDefault,
                    sortOrder = pc.sortOrder,
                    backgroundColor = pc.backgroundColor,
                    backgroundAlpha = pc.backgroundAlpha,
                    blurRadius = pc.blurRadius,
                    blurType = pc.blurType,
                    titleTextColor = pc.titleTextColor,
                    appNameTextColor = pc.appNameTextColor,
                    gridColumns = pc.gridColumns,
                    iconSizeDp = pc.iconSizeDp,
                    showAppName = pc.showAppName,
                    compactAppName = pc.compactAppName,
                    appNameSizeSp = pc.appNameSizeSp,
                    dialogMarginHorizontal = pc.dialogMarginHorizontal,
                    dialogPosition = pc.dialogPosition,
                    bottomMarginDp = pc.bottomMarginDp,
                    dialogRowSpacing = pc.dialogRowSpacing,
                    dialogColumnSpacing = pc.dialogColumnSpacing,
                    dialogAnimDirection = pc.dialogAnimDirection,
                    dialogAnimStyle = pc.dialogAnimStyle,
                    dialogAnimDuration = pc.dialogAnimDuration,
                    createdAt = pc.createdAt,
                    showInDialog = pc.showInDialog
                )
            )
            for (app in pc.apps) {
                val newPath = if (!app.customIconData.isNullOrBlank()) {
                    saveImportedIcon(app.customIconData)
                } else app.customIconPath?.takeIf { it.isNotEmpty() }
                appItemDao.insert(
                    AppItem(
                        collectionId = newId,
                        packageName = app.packageName,
                        activityClassName = app.activityClassName,
                        displayName = app.displayName,
                        sortOrder = app.sortOrder,
                        customIconPath = newPath,
                        customIconSource = app.customIconSource
                    )
                )
            }
            imported++
        }
        if (applyGlobal) backup.globalSettings?.let { importGlobalSettings(it) }
        imported
    }

    /**
     * 把内联的 base64 图标写回私有目录，返回新路径（失败返回 null）
     */
    private fun saveImportedIcon(base64: String): String? {
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            val dir = java.io.File(context.filesDir, "app_icons").apply { mkdirs() }
            val file = java.io.File(
                dir,
                "icon_imported_${System.currentTimeMillis()}_${(Math.random() * 100000).toInt()}.png"
            )
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 应用全局设置（从备份的 globalSettings 映射写回 DialogSettings）
     */
    suspend fun importGlobalSettings(map: Map<String, Any>) = withContext(Dispatchers.IO) {
        val ctx = context
        fun int(k: String, setter: (Context, Int) -> Unit) {
            (map[k] as? Int)?.let { setter(ctx, it) }
        }

        fun str(k: String, setter: (Context, String) -> Unit) {
            (map[k] as? String)?.let { setter(ctx, it) }
        }
        int(DialogSettings.KEY_BG_COLOR) { c, v -> DialogSettings.setBgColor(c, v) }
        int(DialogSettings.KEY_BG_ALPHA) { c, v -> DialogSettings.setBgAlpha(c, v) }
        int(DialogSettings.KEY_BLUR_RADIUS) { c, v -> DialogSettings.setBlurRadius(c, v) }
        int(DialogSettings.KEY_MARGIN_H) { c, v -> DialogSettings.setMarginHorizontal(c, v) }
        str(DialogSettings.KEY_POSITION) { c, v -> DialogSettings.setPosition(c, v) }
        int(DialogSettings.KEY_BOTTOM_MARGIN) { c, v -> DialogSettings.setBottomMargin(c, v) }
        int(DialogSettings.KEY_ROW_SPACING) { c, v -> DialogSettings.setRowSpacing(c, v) }
        int(DialogSettings.KEY_COLUMN_SPACING) { c, v -> DialogSettings.setColSpacing(c, v) }
        str(DialogSettings.KEY_ANIM_DIRECTION) { c, v -> DialogSettings.setAnimDirection(c, v) }
        str(DialogSettings.KEY_ANIM_STYLE) { c, v -> DialogSettings.setAnimStyle(c, v) }
        int(DialogSettings.KEY_ANIM_DURATION) { c, v -> DialogSettings.setAnimDuration(c, v) }
        int(DialogSettings.KEY_TAB_HEIGHT_DP) { c, v -> DialogSettings.setTabHeightDp(c, v) }
    }

    /**
     * 删除所有非默认抽屉
     */
    suspend fun deleteAllNonDefaultCollections(): Int = withContext(Dispatchers.IO) {

        val all = collectionDao.getAll()

        var count = 0

        for (c in all) {

            if (!c.isDefault) {

                appItemDao.deleteByCollection(c.id)

                collectionDao.deleteById(c.id)

                count++

            }

        }

        count

    }

}

// ==================== 备份导入导出：解析与选择性导入 ====================

/**
 * 解析后的单个 APP（导出时可能内联图标 base64）
 */
data class ParsedApp(
    val packageName: String,
    val activityClassName: String,
    val displayName: String,
    val sortOrder: Int,
    val customIconPath: String?,
    val customIconSource: String?,
    val customIconData: String?   // base64（gallery/iconpack 落盘图片），为空则不内联
)

/**
 * 解析后的单个抽屉
 */
data class ParsedCollection(
    val name: String,
    val isDefault: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val iconColor: Int,
    val shortcutIconDrawable: String?,
    val backgroundColor: Int,
    val backgroundAlpha: Int,
    val blurRadius: Int,
    val blurType: String,
    val titleTextColor: Int,
    val appNameTextColor: Int,
    val gridColumns: Int,
    val iconSizeDp: Int,
    val showAppName: Boolean,
    val compactAppName: Boolean,
    val appNameSizeSp: Int,
    val dialogMarginHorizontal: Int,
    val dialogPosition: String,
    val bottomMarginDp: Int,
    val dialogRowSpacing: Int,
    val dialogColumnSpacing: Int,
    val dialogAnimDirection: String,
    val dialogAnimStyle: String,
    val dialogAnimDuration: Int,
    val showInDialog: Boolean,
    val apps: List<ParsedApp>
)

/**
 * 解析后的完整备份
 */
data class BackupData(
    val version: Int,
    val collections: List<ParsedCollection>,
    val globalSettings: Map<String, Any>?
)
