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

    // ── APP Dialer（拨号盘快速启动）──
    const val KEY_DIALER_ENABLED = "dialer_enabled"     // Boolean, 默认 true（主界面卡片与功能总开关）
    const val KEY_KEYBOARD_MODE = "keyboard_mode"     // "t9" / "qwerty", 默认 "t9"
    const val KEY_DIALER_REMEMBER_RESULT = "dialer_remember_result"  // Boolean, 默认 true（打开时恢复上次搜索结果）
    const val KEY_DIALER_LAST_QUERY = "dialer_last_query"   // String, 默认 ""（上次搜索词，供记录上次结果使用）
    const val KEY_DIALER_INPUT_HEIGHT = "dialer_input_height"     // dp, 默认 44（输入栏高度）
    const val KEY_DIALER_RESULT_HEIGHT = "dialer_result_height"  // dp, 默认 64（结果区高度）
    const val KEY_DIALER_PER_PAGE = "dialer_per_page"          // 每页应用数, 默认 5
    const val KEY_DIALER_ICON_SIZE = "dialer_icon_size"        // dp, 默认 42（图标大小）
    const val KEY_DIALER_NAME_SIZE = "dialer_name_size"        // sp, 默认 11（名称字号）
    const val KEY_DIALER_NAME_COLOR = "dialer_name_color"      // Int 0=自动, 默认 0（名称颜色）
    const val KEY_DIALER_SHOW_NAME = "dialer_show_name"        // Boolean, 默认 true（显示名称）
    const val KEY_DIALER_NAME_SINGLE_LINE = "dialer_name_single_line" // Boolean, 默认 false（名称单行）
    const val KEY_DIALER_SHORTCUT_ICON = "dialer_shortcut_icon"  // String, 默认 ""（自定义桌面快捷方式图标 drawable 名；空=默认键盘图标）
    const val KEY_DIALER_CACHE_ENABLED = "dialer_cache_enabled"   // Boolean, 默认 true（APP Dialer 已装应用持久化缓存总开关）

    // ── APP Dialer 独立于全局弹窗的尺寸配置 ──
    const val KEY_DIALER_MARGIN_H = "dialer_margin_horizontal"   // dp, 默认 28（弹窗水平边距，独立于全局）
    const val KEY_DIALER_KEY_HEIGHT = "dialer_key_height"        // dp, 默认 48（按键高度）
    const val KEY_DIALER_KEY_WIDTH = "dialer_key_width"          // dp, 默认 0（0=自适应填满；>0 为固定宽度）
    const val KEY_DIALER_KEY_ROW_SPACING = "dialer_key_row_spacing" // dp, 默认 5（按键行间距）
    const val KEY_DIALER_KEY_COL_SPACING = "dialer_key_col_spacing" // dp, 默认 8（按键列间距）

    // ── 崩溃报告 ──
    const val KEY_CRASH_REPORT = "crash_report"       // Boolean, 默认 true

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

    // ── APP Dialer ──

    /** 总开关：是否启用 APP Dialer（主界面卡片与功能） */
    fun isDialerEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DIALER_ENABLED, true)
    fun setDialerEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DIALER_ENABLED, v).apply()

    /** 自定义桌面快捷方式图标（图标包 drawable 名）；空串=默认键盘图标 */
    fun getDialerShortcutIconName(ctx: Context): String = prefs(ctx).getString(KEY_DIALER_SHORTCUT_ICON, "") ?: ""
    fun setDialerShortcutIconName(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_DIALER_SHORTCUT_ICON, v).apply()

    /** APP Dialer 已装应用持久化缓存总开关：开启后把图标+名称+拼音落盘，进程被杀后下次打开秒显 */
    fun isDialerCacheEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DIALER_CACHE_ENABLED, true)
    fun setDialerCacheEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DIALER_CACHE_ENABLED, v).apply()

    /** 键盘模式：返回 "t9" 或 "qwerty" */
    fun getKeyboardMode(ctx: Context): String {
        val v = prefs(ctx).getString(KEY_KEYBOARD_MODE, "t9") ?: "t9"
        return if (v == "qwerty") "qwerty" else "t9"
    }
    fun setKeyboardMode(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_KEYBOARD_MODE, if (v == "qwerty") "qwerty" else "t9").apply()

    /** 是否记录上次结果：打开拨号盘时恢复上次搜索的 App（关闭则每次全新搜索、全量展示） */
    fun isDialerRememberResult(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DIALER_REMEMBER_RESULT, true)
    fun setDialerRememberResult(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DIALER_REMEMBER_RESULT, v).apply()

    /** 上次拨号盘搜索词（供「记录上次结果」开关恢复使用） */
    fun getDialerLastQuery(ctx: Context): String = prefs(ctx).getString(KEY_DIALER_LAST_QUERY, "") ?: ""
    fun setDialerLastQuery(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_DIALER_LAST_QUERY, v).apply()

    // 输入栏高度 / 结果区高度 / 每页应用数 / 图标大小 / 名称字号 / 名称颜色 / 显示名称 / 名称单行
    fun getDialerInputHeight(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_INPUT_HEIGHT, 44)
    fun setDialerInputHeight(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_INPUT_HEIGHT, v).apply()

    fun getDialerResultHeight(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_RESULT_HEIGHT, 64)
    fun setDialerResultHeight(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_RESULT_HEIGHT, v).apply()

    fun getDialerPerPage(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_PER_PAGE, 5)
    fun setDialerPerPage(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_PER_PAGE, v).apply()

    fun getDialerIconSize(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_ICON_SIZE, 42)
    fun setDialerIconSize(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_ICON_SIZE, v).apply()

    fun getDialerNameSize(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_NAME_SIZE, 11)
    fun setDialerNameSize(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_NAME_SIZE, v).apply()

    fun getDialerNameColor(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_NAME_COLOR, 0)
    fun setDialerNameColor(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_NAME_COLOR, v).apply()

    fun isDialerShowName(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DIALER_SHOW_NAME, true)
    fun setDialerShowName(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_DIALER_SHOW_NAME, v).apply()

    fun isDialerNameSingleLine(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DIALER_NAME_SINGLE_LINE, false)
    fun setDialerNameSingleLine(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_DIALER_NAME_SINGLE_LINE, v).apply()

    // —— 拨号盘独立尺寸 ——
    fun getDialerMarginHorizontal(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_MARGIN_H, 28)
    fun setDialerMarginHorizontal(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_MARGIN_H, v).apply()

    fun getDialerKeyHeight(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_KEY_HEIGHT, 48)
    fun setDialerKeyHeight(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_KEY_HEIGHT, v).apply()

    fun getDialerKeyWidth(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_KEY_WIDTH, 0)
    fun setDialerKeyWidth(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_KEY_WIDTH, v).apply()

    fun getDialerKeyRowSpacing(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_KEY_ROW_SPACING, 5)
    fun setDialerKeyRowSpacing(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_KEY_ROW_SPACING, v).apply()

    fun getDialerKeyColSpacing(ctx: Context): Int = prefs(ctx).getInt(KEY_DIALER_KEY_COL_SPACING, 8)
    fun setDialerKeyColSpacing(ctx: Context, v: Int) = prefs(ctx).edit().putInt(KEY_DIALER_KEY_COL_SPACING, v).apply()

    // ── 崩溃报告 ──

    fun isCrashReportEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CRASH_REPORT, true)
    fun setCrashReportEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_CRASH_REPORT, v).apply()

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
