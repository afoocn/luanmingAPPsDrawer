package com.fengnian.folderdrawer.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.fengnian.folderdrawer.CollectionLauncherActivity
import com.fengnian.folderdrawer.data.Collection

object ShortcutHelper {

    /**
     * 发布动态快捷方式（Dynamic Shortcuts?     *
     * 让手势软件、Tasker 等第三方应用可以通过 LauncherApps / ShortcutManagerCompat
     * 查询到各集合的快捷方式入口，直接调用打开对应集合弹窗?     *
     * 应在集合列表变化时调用（?MainActivity ?LiveData 回调）?     */
    fun publishDynamicShortcuts(context: Context, collections: List<Collection>) {
        val maxCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        if (maxCount <= 0) return

        val iconPackManager = com.fengnian.folderdrawer.iconpack.IconPackManager.getInstance(context)
        val toPublish = collections.take(maxCount)

        val shortcuts = toPublish.map { collection ->
            buildShortcutInfo(context, collection, iconPackManager)
        }

        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    fun pinCollectionToHome(context: Context, collection: Collection): Boolean {
        val iconPackManager = com.fengnian.folderdrawer.iconpack.IconPackManager.getInstance(context)
        val shortcut = buildShortcutInfo(context, collection, iconPackManager)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    /**
     * 构建单个集合?ShortcutInfoCompat（pin ?dynamic 共用?     */
    private fun buildShortcutInfo(
        context: Context,
        collection: Collection,
        iconPackManager: com.fengnian.folderdrawer.iconpack.IconPackManager
    ): ShortcutInfoCompat {
        val launchIntent = Intent(context, CollectionLauncherActivity::class.java).apply {
            action = CollectionLauncherActivity.ACTION_LAUNCH_COLLECTION
            // 把 collection.id 编入 data URI：启动器不会丢弃 data，且能让各 shortcut 的
            // Intent 在 filterEquals 下彼此区分，避免被合并后"总打开第一个抽屉"。
            data = Uri.parse("${CollectionLauncherActivity.SCHEME}://${CollectionLauncherActivity.DATA_HOST}/${collection.id}")
            putExtra(CollectionLauncherActivity.EXTRA_COLLECTION_ID, collection.id)
            // 关键：禁用系统级过渡动画，避免点击快捷方式时系统强制缩放展开
            // 覆盖应用自定义弹窗动画。配合 QuickLaunchDialogActivity 的自定义入场动画。
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }

        val customDrawable = collection.shortcutIconDrawable?.let {
            iconPackManager.getDrawableByName(it)
        }

        // API 26+: 使用 adaptive bitmap
        // API < 26: 使用普?bitmap
        // 使用 createWithBitmap 而非 createWithAdaptiveBitmap
        // 原因：小米澎湃 OS 等系统会对 Adaptive Icon 自动套用蒙版裁剪，
        // 如果图标透明区域或圆角小于系统底板圆角，会露出白色底板形成白边。
        // Shortcut Maker 的做法：用完全不透明的位图 + createWithBitmap，
        // 绕过系统的自适应图标处理，避免白边。
        val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = if (customDrawable != null) {
                drawableToShortcutBitmap(customDrawable, context.resources.displayMetrics.density)
            } else {
                createAdaptiveHamburgerIcon(collection.iconColor)
            }
            IconCompat.createWithBitmap(bitmap)
        } else {
            val bitmap = if (customDrawable != null) {
                drawableToLegacyBitmap(customDrawable)
            } else {
                createLegacyHamburgerIcon(collection.iconColor)
            }
            IconCompat.createWithBitmap(bitmap)
        }

        return ShortcutInfoCompat.Builder(context, "collection_${collection.id}")
            .setShortLabel(collection.name)
            .setLongLabel(collection.name)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
    }

    /**
     * 将 Icon Pack 图标转为完全不透明的正方形位图
     * 尺寸：108dp * density（精确匹配系统要求的物理像素）
     * 背景：APP 主色 #C4704A 填充整个画布，不留任何透明区域
     * 图标：居中缩放至安全区（72dp * density），直接绘制不做圆形裁剪
     */
    private fun drawableToAdaptiveBitmap(
        drawable: android.graphics.drawable.Drawable,
        bgColor: Int,
        density: Float
    ): Bitmap {
        // 108dp 对应物理像素 = 108 * density
        val size = (108f * density).toInt()
        // 安全区 72dp，图标在安全区内居中缩放
        val safeSize = (72f * density).toInt()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 填充 APP 主色背景，确保完全不透明
        canvas.drawColor(bgColor)

        // 图标居中缩放至安全区，不进行任何圆形裁剪
        val iw = drawable.intrinsicWidth
        val ih = drawable.intrinsicHeight

        if (iw > 0 && ih > 0) {
            val scale = safeSize.toFloat() / maxOf(iw, ih)
            val sw = (iw * scale).toInt()
            val sh = (ih * scale).toInt()
            val left = (size - sw) / 2
            val top = (size - sh) / 2
            drawable.setBounds(left, top, left + sw, top + sh)
        } else {
            val offset = (size - safeSize) / 2
            drawable.setBounds(offset, offset, offset + safeSize, offset + safeSize)
        }
        drawable.draw(canvas)

        return bitmap
    }

    /**
     * 将用户自选图标转为透明背景的 shortcut 位图
     * 尺寸 108dp × density，背景透明，图标居中缩放至安全区
     */
    private fun drawableToShortcutBitmap(
        drawable: android.graphics.drawable.Drawable,
        density: Float
    ): Bitmap {
        val size = (108f * density).toInt()
        val safeSize = (96f * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val iw = drawable.intrinsicWidth
        val ih = drawable.intrinsicHeight
        if (iw > 0 && ih > 0) {
            val scale = safeSize.toFloat() / maxOf(iw, ih)
            val sw = (iw * scale).toInt()
            val sh = (ih * scale).toInt()
            val left = (size - sw) / 2
            val top = (size - sh) / 2
            drawable.setBounds(left, top, left + sw, top + sh)
        } else {
            val offset = (size - safeSize) / 2
            drawable.setBounds(offset, offset, offset + safeSize, offset + safeSize)
        }
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * ?Icon Pack 图标转为旧版 bitmap?92x192，用?API < 26?     * 透明背景
     */
    private fun drawableToLegacyBitmap(
        drawable: android.graphics.drawable.Drawable
    ): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.TRANSPARENT)

        val iw = drawable.intrinsicWidth
        val ih = drawable.intrinsicHeight
        if (iw > 0 && ih > 0) {
            val drawSize = (size * 0.85f).toInt()
            val scale = drawSize.toFloat() / maxOf(iw, ih)
            val sw = (iw * scale).toInt()
            val sh = (ih * scale).toInt()
            val left = (size - sw) / 2
            val top = (size - sh) / 2
            drawable.setBounds(left, top, left + sw, top + sh)
        } else {
            drawable.setBounds(0, 0, size, size)
        }
        drawable.draw(canvas)

        return bitmap
    }

    /**
     * Create an adaptive icon bitmap (432x432) with hamburger lines.
     */
    private fun createAdaptiveHamburgerIcon(bgColor: Int): Bitmap {
        val size = 432
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Full background
        paint.color = bgColor
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Subtle gradient overlay
        val gradient = LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            intArrayOf(
                Color.argb(40, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Color.argb(30, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null

        // Hamburger lines
        val lineWidth = 216f
        val lineHeight = 20f
        val cornerRadius = 10f
        val gap = 16f
        val totalH = lineHeight * 3 + gap * 2
        val startY = (size - totalH) / 2f
        val left = (size - lineWidth) / 2f

        paint.color = Color.WHITE
        for (i in 0..2) {
            val top = startY + i * (lineHeight + gap)
            canvas.drawRoundRect(left, top, left + lineWidth, top + lineHeight, cornerRadius, cornerRadius, paint)
        }

        return bitmap
    }

    /**
     * Create a legacy icon bitmap (192x192) with hamburger lines.
     */
    private fun createLegacyHamburgerIcon(bgColor: Int): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Rounded square background
        paint.color = bgColor
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val radius = size * 0.2f
        canvas.drawRoundRect(rect, radius, radius, paint)

        // Subtle gradient overlay
        val gradient = LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            intArrayOf(
                Color.argb(40, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.5f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null

        // Hamburger lines
        val lineWidth = 96f
        val lineHeight = 10f
        val cornerRadius = 5f
        val gap = 8f
        val totalH = lineHeight * 3 + gap * 2
        val startY = (size - totalH) / 2f
        val left = (size - lineWidth) / 2f

        paint.color = Color.WHITE
        for (i in 0..2) {
            val top = startY + i * (lineHeight + gap)
            canvas.drawRoundRect(left, top, left + lineWidth, top + lineHeight, cornerRadius, cornerRadius, paint)
        }

        return bitmap
    }

    /**
     * 构建旧版 bitmap 图标?92x192），用于 ACTION_CREATE_SHORTCUT API
     * 使用普?bitmap 而非 adaptive bitmap，兼容所有手?自动化软?     */
    fun buildLegacyShortcutBitmap(context: Context, collection: Collection): Bitmap {
        val iconPackManager = com.fengnian.folderdrawer.iconpack.IconPackManager.getInstance(context)
        val customDrawable = collection.shortcutIconDrawable?.let {
            iconPackManager.getDrawableByName(it)
        }
        return if (customDrawable != null) {
            drawableToLegacyBitmap(customDrawable)
        } else {
            createLegacyHamburgerIcon(collection.iconColor)
        }
    }

    fun isRequestPinShortcutSupported(context: Context): Boolean {
        return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    }
}
