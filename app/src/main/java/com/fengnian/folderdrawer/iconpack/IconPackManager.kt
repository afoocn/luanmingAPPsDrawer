package com.fengnian.folderdrawer.iconpack

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * Manages Android standard Icon Pack protocol (ADW Launcher format).
 *
 * An icon pack is an app that declares:
 * <intent-filter>
 * <action android:name="org.adw.launcher.THEMES" />
 * <category android:name="android.intent.category.DEFAULT" />
 * </intent-filter>
 *
 * It contains an appfilter.xml resource mapping component names to drawable names:
 * <item component="ComponentInfo{pkg/activity}" drawable="icon_name" />
 */
class IconPackManager(private val context: Context) {

 data class IconPackInfo(
 val packageName: String,
 val label: String
 )

 private var iconPackPackage: String? = null
 private var componentToDrawable: Map<String, String> = emptyMap()
 private var iconPackResources: android.content.res.Resources? = null

 companion object {
 private const val PREF_NAME = "icon_pack_prefs"
 private const val KEY_ACTIVE_PACK = "active_icon_pack"
 private const val ICON_PACK_ACTION = "org.adw.launcher.THEMES"

 @Volatile
 private var instance: IconPackManager? = null

 fun getInstance(context: Context): IconPackManager {
 return instance ?: synchronized(this) {
 instance ?: IconPackManager(context.applicationContext).also { instance = it }
 }
 }
 }

 init {
 loadActivePack()
 }

 /**
 * Find all installed icon packs.
 */
 fun getInstalledIconPacks(): List<IconPackInfo> {
 val pm = context.packageManager
 val intent = Intent(ICON_PACK_ACTION)
 val resolveList: List<ResolveInfo> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
 pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
 } else {
 @Suppress("DEPRECATION")
 pm.queryIntentActivities(intent, 0)
 }

