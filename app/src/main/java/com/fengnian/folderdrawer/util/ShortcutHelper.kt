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
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.fengnian.folderdrawer.CollectionLauncherActivity
import com.fengnian.folderdrawer.DialerConstants
import com.fengnian.folderdrawer.DialerLauncherActivity
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
            buildShortcutInfo(context, collection, iconPackManager, adaptive = true)
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
        iconPackManager: com.fengnian.folderdrawer.iconpack.IconPackManager,
        adaptive: Boolean = false
    ): ShortcutInfoCompat {
        // 特殊抽屉：APP Dialer（id=-2）走拨号盘图标与 intent，复用 CollectionLauncherActivity 链路
        if (collection.id == DialerConstants.DIALER_COLLECTION_ID) {
            return buildDialerShortcutInfo(context, adaptive)
        }
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
        // 圈色（自定义图标透明边距露出的兜底色）改为白色，避免露出主题主色；
        // 仅当用户主动设置 iconColor 时，才以其作为整块图标底色。
        val bgColor = if (collection.iconColor != 0) collection.iconColor else Color.WHITE

        // 普通 createWithBitmap + 不透明方形位图（v2.29 处方，方形不裁圆）：
        // 自适应图标（图标包）直接画满画布、不填额外主色；非自适应图标以主色兜底保证
        // 完全不透明，再把图标画满整张画布（主色仅在图标本身透明处极小露出）。
        // 这样保留图标原始方形/圆角方形状，不裁成圆形；"一圈颜色"被压到最薄。
        // （曾试 createWithAdaptiveBitmap 触发更宽自适应蒙版、圆形裁剪改变形状，均不如本方案。）
        val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = if (customDrawable != null) {
                drawableToAdaptiveBitmap(customDrawable, bgColor, context.resources.displayMetrics.density)
            } else {
                createAdaptiveHamburgerIcon(collection.iconColor)
            }
            // 动态快捷方式（长按菜单）用自适应图标：启动器不再额外套圆形底色板，消除"背景色"；
            // 桌面 pin 仍用普通位图（保持方形白圈处方）。
            if (adaptive) IconCompat.createWithAdaptiveBitmap(bitmap) else IconCompat.createWithBitmap(bitmap)
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
     * 将 Icon Pack 图标转为完全不透明的圆形位图
     * 尺寸：108dp * density。裁剪为圆形并填充底色兜底，
     * 启动器套圆形蒙版时正好填满、无月牙底色；圆角方蒙版下圆形被裁也无底色。
     */
    /**
     * 将 Icon Pack 图标转为完全不透明的方形位图（v2.29 处方，方形不裁圆）。
     * 尺寸：108dp * density（精确匹配系统要求的物理像素）。
     * - 自适应图标（AdaptiveIconDrawable）：其背景层本就铺满 bounds，直接画满整张画布，
     *   不额外填主色，四角即图标包背景色，无"主色圈"。
     * - 其它图标（如位图）：先填主色兜底保证不透明，再把图标画满整张画布
     *   （不再缩进留边），主色仅在图标自身透明像素处极小露出，圈色最薄。
     */
    private fun drawableToAdaptiveBitmap(
        drawable: Drawable,
        bgColor: Int,
        density: Float
    ): Bitmap {
        val size = (108f * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (drawable is android.graphics.drawable.AdaptiveIconDrawable) {
            // 自适应图标：背景铺满整张画布，天然不透明，无需额外加底色
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        } else {
            // 兜底：填充主色保证不透明，图标画满整张画布（主色仅在透明像素处极小露出）
            canvas.drawColor(bgColor)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }

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

    /**
     * 创建并钉「APP Dialer」快捷方式到桌面。
     * 经 DialerLauncherActivity（NoDisplay）路由，data URI = folderdrawer://dialer。
     */
    fun pinDialerToHome(context: Context): Boolean {
        val shortcut = buildDialerShortcutInfo(context)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    private fun buildDialerShortcutInfo(context: Context, adaptive: Boolean = false): ShortcutInfoCompat {
        val launchIntent = Intent(context, CollectionLauncherActivity::class.java).apply {
            action = CollectionLauncherActivity.ACTION_LAUNCH_COLLECTION
            // 把语义编入 data URI：folderdrawer://collection/-2，复用普通抽屉的稳定路由链路
            data = Uri.parse("${CollectionLauncherActivity.SCHEME}://${CollectionLauncherActivity.DATA_HOST}/${DialerConstants.DIALER_COLLECTION_ID}")
            // 与普通抽屉的动态快捷方式保持一致：携带 EXTRA_COLLECTION_ID 并加 CLEAR_TOP，
            // 避免部分手势/启动器软件因 Intent 结构不一致而在添加时卡死
            putExtra(CollectionLauncherActivity.EXTRA_COLLECTION_ID, DialerConstants.DIALER_COLLECTION_ID)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }

        val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && adaptive) {
            IconCompat.createWithAdaptiveBitmap(getDialerShortcutIcon(context))
        } else {
            IconCompat.createWithBitmap(getDialerShortcutIcon(context))
        }

        return ShortcutInfoCompat.Builder(context, "dialer_main")
            .setShortLabel("APP Dialer")
            .setLongLabel("APP Dialer")
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
    }

    /**
     * 返回 Dialer 桌面快捷方式图标位图：
     * 若用户在设置里自定义了图标（图标包 drawable 名），则像抽屉那样从图标包取图并渲染；
     * 否则回退到内置的「九宫格键盘」图标。尺寸与抽屉快捷方式一致（Oreo+ 用 108dp 自适应位图，
     * 旧系统用 192px 普通位图），避免部分软件处理超大 bitmap 时变慢/卡死。
     */
    fun getDialerShortcutIcon(context: Context): Bitmap {
        val ipm = com.fengnian.folderdrawer.iconpack.IconPackManager.getInstance(context)
        val name = com.fengnian.folderdrawer.util.DialogSettings.getDialerShortcutIconName(context)
        val customDrawable = if (name.isNotBlank()) ipm.getDrawableByName(name) else null
        val primary = context.getColor(com.fengnian.folderdrawer.R.color.primary)
        val density = context.resources.displayMetrics.density
        return if (customDrawable != null) {
            // 自定义图标：圈色（图标透明边距）改为白色，避免露出主题主色
            val bg = Color.WHITE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                drawableToAdaptiveBitmap(customDrawable, bg, density)
            } else {
                drawableToLegacyBitmap(customDrawable)
            }
        } else {
            // 内置九宫格图标：保持主色底，否则白色键盘图案在白底上不可见
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createDialerIcon(primary, (108f * density).toInt())
            } else {
                createDialerIcon(primary, 192)
            }
        }
    }

    /**
     * 生成一个「九宫格拨号键盘」图标位图（sizePx × sizePx，不透明背景），
     * 用于 Dialer 快捷方式。绕过系统对自适应图标套蒙版导致的白边。
     */
    private fun createDialerIcon(bgColor: Int, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = bgColor
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)

        paint.color = Color.WHITE
        val pad = sizePx * 0.16f
        val gridArea = sizePx - pad * 2
        val cols = 3
        val rows = 4
        val cellW = gridArea / cols
        val cellH = gridArea / rows
        val cell = minOf(cellW, cellH) * 0.62f
        val radius = cell * 0.22f
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = pad + c * cellW + cellW / 2f
                val cy = pad + r * cellH + cellH / 2f
                canvas.drawRoundRect(
                    cx - cell / 2f, cy - cell / 2f,
                    cx + cell / 2f, cy + cell / 2f,
                    radius, radius, paint
                )
            }
        }
        return bitmap
    }
}
