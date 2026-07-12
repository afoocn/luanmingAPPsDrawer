package com.fengnian.folderdrawer.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局弹窗设置管理器，使用 SharedPreferences 存储。
 */
object DialogSettings {
    private const val PREFS_NAME = "global_dialog_settings"

    // ── 弹窗背景 ──
    const val KEY_BG_COLOR = "bg_color"              // Int, 0 = 使用 surface
    const val KEY_BG_ALPHA = "bg_alpha"               // Int 0-255
    const val KEY_BLUR_RADIUS = "blur_radius"         // Int 0-25

    // ── 弹窗样式 ──
    const val KEY_MARGIN_H = "margin_horizontal"       // dp
    const val KEY_POSITION = "position"               // center / bottom
    const val KEY_BOTTOM_MARGIN = "bottom_margin"     // dp
    const val KEY_ROW_SPACING = "row_spacing"         // dp
    const val KEY_COLUMN_SPACING = "col_spacing"      // dp

    // ── 弹窗动画 ──
    const val KEY_ANIM_DIRECTION = "anim_direction"   // bottom / left / right / center
    const val KEY_ANIM_STYLE = "anim_style"           // slide / scale / spring
    const val KEY_ANIM_DURATION = "anim_duration"     // ms

    // ── 标签页高度 ──
    const val KEY_TAB_HEIGHT_DP = "tab_height_dp"     // dp

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 读取 ──

    fun getBgColor(ctx: Context): Int = prefs(ctx).getInt(KEY_BG_COLOR, 0)
    fun getBgAlpha(ctx: Context): Int = prefs(ctx).getInt(KEY_BG_ALPHA, 120)
    fun getBlurRadius(ctx: Context): Int = prefs(ctx).getInt(KEY_BLUR_RADIUS, 10)

    fun getMarginHorizontal(ctx: Context): Int = prefs(ctx).getInt(KEY_MARGIN_H, 28)
    fun getPosition(ctx: Context): String = prefs(ctx).getString(KEY_POSITION, "center") ?: "center"
    fun getBottomMargin(ctx: Context): Int = prefs(ctx).getInt(KEY_BOTTOM_MARGIN, 16)
    fun getRowSpacing(ctx: Context): Int = prefs(ctx).getInt(KEY_ROW_SPACING, 8)
    fun getColSpacing(ctx: Context): Int = prefs(ctx).getInt(KEY_COLUMN_SPACING, 8)

    fun getAnimDirection(ctx: Context): String = prefs(ctx).getString(KEY_ANIM_DIRECTION, "bottom") ?: "bottom"
    fun getAnimStyle(ctx: Context): String = prefs(ctx).getString(KEY_ANIM_STYLE, "spring") ?: "spring"
    fun getAnimDuration(ctx: Context): Int = prefs(ctx).getInt(KEY_ANIM_DURATION, 300)

    fun getTabHeightDp(ctx: Context): Int = prefs(ctx).getInt(KEY_TAB_HEIGHT_DP, 40)

    // ── 写入 ──

    fun setBgColor(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_BG_COLOR, v).apply()
    fun setBgAlpha(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_BG_ALPHA, v).apply()
    fun setBlurRadius(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_BLUR_RADIUS, v).apply()

    fun setMarginHorizontal(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_MARGIN_H, v).apply()
    fun setPosition(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_POSITION, v).apply()
    fun setBottomMargin(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_BOTTOM_MARGIN, v).apply()
    fun setRowSpacing(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_ROW_SPACING, v).apply()
    fun setColSpacing(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_COLUMN_SPACING, v).apply()

    fun setAnimDirection(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_ANIM_DIRECTION, v).apply()
    fun setAnimStyle(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_ANIM_STYLE, v).apply()
    fun setAnimDuration(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_ANIM_DURATION, v).apply()

    fun setTabHeightDp(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_TAB_HEIGHT_DP, v).apply()
}
