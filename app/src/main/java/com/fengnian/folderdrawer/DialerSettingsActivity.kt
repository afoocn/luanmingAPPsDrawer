package com.fengnian.folderdrawer

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.ColorPickerDialog
import com.fengnian.folderdrawer.util.DialogSettings
import com.fengnian.folderdrawer.util.DialerCacheStore
import com.fengnian.folderdrawer.util.ShortcutHelper
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APP Dialer 专属设置页（从主界面拨号盘卡片的铅笔按钮进入）。
 * 包含全部拨号盘设置 + 桌面快捷方式图标自定义（支持图标包）+ 创建快捷方式。
 */
class DialerSettingsActivity : AppCompatActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private val rootLayout: LinearLayout by lazy { findViewById(R.id.rootLayout) }
    private val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }

    private val colorPrimary by lazy { getColor(R.color.primary) }
    private val colorOnSurface by lazy { getColor(R.color.on_surface) }
    private val colorOnSurfaceVariant by lazy { getColor(R.color.on_surface_variant) }
    private val colorOutline by lazy { getColor(R.color.outline) }
    private val colorSurface by lazy { getColor(R.color.surface) }

    private lateinit var iconPackManager: IconPackManager
    private var iconPreview: ImageView? = null
    private var iconLabel: TextView? = null

    /** 图标尺寸变更后去抖重建缓存，避免拖动 seekbar 时频繁全量扫描 */
    private val rebuildHandler = Handler(Looper.getMainLooper())
    private val rebuildTask = Runnable {
        if (DialogSettings.isDialerCacheEnabled(this@DialerSettingsActivity)) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    com.fengnian.folderdrawer.util.DialerCacheStore.rebuildAndSave(this@DialerSettingsActivity)
                } catch (_: Exception) {
                }
            }
        }
    }

    private val iconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val name = result.data?.getStringExtra(IconPickerActivity.EXTRA_ICON_DRAWABLE_NAME)
            ?: return@registerForActivityResult
        DialogSettings.setDialerShortcutIconName(this, name)
        updateDialerIconPreview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer_settings)

        setSupportActionBar(toolbar)
        toolbar.navigationIcon = ContextCompat
            .getDrawable(this, R.drawable.ic_arrow_back)?.mutate()?.apply {
                setTint(getColor(R.color.on_background))
            }
        toolbar.setNavigationOnClickListener { finish() }

        iconPackManager = IconPackManager.getInstance(this)
        iconPackManager.refreshAvailablePacks()

        buildDialerSection()
    }

    private fun Int.dp2px(): Int = (this * density + 0.5f).toInt()

    // ==================== 各设置区块 ====================

    private fun buildDialerSection() {
        buildCacheSection()
        val card = addSectionCard()

        // 键盘模式
        addRadioRow(card, "键盘模式",
            listOf("t9" to "T9", "qwerty" to "QWERTY"),
            DialogSettings.getKeyboardMode(this)
        ) { DialogSettings.setKeyboardMode(this, it) }

        // 桌面快捷方式图标（自定义 + 图标包）
        addDialerIconRow(card)

        // 图标主题（图标包）入口
        val packRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.dp2px(), 0, 4.dp2px())
        }
        packRow.addView(makeTextButton("图标主题（图标包）") {
            startActivity(Intent(this, IconPackListActivity::class.java))
        })
        card.addView(packRow)

        // 创建 Dialer 桌面快捷方式
        val pinRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.dp2px(), 0, 4.dp2px())
        }
        pinRow.addView(makeTextButton("创建 Dialer 快捷方式") { pinDialerShortcut() })
        card.addView(pinRow)

        // 记录上次结果
        addSwitchRow(card, "记录上次结果", DialogSettings.isDialerRememberResult(this)) {
            DialogSettings.setDialerRememberResult(this, it)
        }

        // —— 外观与结果区 ——
        addSeekBarRow(card, "输入栏高度", 16, 80, DialogSettings.getDialerInputHeight(this), "dp") { DialogSettings.setDialerInputHeight(this, it) }
        addSeekBarRow(card, "结果区高度", 48, 200, DialogSettings.getDialerResultHeight(this), "dp") { DialogSettings.setDialerResultHeight(this, it) }
        addSeekBarRow(card, "每页应用数", 3, 12, DialogSettings.getDialerPerPage(this), "") { DialogSettings.setDialerPerPage(this, it) }
        addSeekBarRow(card, "图标大小", 24, 96, DialogSettings.getDialerIconSize(this), "dp") {
            DialogSettings.setDialerIconSize(this, it)
            // 图标展示尺寸变化后，缓存的图标按旧尺寸生成会显得不完整，去抖后自动重建缓存
            scheduleCacheRebuild()
        }
        addSeekBarRow(card, "名称字号", 8, 22, DialogSettings.getDialerNameSize(this), "sp") { DialogSettings.setDialerNameSize(this, it) }
        addColorButton(card, "名称颜色", DialogSettings.getDialerNameColor(this)) { DialogSettings.setDialerNameColor(this, it) }
        addSwitchRow(card, "显示名称", DialogSettings.isDialerShowName(this)) { DialogSettings.setDialerShowName(this, it) }
        addSwitchRow(card, "名称单行显示", DialogSettings.isDialerNameSingleLine(this)) { DialogSettings.setDialerNameSingleLine(this, it) }

        // —— 弹窗与键盘尺寸（独立于全局弹窗配置）——
        addSeekBarRow(card, "弹窗水平边距", 5, 64, DialogSettings.getDialerMarginHorizontal(this), "dp") { DialogSettings.setDialerMarginHorizontal(this, it) }
        addSeekBarRow(card, "按键高度", 32, 72, DialogSettings.getDialerKeyHeight(this), "dp") { DialogSettings.setDialerKeyHeight(this, it) }
        addSeekBarRow(card, "按键宽度(0自适应)", 0, 96, DialogSettings.getDialerKeyWidth(this), "dp") { DialogSettings.setDialerKeyWidth(this, it) }
        addSeekBarRow(card, "按键行间距", 0, 24, DialogSettings.getDialerKeyRowSpacing(this), "dp") { DialogSettings.setDialerKeyRowSpacing(this, it) }
        addSeekBarRow(card, "按键列间距", 0, 24, DialogSettings.getDialerKeyColSpacing(this), "dp") { DialogSettings.setDialerKeyColSpacing(this, it) }
    }

    // ==================== 缓存管理 ====================

    private fun buildCacheSection() {
        val card = addSectionCard()
        addSwitchRow(card, "启用 APP Dialer 缓存", DialogSettings.isDialerCacheEnabled(this)) { enabled ->
            DialogSettings.setDialerCacheEnabled(this, enabled)
            // 关闭开关时一并清掉磁盘缓存，避免留存过时数据
            if (!enabled) {
                lifecycleScope.launch(Dispatchers.IO) { DialerCacheStore.clear(this@DialerSettingsActivity) }
            }
        }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4.dp2px(), 0, 4.dp2px())
        }
        btnRow.addView(makeTextButton("更新缓存") { updateCache() })
        btnRow.addView(makeTextButton("清除缓存") { confirmClearCache() })
        card.addView(btnRow)
    }

    private fun updateCache() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { DialerCacheStore.rebuildAndSave(this@DialerSettingsActivity) }
            Toast.makeText(this@DialerSettingsActivity, "缓存已更新", Toast.LENGTH_SHORT).show()
        }
    }

    /** 图标尺寸/外观变化后去抖重建缓存（拖 seekbar 时多次回调只重建一次） */
    private fun scheduleCacheRebuild() {
        rebuildHandler.removeCallbacks(rebuildTask)
        rebuildHandler.postDelayed(rebuildTask, 800L)
    }

    private fun confirmClearCache() {
        AlertDialog.Builder(this)
            .setTitle("清除缓存")
            .setMessage("将删除 APP Dialer 已缓存的应用图标与名称，下次打开会重新读取（可能稍慢）。确定清除？")
            .setPositiveButton("清除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { DialerCacheStore.clear(this@DialerSettingsActivity) }
                    Toast.makeText(this@DialerSettingsActivity, "缓存已清除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 快捷方式图标 ====================

    private fun addDialerIconRow(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp2px(), 0, 10.dp2px())
        }
        val preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dp2px(), 48.dp2px()).apply { marginEnd = 12.dp2px() }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10.dp2px().toFloat()
                setStroke(1.dp2px(), colorOutline)
            }
        }
        iconPreview = preview
        row.addView(preview)

        val label = TextView(this).apply {
            textSize = 13f
            setTextColor(colorOnSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        iconLabel = label
        row.addView(label)

        val pickBtn = makeTextButton("选择图标") { launchIconPicker() }
        pickBtn.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 8.dp2px() }
        row.addView(pickBtn)

        val resetBtn = makeTextButton("恢复默认") {
            DialogSettings.setDialerShortcutIconName(this, "")
            updateDialerIconPreview()
        }
        row.addView(resetBtn)

        parent.addView(row)
        updateDialerIconPreview()
    }

    private fun launchIconPicker() {
        // 从当前激活的图标包选择图标（与抽屉快捷方式图标机制一致）
        iconPickerLauncher.launch(Intent(this, IconPickerActivity::class.java))
    }

    private fun updateDialerIconPreview() {
        val preview = iconPreview ?: return
        val label = iconLabel ?: return
        val name = DialogSettings.getDialerShortcutIconName(this)
        if (name.isBlank()) {
            preview.setImageDrawable(null)
            preview.setBackgroundColor(colorPrimary)
            label.text = "默认键盘图标"
        } else {
            preview.setBackgroundColor(Color.TRANSPARENT)
            label.text = name
            lifecycleScope.launch {
                val d = withContext(Dispatchers.IO) { iconPackManager.getDrawableByName(name) }
                preview.setImageDrawable(d)
            }
        }
    }

    private fun pinDialerShortcut() {
        if (!ShortcutHelper.isRequestPinShortcutSupported(this)) {
            Toast.makeText(this, "当前启动器不支持添加快捷方式", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = ShortcutHelper.pinDialerToHome(this)
        Toast.makeText(this, if (ok) "请在桌面上确认添加" else "创建失败", Toast.LENGTH_SHORT).show()
    }

    // ==================== UI 辅助方法（与全局设置页一致） ====================

    private fun addSectionCard(): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = 20.dp2px().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp2px()
            strokeColor = colorOutline
            setCardBackgroundColor(colorSurface)
            setContentPadding(16.dp2px(), 12.dp2px(), 16.dp2px(), 14.dp2px())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp2px() }
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(inner)
        rootLayout.addView(card)
        return inner
    }

    private fun addSeekBarRow(
        parent: LinearLayout,
        label: String,
        min: Int,
        max: Int,
        current: Int,
        suffix: String = "",
        onChanged: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6.dp2px(), 0, 4.dp2px())
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val labelTv = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(labelTv)

        val valueTv = TextView(this).apply {
            text = "$current$suffix"
            textSize = 13f
            setTextColor(colorOnSurfaceVariant)
            gravity = Gravity.END
        }
        topRow.addView(valueTv)
        row.addView(topRow)

        val seekBar = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setPadding(0, 4.dp2px(), 0, 0)
            thumbTintList = android.content.res.ColorStateList.valueOf(colorPrimary)
            progressTintList = android.content.res.ColorStateList.valueOf(colorPrimary)
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = progress + min
                valueTv.text = "$v$suffix"
                if (fromUser) onChanged(v)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        row.addView(seekBar)
        parent.addView(row)
    }

    private fun addColorButton(
        parent: LinearLayout,
        label: String,
        currentColor: Int,
        onPicked: (Int) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp2px(), 0, 8.dp2px())
        }
        val labelTv = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelTv)

        val swatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(28.dp2px(), 28.dp2px()).apply { marginEnd = 10.dp2px() }
            val border = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(2.dp2px(), colorOutline)
                setColor(if (currentColor != 0) currentColor else Color.TRANSPARENT)
            }
            background = border
        }
        row.addView(swatch)

        val btn = TextView(this).apply {
            text = if (currentColor != 0) String.format("#%08X", currentColor) else "自动"
            textSize = 13f
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            setPadding(14.dp2px(), 8.dp2px(), 14.dp2px(), 8.dp2px())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8.dp2px().toFloat()
                setStroke(1.dp2px(), colorOutline)
                setColor(Color.TRANSPARENT)
            }
        }
        btn.setOnClickListener {
            ColorPickerDialog.show(this, if (currentColor != 0) currentColor else 0xFFFFFFFF.toInt()) { picked ->
                onPicked(picked)
                val border = swatch.background as? GradientDrawable
                border?.setColor(if (picked != 0) picked else Color.TRANSPARENT)
                btn.text = if (picked != 0) String.format("#%08X", picked) else "自动"
            }
        }
        row.addView(btn)
        parent.addView(row)
    }

    private fun addRadioRow(
        parent: LinearLayout,
        label: String,
        options: List<Pair<String, String>>,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val labelTv = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(colorOnSurface)
            setPadding(0, 8.dp2px(), 0, 4.dp2px())
        }
        parent.addView(labelTv)

        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        for ((value, display) in options) {
            val isSelected = value == currentValue
            val chip = TextView(this).apply {
                text = display
                textSize = 13f
                gravity = Gravity.CENTER
                tag = value
                setPadding(18.dp2px(), 8.dp2px(), 18.dp2px(), 8.dp2px())
                setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextColor(if (isSelected) colorPrimary else colorOnSurfaceVariant)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10.dp2px().toFloat()
                    setStroke(1.dp2px(), if (isSelected) colorPrimary else colorOutline)
                    setColor(if (isSelected) adjustAlpha(colorPrimary, 0.12f) else Color.TRANSPARENT)
                }
                setOnClickListener {
                    onSelected(value)
                    for (i in 0 until chipsRow.childCount) {
                        val child = chipsRow.getChildAt(i) as? TextView ?: continue
                        val sel = child.tag == value
                        child.setTypeface(null, if (sel) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                        child.setTextColor(if (sel) colorPrimary else colorOnSurfaceVariant)
                        (child.background as? GradientDrawable)?.apply {
                            setStroke(1.dp2px(), if (sel) colorPrimary else colorOutline)
                            setColor(if (sel) adjustAlpha(colorPrimary, 0.12f) else Color.TRANSPARENT)
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 10.dp2px() }
            }
            chipsRow.addView(chip)
        }
        parent.addView(chipsRow)
    }

    private fun addSwitchRow(parent: LinearLayout, label: String, current: Boolean, onChanged: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dp2px(), 0, 10.dp2px())
        }
        val labelTv = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelTv)
        val switch = SwitchCompat(this).apply {
            isChecked = current
            thumbTintList = android.content.res.ColorStateList.valueOf(colorPrimary)
            trackTintList = android.content.res.ColorStateList.valueOf(adjustAlpha(colorPrimary, 0.4f))
            setOnCheckedChangeListener { _, checked -> onChanged(checked) }
        }
        row.addView(switch)
        parent.addView(row)
    }

    private fun makeTextButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            setPadding(20.dp2px(), 10.dp2px(), 20.dp2px(), 10.dp2px())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10.dp2px().toFloat()
                setStroke(1.dp2px(), colorOutline)
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = ((Color.alpha(color) * factor).toInt()).coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
