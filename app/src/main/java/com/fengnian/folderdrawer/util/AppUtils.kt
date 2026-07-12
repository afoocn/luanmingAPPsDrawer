package com.fengnian.folderdrawer.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable
)

object AppUtils {

    /**
     * Get all launchable apps on the device.
     */
    fun getInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveList: List<ResolveInfo> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolveList.map { ri ->
            InstalledApp(
                packageName = ri.activityInfo.packageName,
                activityName = ri.activityInfo.name,
                label = ri.loadLabel(pm).toString(),
                icon = ri.loadIcon(pm)
            )
        }.sortedBy { it.label.lowercase() }
    }

    /**
     * Launch an app by package + activity.
     */
    fun launchApp(context: Context, packageName: String, activityName: String) {
        val intent = Intent().apply {
            setClassName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: try launching by package
            // try launching by package
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    /**
     * Get the icon for an app. If an icon pack is active, try to get the themed icon.
     */
    fun getAppIcon(
        context: Context,
        packageName: String,
        activityName: String
    ): Drawable? {
        return try {
            val pm = context.packageManager
            val info = pm.getActivityInfo(
                android.content.ComponentName(packageName, activityName),
                0
            )
            info.loadIcon(pm)
        } catch (e: Exception) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Check if a package is installed.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 加载集合内的 App（来自数据库），过滤掉未安装的?     * 用于主界面卡片的 app 图标预览?     */
    fun getCollectionApps(context: Context, collectionId: Long): List<AppPreviewItem> {
        val db = com.fengnian.folderdrawer.data.AppDatabase.get(context)
        val items = kotlinx.coroutines.runBlocking {
            db.appItemDao().getByCollection(collectionId)
        }
        return items.mapNotNull { item ->
            if (isAppInstalled(context, item.packageName)) {
                AppPreviewItem(
                    packageName = item.packageName,
                    activityName = item.activityClassName,
                    displayName = item.displayName
                )
            } else null
        }
    }
}

/**
 * 主界面卡片预览用的轻?App 数据
 */
data class AppPreviewItem(
    val packageName: String,
    val activityName: String,
    val displayName: String
)