 return resolveList.map { ri ->
 IconPackInfo(
 packageName = ri.activityInfo.packageName,
 label = ri.loadLabel(pm).toString()
 )
 }.sortedBy { it.label.lowercase() }
 }

 /**
 * Set the active icon pack. Pass null to use system icons.
 */
 fun setActiveIconPack(packageName: String?) {
 context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
 .edit()
 .putString(KEY_ACTIVE_PACK, packageName)
 .apply()
 loadActivePack()
 }

 fun getActiveIconPack(): String? {
 return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
 .getString(KEY_ACTIVE_PACK, null)
 }

 private fun loadActivePack() {
 val packPkg = getActiveIconPack()
 if (packPkg.isNullOrEmpty()) {
 iconPackPackage = null
 componentToDrawable = emptyMap()
 iconPackResources = null
 return
 }

 try {
 val pm = context.packageManager
 val res = pm.getResourcesForApplication(packPkg)
 iconPackPackage = packPkg
 iconPackResources = res

 // Parse appfilter.xml
 val filterId = res.getIdentifier("appfilter", "xml", packPkg)
 if (filterId != 0) {
 componentToDrawable = parseAppFilter(res.getXml(filterId))
 }
 } catch (e: Exception) {
 e.printStackTrace()
 iconPackPackage = null
 componentToDrawable = emptyMap()
 iconPackResources = null
 }
 }

 /**
 * Parse appfilter.xml to build component -> drawable name map.
 */
 private fun parseAppFilter(parser: XmlResourceParser): Map<String, String> {
 val map = mutableMapOf<String, String>()
 try {
 var eventType = parser.eventType
 while (eventType != XmlPullParser.END_DOCUMENT) {
 if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
 val component = parser.getAttributeValue(null, "component")
 val drawable = parser.getAttributeValue(null, "drawable")
 if (component != null && drawable != null) {
 // component format: ComponentInfo{pkg/activity}
 val cleanComponent = component
 .replace("ComponentInfo{", "")
 .replace("}", "")
 .trim()
 map[cleanComponent] = drawable
 }
 }
 eventType = parser.next()
 }
 } catch (e: Exception) {
 e.printStackTrace()
 }
 return map
 }

 /**
 * Get the themed icon for a given app component.
 * Returns null if no icon pack is active or no mapping exists.
 */
 fun getThemedIcon(packageName: String, activityName: String): Drawable? {
 val res = iconPackResources ?: return null
 val packPkg = iconPackPackage ?: return null

 val componentKey = "$packageName/$activityName"
 val drawableName = componentToDrawable[componentKey] ?: return null

 val drawableId = res.getIdentifier(drawableName, "drawable", packPkg)
 if (drawableId == 0) return null

 return try {
 res.getDrawable(drawableId, null)
 } catch (e: Exception) {
 null
 }
 }

 /**
 * Check if an icon pack is currently active.
 */
 fun hasActiveIconPack(): Boolean = !iconPackPackage.isNullOrEmpty()

 /**
 * ?icon pack
 */
 fun refreshAvailablePacks() {
 loadActivePack()
 }

 /**
 * Icon Pack drawable
 * ?drawable.xml ?appfilter.xml
 */
 fun getAllDrawableNames(): List<String> {
 val res = iconPackResources ?: return emptyList()
 val packPkg = iconPackPackage ?: return emptyList()
 val names = linkedSetOf<String>()

 // 1. drawable.xml ?ADW
 try {
 val drawableXmlId = res.getIdentifier("drawable", "xml", packPkg)
 if (drawableXmlId != 0) {
 val parser = res.getXml(drawableXmlId)
 var eventType = parser.eventType
 while (eventType != XmlPullParser.END_DOCUMENT) {
 if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
 val drawable = parser.getAttributeValue(null, "drawable")
 if (drawable != null) names.add(drawable)
 }
 eventType = parser.next()
 }
 }
 } catch (_: Exception) {}

 // 2. ?appfilter.xml
 if (names.isEmpty()) {
 names.addAll(componentToDrawable.values)
 }

 return names.toList()
 }

 /**
 * ?drawable ?Icon Pack
 */
 fun getDrawableByName(drawableName: String): Drawable? {
 val res = iconPackResources ?: return null
 val packPkg = iconPackPackage ?: return null
 val drawableId = res.getIdentifier(drawableName, "drawable", packPkg)
 if (drawableId == 0) return null
 return try {
 res.getDrawable(drawableId, null)
 } catch (_: Exception) {
 null
 }
 }

 /**
 * icon pack fallback ? */
 fun getIcon(packageName: String, activityClassName: String, _fallbackName: String): Drawable? {
 val themed = getThemedIcon(packageName, activityClassName)
 if (themed != null) return themed
 return try {
 val pm = context.packageManager
 val info = pm.getActivityInfo(
 android.content.ComponentName(packageName, activityClassName),
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

 // ===== Icon Pack =====

 private var tempResources: android.content.res.Resources? = null
 private var tempPackPackage: String? = null

 /**
 * ?Icon Pack ? */
 fun loadPack(packPackage: String): Boolean {
 return try {
 val pm = context.packageManager
 tempResources = pm.getResourcesForApplication(packPackage)
 tempPackPackage = packPackage
 true
 } catch (e: Exception) {
 false
 }
 }

 /**
 * ?Icon Pack
 */
 fun clearTempPack() {
 tempResources = null
 tempPackPackage = null
 }

 /**
 * Icon Pack drawable ? */
 fun getAllDrawableNamesFromTemp(): List<String> {
 val res = tempResources ?: return getAllDrawableNames()
 val packPkg = tempPackPackage ?: iconPackPackage ?: return emptyList()
 val names = linkedSetOf<String>()

 try {
 val drawableXmlId = res.getIdentifier("drawable", "xml", packPkg)
 if (drawableXmlId != 0) {
 val parser = res.getXml(drawableXmlId)
 var eventType = parser.eventType
 while (eventType != XmlPullParser.END_DOCUMENT) {
 if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
 val drawable = parser.getAttributeValue(null, "drawable")
 if (drawable != null) names.add(drawable)
 }
 eventType = parser.next()
 }
 }
 } catch (_: Exception) {}

 if (names.isEmpty()) {
 // ?appfilter.xml
 try {
 val filterId = res.getIdentifier("appfilter", "xml", packPkg)
 if (filterId != 0) {
 val parser = res.getXml(filterId)
 var eventType = parser.eventType
 while (eventType != XmlPullParser.END_DOCUMENT) {
 if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
 val drawable = parser.getAttributeValue(null, "drawable")
 if (drawable != null) names.add(drawable)
 }
 eventType = parser.next()
 }
 }
 } catch (_: Exception) {}
 }

 return names.toList()
 }

 /**
 * Icon Pack drawable ? */
 fun getDrawableByNameFromTemp(drawableName: String): Drawable? {
 val res = tempResources ?: return getDrawableByName(drawableName)
 val packPkg = tempPackPackage ?: iconPackPackage ?: return null
 val drawableId = res.getIdentifier(drawableName, "drawable", packPkg)
 if (drawableId == 0) return null
 return try {
 res.getDrawable(drawableId, null)
 } catch (_: Exception) {
 null
 }
 }
}
