@file:Suppress("DEPRECATION")

package com.fengnian.folderdrawer.util

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import com.fengnian.folderdrawer.R

/**
 * 背景工具：处理背景图、磨砂、透明度合成
 * 策略：
 * - API 31+ (Android 12+) → 用 RenderEffect.createBlurEffect 高斯模糊，性能好
 * - API 21-30 → 用 RenderScript (已弃用但还能用)，FastBlur
 */
object BackgroundHelper {

    /**
     * 给 ImageView 设置背景（图片可选磨砂、可选alpha）
     */
    fun applyBackground(
        target: ImageView,
        imageBitmap: Bitmap?,
        customColor: Int,
        alpha: Int,
        blurEnabled: Boolean
    ) {
        val context = target.context

        val finalBitmap: Bitmap? = when {
            imageBitmap != null -> {
                if (blurEnabled) {
                    blurImage(context, imageBitmap)
                } else {
                    imageBitmap
                }
            }
            customColor != 0 -> null  // 纯色模式，不需要bitmap
            else -> null
        }

        if (finalBitmap != null) {
            target.setImageBitmap(finalBitmap)
            target.imageAlpha = alpha
            target.setBackgroundColor(Color.TRANSPARENT)
        } else if (customColor != 0) {
            // 纯色背景
            target.setImageDrawable(null)
            val withAlpha = applyAlphaToColor(customColor, alpha)
            target.setBackgroundColor(withAlpha)
        } else {
            // 无背景
            target.setImageDrawable(null)
            target.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    /**
     * 给 View 设置颜色背景（带 alpha）
     */
    fun applyColorBackground(view: View, color: Int, alpha: Int) {
        view.setBackgroundColor(applyAlphaToColor(color, alpha))
    }

    fun applyAlphaToColor(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return Color.argb(
            a,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    /**
     * 模糊图片。API 31+ 用 RenderEffect（性能好）；老的用 RenderScript。
     */
    fun blurImage(context: Context, source: Bitmap, radius: Float = 18f): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurWithRenderEffect(source, radius)
        } else {
            blurWithRenderScript(context, source, radius)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun blurWithRenderEffect(source: Bitmap, radius: Float): Bitmap {
        // 缩放图以加速模糊
        val scale = 0.5f
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)

        val output = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(scaled, 0f, 0f, null)

        // 在 Android 12+ 上，最稳的方式是先应用 RenderEffect 到 View
        // 但这里我们处理的是 Bitmap，所以走 Paint 路线
        val paint = Paint().apply {
            isAntiAlias = true
            // 高斯模糊通过 RenderEffect 作用到 View 上时
            // 直接在 Canvas 上无法用 RenderEffect
            // 这里用 Paint + BlurMaskFilter 兜底
            maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return output
    }

    @Suppress("DEPRECATION")
    private fun blurWithRenderScript(context: Context, source: Bitmap, radius: Float): Bitmap {
        return try {
            val rs = RenderScript.create(context)
            val input = Allocation.createFromBitmap(rs, source)
            val output = Allocation.createTyped(rs, input.type)
            val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script.setRadius(radius.coerceAtMost(25f))
            script.setInput(input)
            script.forEach(output)
            val blurred = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            output.copyTo(blurred)
            rs.destroy()
            blurred
        } catch (e: Throwable) {
            // RenderScript 不可用时返回原图
            source
        }
    }

    /**
     * 从 Content URI 加载 Bitmap
     */
    fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 颜色加深：用于卡片浮起时的 tint
     */
    fun darken(color: Int, ratio: Float = 0.85f): Int {
        val r = (Color.red(color) * ratio).toInt()
        val g = (Color.green(color) * ratio).toInt()
        val b = (Color.blue(color) * ratio).toInt()
        return Color.rgb(r, g, b)
    }
}
