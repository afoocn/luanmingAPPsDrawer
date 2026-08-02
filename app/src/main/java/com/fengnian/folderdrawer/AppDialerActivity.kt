package com.fengnian.folderdrawer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fengnian.folderdrawer.databinding.ActivityAppDialerBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.AppSearchEntry
import com.fengnian.folderdrawer.util.AppUtils
import com.fengnian.folderdrawer.util.BackgroundHelper
import com.fengnian.folderdrawer.util.DialogSettings
import com.fengnian.folderdrawer.util.T9KeyboardHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APP Dialer 弹窗：T9 / QWERTY 拨号盘风格，按键后用拼音首字母或英文名实时匹配已安装 App。
 *
 * 复用 QuickLaunchDialogActivity 的全屏半透明窗口 + 原生毛玻璃 + 点外部关闭范式。
 */

/**
 * 进程内缓存：首次打开拨号盘时构建「已装应用 + 搜索键 + 图标包图标」列表，
 * 后续打开直接命中缓存秒显全部应用，后台异步刷新以捕获新装/卸载应用与图标包变化。
 */
private object DialerAppCache {
    @Volatile
    var entries: List<AppSearchEntry>? = null
}

class AppDialerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDialerBinding
    private lateinit var iconPackManager: IconPackManager
    private var inputBuffer = StringBuilder()
    private var allEntries: List<AppSearchEntry> = emptyList()
    private var isT9 = true
    private var hasPlayedEnterAnim = false
    /** 卡片实际底色是否为浅色：决定文字/按键配色，避免浅底白字看不见 */
    private var cardIsLight = false

    /** 锁屏（息屏）即退出拨号盘弹窗 */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_CollectionDrawer_Translucent)
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // 全屏半透明窗口（windowIsTranslucent=true）：卡片外全透明，点外面关闭由根布局监听处理
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }

        binding = ActivityAppDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        iconPackManager = IconPackManager.getInstance(this)

        isT9 = DialogSettings.getKeyboardMode(this) == "t9"

        // 输入行始终为空：不记忆按过的键，打开即空、立即可进行新的搜索匹配
        inputBuffer = StringBuilder()

        // 应用全局弹窗设置：水平边距 / 位置（与主弹窗一致，全程不碰 window）
        applyDialogLayout()
        // 输入栏高度（按拨号键搜索应用这一栏）可调
        applyInputHeight()
        // 卡片背景 + 毛玻璃：必须在 onCreate 就应用（与主弹窗一致），
        // 否则窗口已显示、动画播放中途才开模糊会造成一帧卡顿「弹出卡一下」
        applyCardBackground()

        // 锁屏即退出：监听息屏广播，避免亮屏后弹窗仍在
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        setupDismissListeners()
        loadApps()
        renderKeyboard()
    }

    // ===== 数据加载 =====
    private fun loadApps() {
        // 先命中缓存：秒显（不阻塞 UI），打开即可见、即可搜
        DialerAppCache.entries?.let { cached ->
            allEntries = cached
            renderInitialOrCurrent()
        }
        // 后台刷新（捕获新装/卸载应用、图标包变化），完成后更新缓存并重渲
        lifecycleScope.launch {
            val iconPm = iconPackManager
            val entries = withContext(Dispatchers.IO) {
                AppUtils.getInstalledApps(applicationContext).map { ia ->
                    val keys = T9KeyboardHelper.buildSearchKeys(ia.label)
                    // 套用全局激活的图标包（与抽屉图标主题一致）；无图标包时回退系统图标
                    val themedIcon = iconPm.getIcon(ia.packageName, ia.activityName, ia.label) ?: ia.icon
                    AppSearchEntry(
                        ia.packageName, ia.activityName, ia.label, themedIcon,
                        keys.first, keys.second, keys.third
                    )
                }
            }
            DialerAppCache.entries = entries
            allEntries = entries
            renderInitialOrCurrent()
        }
    }

    // ===== 输入处理 =====
    private fun onKey(c: Char) {
        inputBuffer.append(c)
        refresh()
    }

    private fun onBackspace() {
        if (inputBuffer.isNotEmpty()) {
            inputBuffer.deleteAt(inputBuffer.length - 1)
            refresh()
        }
    }

    private fun onClear() {
        inputBuffer.clear()
        refresh()
    }

    private fun refresh() {
        binding.inputDisplay.text = inputBuffer.toString()
        val matched = if (allEntries.isEmpty()) emptyList()
        else T9KeyboardHelper.match(allEntries, inputBuffer.toString(), isT9)
        renderResults(matched)
        // 持久化当前查询，供「记录上次结果」开关在下次打开时恢复
        DialogSettings.setDialerLastQuery(this, inputBuffer.toString())
    }

    /**
     * 初始/刷新渲染决策：
     * - 用户尚未输入（inputBuffer 为空）：输入行保持空；结果区按「记录上次结果」开关
     *   显示上次匹配的 App（开启时）或全部 App（关闭时，即全新搜索）。
     * - 用户已输入：按当前按键实时匹配（尊重用户输入，不被后台刷新覆盖）。
     */
    private fun renderInitialOrCurrent() {
        if (allEntries.isEmpty()) return
        if (inputBuffer.isEmpty()) {
            // 输入行始终为空：不显示上次按过的键
            binding.inputDisplay.text = ""
            val lastQuery = DialogSettings.getDialerLastQuery(this)
            val show = if (DialogSettings.isDialerRememberResult(this) && lastQuery.isNotEmpty())
                T9KeyboardHelper.match(allEntries, lastQuery, isT9) else allEntries
            renderResults(show)
        } else {
            refresh()
        }
    }

    // ===== 结果渲染 =====
    private fun renderResults(entries: List<AppSearchEntry>) {
        binding.resultContainer.removeAllViews()
        val density = resources.displayMetrics.density

        // 读取 Dialer 专属设置（横向单行：每格等宽 = (屏宽-2边距-卡片内边距)/每页个数，多余横向滑出）
        val marginPx = (DialogSettings.getDialerMarginHorizontal(this) * density).toInt()
        val cardInnerPad = (16 * density).toInt() // 卡片内 padding 8dp × 2
        val perPage = DialogSettings.getDialerPerPage(this).coerceAtLeast(1)
        val screenW = resources.displayMetrics.widthPixels
        val cellW = ((screenW - 2 * marginPx - cardInnerPad) / perPage)
            .coerceAtLeast((40 * density).toInt())
        val resultH = (DialogSettings.getDialerResultHeight(this) * density).toInt()
        val iconSizeRaw = (DialogSettings.getDialerIconSize(this) * density).toInt()
        val nameSize = DialogSettings.getDialerNameSize(this).toFloat()
        val nameColor = DialogSettings.getDialerNameColor(this)
        val showName = DialogSettings.isDialerShowName(this)
        val singleLine = DialogSettings.isDialerNameSingleLine(this)
        val colSpacing = (DialogSettings.getColSpacing(this) * density).toInt()

        // 结果区整体高度（固定，弹窗出来即占好高度，不随加载撑开）
        binding.resultScroll.layoutParams = binding.resultScroll.layoutParams.apply { height = resultH }

        if (entries.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (allEntries.isEmpty()) "正在加载应用…" else "没有匹配的 App"
                setTextColor(secondaryTextColor())
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            binding.resultContainer.addView(empty)
            return
        }

        // 图标尺寸受「格子宽」与「结果区高（减去名称）」双重约束，避免溢出
        val nameH = if (showName) (nameSize * density * 1.3f).toInt() + (4 * density).toInt() else 0
        val maxIconByHeight = resultH - nameH - (10 * density).toInt()
        val maxIconByWidth = cellW - (12 * density).toInt()
        val iconSize = iconSizeRaw.coerceIn(
            (20 * density).toInt(),
            maxOf((20 * density).toInt(), minOf(maxIconByHeight, maxIconByWidth))
        )

        entries.forEach { entry ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    (3 * density).toInt(), (4 * density).toInt(),
                    (3 * density).toInt(), (4 * density).toInt()
                )
                setOnClickListener { launchApp(entry) }
                layoutParams = LinearLayout.LayoutParams(cellW, resultH).apply {
                    marginStart = colSpacing / 2
                    marginEnd = colSpacing / 2
                }
            }
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(entry.icon)
            }
            item.addView(iconView)
            if (showName) {
                val nameView = TextView(this).apply {
                    text = entry.label
                    textSize = nameSize
                    setTextColor(if (nameColor != 0) nameColor else primaryTextColor())
                    gravity = Gravity.CENTER
                    maxLines = if (singleLine) 1 else 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, (2 * density).toInt(), 0, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                item.addView(nameView)
            }
            binding.resultContainer.addView(item)
        }
    }

    private fun launchApp(entry: AppSearchEntry) {
        AppUtils.launchApp(this, entry.packageName, entry.activityName)
        dismissWithAnimation()
    }

    // ===== 键盘渲染 =====
    private fun renderKeyboard() {
        binding.keyboardContainer.removeAllViews()
        val rows: List<List<KeySpec>> = if (isT9) {
            // T9：去掉 0 与独立清除键；删除键（短按删一个字符、长按全清）置于第一排第一个；
            // 三行紧凑，去掉多余高度
            listOf(
                listOf(
                    KeySpec("⌫", "长按清", ::onBackspace, longAction = ::onClear),
                    KeySpec("2", "ABC", { onKey('2') }),
                    KeySpec("3", "DEF", { onKey('3') })
                ),
                listOf(
                    KeySpec("4", "GHI", { onKey('4') }),
                    KeySpec("5", "JKL", { onKey('5') }),
                    KeySpec("6", "MNO", { onKey('6') })
                ),
                listOf(
                    KeySpec("7", "PQRS", { onKey('7') }),
                    KeySpec("8", "TUV", { onKey('8') }),
                    KeySpec("9", "WXYZ", { onKey('9') })
                )
            )
        } else {
            listOf(
                listOf(
                    KeySpec("Q", null, { onKey('q') }), KeySpec("W", null, { onKey('w') }),
                    KeySpec("E", null, { onKey('e') }), KeySpec("R", null, { onKey('r') }),
                    KeySpec("T", null, { onKey('t') }), KeySpec("Y", null, { onKey('y') }),
                    KeySpec("U", null, { onKey('u') }), KeySpec("I", null, { onKey('i') }),
                    KeySpec("O", null, { onKey('o') }), KeySpec("P", null, { onKey('p') })
                ),
                listOf(
                    KeySpec("A", null, { onKey('a') }), KeySpec("S", null, { onKey('s') }),
                    KeySpec("D", null, { onKey('d') }), KeySpec("F", null, { onKey('f') }),
                    KeySpec("G", null, { onKey('g') }), KeySpec("H", null, { onKey('h') }),
                    KeySpec("J", null, { onKey('j') }), KeySpec("K", null, { onKey('k') }),
                    KeySpec("L", null, { onKey('l') })
                ),
                listOf(
                    KeySpec("Z", null, { onKey('z') }), KeySpec("X", null, { onKey('x') }),
                    KeySpec("C", null, { onKey('c') }), KeySpec("V", null, { onKey('v') }),
                    KeySpec("B", null, { onKey('b') }), KeySpec("N", null, { onKey('n') }),
                    KeySpec("M", null, { onKey('m') }),
                    KeySpec("⌫", "长按清", ::onBackspace, longAction = ::onClear)
                )
            )
        }
        rows.forEach { rowSpecs ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            rowSpecs.forEach { spec ->
                row.addView(makeKeyButton(spec.main, spec.sub, spec.action, spec.longAction, spec.spacer))
            }
            binding.keyboardContainer.addView(row)
        }
    }

    private data class KeySpec(
        val main: String,
        val sub: String?,
        val action: () -> Unit,
        val longAction: (() -> Unit)? = null,
        val spacer: Boolean = false
    )

    private fun makeKeyButton(
        main: String,
        sub: String?,
        onClick: () -> Unit,
        longAction: (() -> Unit)? = null,
        spacer: Boolean = false
    ): LinearLayout {
        val density = resources.displayMetrics.density
        // 按键尺寸：高度固定可调；宽度 0=自适应填满（weight=1），>0=固定宽度 dp
        val keyHeight = (DialogSettings.getDialerKeyHeight(this) * density).toInt()
            .coerceAtLeast((32 * density).toInt())
        val keyWidth = DialogSettings.getDialerKeyWidth(this)
        val keyWidthPx = if (keyWidth > 0) (keyWidth * density).toInt() else 0
        val colSpacing = DialogSettings.getDialerKeyColSpacing(this)
        val rowSpacing = DialogSettings.getDialerKeyRowSpacing(this)
        val hMargin = colSpacing / 2

        fun keyLayoutParams(): LinearLayout.LayoutParams {
            val p = if (keyWidthPx > 0)
                LinearLayout.LayoutParams(keyWidthPx, keyHeight)
            else
                LinearLayout.LayoutParams(0, keyHeight, 1f)
            p.setMargins(hMargin, 0, hMargin, rowSpacing)
            return p
        }

        if (spacer) {
            // 占位空格：保持网格平衡（与按键等宽等高、无背景无点击）
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = keyLayoutParams()
            }
        }
        // 卡片感知背景：遮罩基色取与卡片底色相反的色（浅底用黑、深底用白），
        // 配色跟随卡片真实底色，避免「浅底白字/深底黑字」错配看不清。
        val scrimBase = if (cardIsLight) Color.BLACK else Color.WHITE
        val scrimAlpha = if (cardIsLight) 0.06f else 0.12f
        val strokeAlpha = if (cardIsLight) 0.20f else 0.16f
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                (3 * density).toInt(), (6 * density).toInt(),
                (3 * density).toInt(), (6 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (10 * density)
                setColor(adjustAlpha(scrimBase, scrimAlpha))
                setStroke((1 * density).toInt(), adjustAlpha(scrimBase, strokeAlpha))
            }
            setOnClickListener { onClick() }
            if (longAction != null) {
                isLongClickable = true
                setOnLongClickListener {
                    longAction.invoke()
                    true
                }
            }
            layoutParams = keyLayoutParams()
        }
        val mainTv = TextView(this).apply {
            text = main
            textSize = if (sub != null) 17f else 19f
            setTextColor(primaryTextColor())
            gravity = Gravity.CENTER
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        container.addView(mainTv)
        if (sub != null) {
            val subTv = TextView(this).apply {
                text = sub
                textSize = 8f
                setTextColor(secondaryTextColor())
                gravity = Gravity.CENTER
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                setPadding(0, (1 * density).toInt(), 0, 0)
            }
            container.addView(subTv)
        }
        return container
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = ((Color.alpha(color) * factor).toInt()).coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    /** 输入栏（搜索行）文字/提示色：跟随卡片底色对比度，浅底用深色字 */
    private fun applyTextContrast() {
        binding.inputDisplay.setTextColor(primaryTextColor())
        binding.inputDisplay.setHintTextColor(secondaryTextColor())
    }

    /** 主键文字色（应用名、按键主字符） */
    private fun primaryTextColor(): Int =
        if (cardIsLight) Color.parseColor("#1A1C1E") else Color.WHITE

    /** 次级文字色（提示、按键字母、空状态） */
    private fun secondaryTextColor(): Int =
        if (cardIsLight) Color.parseColor("#5F6368") else Color.parseColor("#C4C7CF")

    /** 估算颜色是否「浅色」（用于决定对比文字） */
    private fun isColorLight(color: Int): Boolean {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
        return lum > 0.6f
    }

    /** 应用全局水平边距 + 弹窗位置（居中/底部，与主弹窗一致，全程不碰 window） */
    private fun applyDialogLayout() {
        val density = resources.displayMetrics.density
        val marginPx = (DialogSettings.getDialerMarginHorizontal(this) * density).toInt()
        val params = binding.contentCard.layoutParams as FrameLayout.LayoutParams
        params.marginStart = marginPx
        params.marginEnd = marginPx

        val position = DialogSettings.getPosition(this)
        if (position == "bottom") {
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // 贴底时额外留出导航栏高度，避免被系统导航条遮挡
            params.bottomMargin = (DialogSettings.getBottomMargin(this) * density).toInt() + getNavigationBarHeight()
        } else {
            params.gravity = Gravity.CENTER
            params.bottomMargin = 0
        }
        binding.contentCard.layoutParams = params
    }

    /** 输入栏（按拨号键搜索应用这一栏）高度，单独可调，像标签页高度那样 */
    private fun applyInputHeight() {
        val density = resources.displayMetrics.density
        val h = (DialogSettings.getDialerInputHeight(this) * density).toInt()
        val lp = binding.inputDisplay.layoutParams
        lp.height = h
        binding.inputDisplay.layoutParams = lp
    }

    /** 读取系统导航栏高度（无导航栏时返回 0） */
    private fun getNavigationBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    // ===== 关闭 / 动画 / 毛玻璃（范式同 QuickLaunchDialogActivity）=====

    private fun setupDismissListeners() {
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                val cardLoc = IntArray(2)
                binding.contentCard.getLocationOnScreen(cardLoc)
                val cardRight = cardLoc[0] + binding.contentCard.width
                val cardBottom = cardLoc[1] + binding.contentCard.height
                if (x < cardLoc[0] || x > cardRight || y < cardLoc[1] || y > cardBottom) {
                    dismissWithAnimation()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        dismissWithAnimation()
    }

    private fun dismissWithAnimation() {
        binding.root.setOnTouchListener(null)
        val duration = (DialogSettings.getAnimDuration(this) * 0.6f).toLong()
        binding.contentCard.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .translationY(40f * resources.displayMetrics.density)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator(2f))
            .withEndAction { finish() }
            .start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !hasPlayedEnterAnim) {
            hasPlayedEnterAnim = true
            playEnterAnimation()
        }
    }

    private fun playEnterAnimation() {
        val card = binding.contentCard
        val duration = DialogSettings.getAnimDuration(this).toLong()
        val direction = DialogSettings.getAnimDirection(this)
        val style = DialogSettings.getAnimStyle(this)
        val density = resources.displayMetrics.density

        card.post {
            card.alpha = 0f
            card.scaleX = 1f
            card.scaleY = 1f
            card.translationX = 0f
            card.translationY = 0f

            when (style) {
                "scale" -> {
                    card.scaleX = 0.85f
                    card.scaleY = 0.85f
                    card.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                }
                "slide" -> {
                    when (direction) {
                        "bottom" -> card.translationY = 120f * density
                        "left" -> card.translationX = -400f * density
                        "right" -> card.translationX = 400f * density
                        "center" -> {
                            card.animate()
                                .alpha(1f)
                                .setDuration(duration)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                            return@post
                        }
                    }
                    card.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(duration)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                }
                "spring" -> {
                    when (direction) {
                        "bottom" -> card.translationY = 120f * density
                        "left" -> card.translationX = -400f * density
                        "right" -> card.translationX = 400f * density
                        "center" -> {
                            card.scaleX = 0.85f
                            card.scaleY = 0.85f
                            card.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(duration)
                                .setInterpolator(OvershootInterpolator(2f))
                                .start()
                            return@post
                        }
                    }
                    card.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(duration)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                }
                else -> {
                    card.translationY = 120f * density
                    card.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(duration)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                }
            }
        }
    }

    private fun applyCardBackground() {
        val bgColor = DialogSettings.getBgColor(this)
        val bgAlpha = DialogSettings.getBgAlpha(this)
        val blurRadius = DialogSettings.getBlurRadius(this)
        val hasColor = bgColor != 0
        val baseColor = if (hasColor) bgColor else ContextCompat.getColor(this, R.color.surface)
        // 根据卡片实际底色决定文字/按键配色，避免「浅底白字」看不见
        cardIsLight = isColorLight(baseColor)
        applyTextContrast()
        binding.contentCard.elevation = 0f
        if (blurRadius > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val px = (blurRadius * 4f).toInt().coerceAtLeast(1)
            window?.setBackgroundBlurRadius(px)
            val cardAlpha = bgAlpha.coerceIn(30, 200)
            binding.contentCard.setCardBackgroundColor(
                BackgroundHelper.applyAlphaToColor(baseColor, cardAlpha)
            )
            binding.root.setBackgroundColor(Color.TRANSPARENT)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window?.setBackgroundBlurRadius(0)
            binding.contentCard.setCardBackgroundColor(
                BackgroundHelper.applyAlphaToColor(baseColor, bgAlpha)
            )
            binding.root.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }
}
