package com.fengnian.folderdrawer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.fengnian.folderdrawer.data.BackupData
import com.fengnian.folderdrawer.util.ColorPickerDialog
import com.fengnian.folderdrawer.util.DialogSettings
import com.fengnian.folderdrawer.viewmodel.CollectionViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class GlobalDialogSettingsActivity : AppCompatActivity() {

    private val density by lazy { resources.displayMetrics.density }
    private val rootLayout: LinearLayout by lazy { findViewById(R.id.rootLayout) }
    private val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    private val viewModel: CollectionViewModel by viewModels()

    // 颜色（遵循系统主题，支持浅色/深色自动切换）
    private val colorPrimary by lazy { getColor(R.color.primary) }
    private val colorOnSurface by lazy { getColor(R.color.on_surface) }
    private val colorOnSurfaceVariant by lazy { getColor(R.color.on_surface_variant) }
    private val colorOutline by lazy { getColor(R.color.outline) }
    private val colorSurface by lazy { getColor(R.color.surface) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_dialog_settings)

        setSupportActionBar(toolbar)
        // 显式给返回箭头着色，确保暗色模式下清晰可见（appcompat Toolbar 不认 navigationIconTint）
        toolbar.navigationIcon = androidx.core.content.ContextCompat
            .getDrawable(this, R.drawable.ic_arrow_back)?.mutate()?.apply {
                setTint(getColor(R.color.on_background))
            }
        toolbar.setNavigationOnClickListener { finish() }

        buildBackgroundSection()
        buildStyleSection()
        buildAnimSection()
        buildOtherSection()
    }

    private fun Int.dp2px(): Int = (this * density + 0.5f).toInt()

    // ==================== 工具方法 ====================

    /** 分区标签（对齐编辑页 style="@style/TextAppearance.Material3.LabelLarge"） */
    private fun addSectionLabel(title: String) {
        rootLayout.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(colorOnSurfaceVariant)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
            setPadding(4.dp2px(), 24.dp2px(), 0, 8.dp2px())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
    }

    /** 白底卡片容器（对齐编辑页 MaterialCardView 样式） */
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

        // 色块
        val swatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(28.dp2px(), 28.dp2px()).apply { marginEnd = 10.dp2px() }
            val border = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
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
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 8.dp2px().toFloat()
                setStroke(1.dp2px(), colorOutline)
                setColor(Color.TRANSPARENT)
            }
        }
        btn.setOnClickListener {
            ColorPickerDialog.show(this, if (currentColor != 0) currentColor else 0xFFFFFFFF.toInt()) { picked ->
                onPicked(picked)
                val border = swatch.background as? android.graphics.drawable.GradientDrawable
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
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
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
                        (child.background as? android.graphics.drawable.GradientDrawable)?.apply {
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

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = ((Color.alpha(color) * factor).toInt()).coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    // ==================== 各设置区块 ====================

    private fun buildBackgroundSection() {
        addSectionLabel("弹窗背景")
        val card = addSectionCard()
        addColorButton(card, "背景颜色", DialogSettings.getBgColor(this)) { DialogSettings.setBgColor(this, it) }
        addSeekBarRow(card, "背景透明度", 0, 255, DialogSettings.getBgAlpha(this)) { DialogSettings.setBgAlpha(this, it) }
        addSeekBarRow(card, "模糊度", 0, 25, DialogSettings.getBlurRadius(this)) { DialogSettings.setBlurRadius(this, it) }
    }

    private fun buildStyleSection() {
        addSectionLabel("弹窗样式")
        val card = addSectionCard()
        addSeekBarRow(card, "水平边距", 5, 64, DialogSettings.getMarginHorizontal(this), "dp") { DialogSettings.setMarginHorizontal(this, it) }
        addRadioRow(card, "弹窗位置",
            listOf("center" to "居中", "bottom" to "底部"),
            DialogSettings.getPosition(this)
        ) { DialogSettings.setPosition(this, it) }
        addSeekBarRow(card, "底部边距", 0, 120, DialogSettings.getBottomMargin(this), "dp") { DialogSettings.setBottomMargin(this, it) }
        addSeekBarRow(card, "行间距", 0, 32, DialogSettings.getRowSpacing(this), "dp") { DialogSettings.setRowSpacing(this, it) }
        addSeekBarRow(card, "列间距", 0, 32, DialogSettings.getColSpacing(this), "dp") { DialogSettings.setColSpacing(this, it) }
    }

    private fun buildAnimSection() {
        addSectionLabel("弹窗动画")
        val card = addSectionCard()
        addRadioRow(card, "动画方向",
            listOf("bottom" to "底部", "left" to "左侧", "right" to "右侧", "center" to "居中"),
            DialogSettings.getAnimDirection(this)
        ) { DialogSettings.setAnimDirection(this, it) }
        addRadioRow(card, "动画风格",
            listOf("slide" to "滑动", "scale" to "缩放", "spring" to "弹性"),
            DialogSettings.getAnimStyle(this)
        ) { DialogSettings.setAnimStyle(this, it) }
        addSeekBarRow(card, "动画时长", 150, 500, DialogSettings.getAnimDuration(this), "ms") { DialogSettings.setAnimDuration(this, it) }
    }

    private fun buildOtherSection() {
        addSectionLabel("其他")
        val card = addSectionCard()
        addSeekBarRow(card, "标签页高度", 28, 64, DialogSettings.getTabHeightDp(this), "dp") { DialogSettings.setTabHeightDp(this, it) }

        // 配置导入导出按钮
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp2px(), 0, 4.dp2px())
        }

        val exportBtn = TextView(this).apply {
            text = "导出配置"
            textSize = 13f
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            setPadding(20.dp2px(), 10.dp2px(), 20.dp2px(), 10.dp2px())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 10.dp2px().toFloat()
                setStroke(1.dp2px(), colorOutline)
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener { exportConfig() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12.dp2px() }
        }
        btnRow.addView(exportBtn)

        val importBtn = TextView(this).apply {
            text = "导入配置"
            textSize = 13f
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
            setPadding(20.dp2px(), 10.dp2px(), 20.dp2px(), 10.dp2px())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 10.dp2px().toFloat()
                setStroke(1.dp2px(), colorOutline)
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener { importConfig() }
        }
        btnRow.addView(importBtn)
        card.addView(btnRow)
    }

    // ===== 导入导出 =====

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val json = viewModel.repository.exportToJson()
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this@GlobalDialogSettingsActivity, "已导出", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@GlobalDialogSettingsActivity, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportConfig() {
        exportLauncher.launch("drawer_backup_${System.currentTimeMillis()}.json")
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: run {
                    Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
            showImportSelection(viewModel.repository.parseBackup(json))
        } catch (e: Exception) {
            Toast.makeText(this, "读取失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importConfig() {
        importLauncher.launch("application/json")
    }

    /**
     * 解析后进入「选择要导入的内容」对话框。
     */
    private fun showImportSelection(backup: BackupData) {
        if (backup.collections.isEmpty() && backup.globalSettings == null) {
            Toast.makeText(this, "文件中没有可导入的内容", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val existing = viewModel.repository.existingCollectionNames()
            buildImportDialog(backup, existing)
        }
    }

    /**
     * 构建导入选择对话框：
     * - 每个抽屉一行：复选框 + 可编辑名称 + 应用数
     * - 重名者预填去重建议名（用户可改）
     * - 顶部「导入全局设置」开关（文件含则默认勾选）
     */
    private fun buildImportDialog(backup: BackupData, existing: List<String>) {
        val ctx = this
        val collections = backup.collections
        val hasGlobal = backup.globalSettings != null

        val scroll = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp2px(), 12.dp2px(), 24.dp2px(), 12.dp2px())
        }

        container.addView(TextView(ctx).apply {
            text = "选择要导入的抽屉"
            textSize = 16f
            setTextColor(colorOnSurface)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        // 全选（仅在有抽屉时显示）
        val selectAllCb = CheckBox(ctx).apply {
            text = "全选抽屉"
            isChecked = true
            setTextColor(colorOnSurface)
        }
        if (collections.isNotEmpty()) container.addView(selectAllCb)

        // 全局设置开关
        val globalRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val globalCb = CheckBox(ctx).apply {
            text = "导入全局设置"
            isChecked = hasGlobal
            isEnabled = hasGlobal
            setTextColor(colorOnSurface)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        globalRow.addView(globalCb)
        if (!hasGlobal) {
            globalRow.addView(TextView(ctx).apply {
                text = "(文件不含)"; textSize = 11f; setTextColor(colorOnSurfaceVariant)
            })
        }
        container.addView(globalRow)

        container.addView(View(ctx).apply {
            setBackgroundColor(colorOutline)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp2px())
        })

        // 每个抽屉一行
        val rows = mutableListOf<Pair<CheckBox, EditText>>()
        collections.forEach { pc ->
            val conflict = existing.contains(pc.name)
            val suggested = if (conflict) {
                makeUnique("${pc.name} (导入)", existing + rows.map { it.second.text.toString() })
            } else {
                pc.name
            }
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val cb = CheckBox(ctx).apply { isChecked = true; setTextColor(colorOnSurface) }
            val nameEdit = EditText(ctx).apply {
                setText(suggested)
                setTextColor(colorOnSurface)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (conflict) hint = "重名，可改名"
            }
            val countTv = TextView(ctx).apply {
                text = "· ${pc.apps.size}个应用"; textSize = 11f; setTextColor(colorOnSurfaceVariant)
            }
            row.addView(cb)
            row.addView(nameEdit)
            row.addView(countTv)
            container.addView(row)
            rows.add(cb to nameEdit)
        }

        // 「全选抽屉」统一控制所有行的复选框（放在循环外，避免监听器被反复覆盖只生效最后一行）
        selectAllCb.setOnCheckedChangeListener { _, checked ->
            rows.forEach { it.first.isChecked = checked }
        }

        scroll.addView(container)

        AlertDialog.Builder(ctx)
            .setView(scroll)
            .setPositiveButton("导入") { _, _ ->
                val selected = rows.mapIndexedNotNull { i, (cb, edit) ->
                    if (cb.isChecked) i to edit.text.toString().trim() else null
                }
                val applyGlobal = globalCb.isChecked
                if (selected.isEmpty() && !applyGlobal) {
                    Toast.makeText(ctx, "请至少选择一项或勾选全局设置", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                doImport(backup, selected, applyGlobal)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 生成一个不与 taken 中任何名称重复的名字
     */
    private fun makeUnique(base: String, taken: List<String>): String {
        if (!taken.contains(base)) return base
        var n = 1
        while (taken.contains("$base ($n)")) n++
        return "$base ($n)"
    }

    /**
     * 执行导入：把选中的抽屉按（可能改过的）名称写入，并可选应用全局设置
     */
    private fun doImport(backup: BackupData, selected: List<Pair<Int, String>>, applyGlobal: Boolean) {
        lifecycleScope.launch {
            try {
                val indices = selected.map { it.first }
                val nameOverrides = selected.associate { it.first to it.second }
                val count = viewModel.repository.importSelected(backup, indices, nameOverrides, applyGlobal)
                val msg = buildString {
                    if (count > 0) append("已导入 $count 个抽屉")
                    if (applyGlobal) append(if (count > 0) "，并应用全局设置" else "已应用全局设置")
                }.ifBlank { "未导入任何内容" }
                Toast.makeText(
                    this@GlobalDialogSettingsActivity,
                    "$msg，请重新生成桌面快捷图标",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@GlobalDialogSettingsActivity,
                    "导入失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
