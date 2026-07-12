package com.fengnian.folderdrawer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.fengnian.folderdrawer.adapter.EditAppGridAdapter
import com.fengnian.folderdrawer.data.AppItem
import com.fengnian.folderdrawer.databinding.ActivityCollectionEditBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.ColorPickerDialog
import com.fengnian.folderdrawer.viewmodel.CollectionViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 抽屉编辑页：名称、应用管理、图标主题、快捷方式图标、弹窗背景、文字颜色、弹窗样式、弹窗动? */
class CollectionEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCollectionEditBinding
    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var iconPackManager: IconPackManager
    private lateinit var appsAdapter: EditAppGridAdapter

    private val density by lazy { resources.displayMetrics.density }

    // 状态
    private var selectedShortcutIconDrawable: String? = null
    private var selectedGridColumns: Int = 5
    private var selectedIconSizeDp: Int = 48
    private var selectedShowAppName: Boolean = true
    private var selectedCompactAppName: Boolean = false
    private var selectedAppNameSizeSp: Int = 11
    private var selectedAppNameTextColor: Int = 0
    private var editingId: Long = -1
    private var isNew: Boolean = true

    private var pendingAppForIconReplace: AppItem? = null
    private var pendingIconPackPackage: String? = null

    private val iconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val drawableName = result.data?.getStringExtra(IconPickerActivity.EXTRA_ICON_DRAWABLE_NAME)
            ?: return@registerForActivityResult

        if (pendingAppForIconReplace != null) {
            // 替换 APP 图标
            applyIconToApp(pendingAppForIconReplace!!, drawableName, pendingIconPackPackage != null)
            pendingAppForIconReplace = null
            pendingIconPackPackage = null
        } else {
            // 选择快捷方式图标
            selectedShortcutIconDrawable = drawableName
            updateShortcutIconPreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        editingId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1)
        isNew = intent.getBooleanExtra(EXTRA_IS_NEW, true)

        iconPackManager = IconPackManager.getInstance(this)

        if (!isNew && editingId != -1L) {
            binding.toolbar.title = "编辑抽屉"
            loadExisting(editingId)
        } else {
            binding.toolbar.title = "新建抽屉"
        }

        setupIconPackSelector()
        setupShortcutIconPicker()
        setupDialogStyleControls()
        setupTextColorControls()
        setupAppsSection()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // 保存按钮已改为 Toolbar 内自定义布局，不再使用 menu item
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ===== 应用管理 =====

    private fun setupAppsSection() {
        appsAdapter = EditAppGridAdapter(
            context = this,
            iconPackManager = iconPackManager,
            scope = lifecycleScope,
            onRemoveClick = { app -> confirmRemoveApp(app) },
            onAppLongClick = { app -> showReplaceIconMenu(app) },
            onItemMove = { _, _ -> /* position handled internally */ }
        )
        binding.appsRecycler.layoutManager = GridLayoutManager(this, 4)
        binding.appsRecycler.adapter = appsAdapter

        // 拖拽排序：ItemTouchHelper
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                appsAdapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    appsAdapter.isDragging = true
                    viewHolder?.itemView?.apply {
                        alpha = 0.7f
                        scaleX = 1.15f
                        scaleY = 1.15f
                        elevation = 16f * density
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.apply {
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                    elevation = 0f
                }
                // 延迟重置 isDragging，防止 ACTION_UP 被图标 onClick 捕获
                recyclerView.postDelayed({ appsAdapter.isDragging = false }, 300L)
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(binding.appsRecycler)

        // 自定义保存按钮（Toolbar 内嵌）
        binding.saveButton.setOnClickListener {
            save()
        }

        binding.addAppsButton.setOnClickListener {
            if (editingId == -1L) {
                saveAndContinueEditing()
            } else {
                launchAppPicker()
            }
        }

        if (editingId != -1L) {
            observeApps()
        } else {
            binding.appsEmptyHint.visibility = View.VISIBLE
        }
    }

    /**
     * 显示替换图标菜单（带图标预览）
     */
    private fun showReplaceIconMenu(app: AppItem) {
        val packs = iconPackManager.getInstalledIconPacks()
        val options = mutableListOf<IconOption>()
        var currentDialog: android.app.AlertDialog? = null

        // 恢复自动
        options.add(IconOption("恢复自动（系统）", { null }) { _, _ ->
            currentDialog?.dismiss()
            val updated = app.copy(customIconPath = null, customIconSource = null)
            lifecycleScope.launch {
                viewModel.repository.updateAppItem(updated)
                refreshAppGrid()
                Toast.makeText(this@CollectionEditActivity, "已恢复", Toast.LENGTH_SHORT).show()
            }
        })

        // 从相册选择
        options.add(IconOption("从相册选择", { null }) { _, _ ->
            currentDialog?.dismiss()
            pendingAppForIconReplace = app
            pickImageFromGallery()
        })

        // 各图标包
        packs.forEach { pack ->
            options.add(IconOption("从「${pack.label}」选择", {
                try {
                    packageManager.getApplicationIcon(pack.packageName)
                } catch (_: Exception) { null }
            }) { _, _ ->
                currentDialog?.dismiss()
                pendingAppForIconReplace = app
                pendingIconPackPackage = pack.packageName
                iconPickerLauncher.launch(
                    Intent(this, IconPickerActivity::class.java).apply {
                        putExtra(IconPickerActivity.EXTRA_ICON_PACK_PACKAGE, pack.packageName)
                    }
                )
            })
        }

        currentDialog = showIconOptionDialog("替换「${app.displayName}」图标", options)
    }

    /**
     * 显示图标选项对话框（列表风格，一级菜单），返回对话框以便调用方关闭
     */
    private fun showIconOptionDialog(title: String, options: List<IconOption>): android.app.AlertDialog {
        val dialogView = layoutInflater.inflate(R.layout.dialog_icon_picker_simple, null)
        val titleTv = dialogView.findViewById<android.widget.TextView>(R.id.dialogTitle)
        val closeBtn = dialogView.findViewById<android.widget.ImageButton>(R.id.closeButton)
        val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.iconList)

        titleTv.text = title
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = IconOptionAdapter(options) { opt ->
            opt.onClick(opt, -1)
        }
        recycler.adapter = adapter

        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
        return dialog
    }

    /**
     * 从相册选择图片作为图标
     */
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        galleryLauncher.launch(intent)
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        val app = pendingAppForIconReplace ?: return@registerForActivityResult

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    }
                } ?: return@launch

                val dir = java.io.File(filesDir, "app_icons").apply { mkdirs() }
                val file = java.io.File(dir, "icon_${app.id}_${System.currentTimeMillis()}.png")
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                val updated = app.copy(
                    customIconPath = file.absolutePath,
                    customIconSource = "gallery"
                )
                viewModel.repository.updateAppItem(updated)
                refreshAppGrid()
                Toast.makeText(this@CollectionEditActivity, "已替换图标", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@CollectionEditActivity, "替换失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        pendingAppForIconReplace = null
    }

    /**
     * 从指定 Icon Pack 选图标后应用到 APP
     * 临时加载该图标包获取 Drawable，保存为 PNG 到私有目?     */
    private fun applyIconToApp(app: AppItem, drawableName: String, useTempPack: Boolean) {
        val packPkg = pendingIconPackPackage
        lifecycleScope.launch {
            try {
                val drawable = withContext(Dispatchers.IO) {
                    if (useTempPack && !packPkg.isNullOrBlank()) {
                        iconPackManager.loadPack(packPkg)
                        val d = iconPackManager.getDrawableByNameFromTemp(drawableName)
                        iconPackManager.clearTempPack()
                        d
                    } else {
                        iconPackManager.getDrawableByName(drawableName)
                    }
                } ?: return@launch

                val bitmap = drawableToBitmap(drawable)
                val dir = java.io.File(filesDir, "app_icons").apply { mkdirs() }
                val file = java.io.File(dir, "icon_${app.id}_${System.currentTimeMillis()}.png")
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                val updated = app.copy(
                    customIconPath = file.absolutePath,
                    customIconSource = "iconpack"
                )
                viewModel.repository.updateAppItem(updated)
                refreshAppGrid()
                Toast.makeText(this@CollectionEditActivity, "已替换图标", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@CollectionEditActivity, "替换失败${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        pendingAppForIconReplace = null
        pendingIconPackPackage = null
    }

    /**
     * Drawable ?Bitmap（PNG 编码用）
     */
    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        val w = drawable.intrinsicWidth.coerceAtLeast(192)
        val h = drawable.intrinsicHeight.coerceAtLeast(192)
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    private fun observeApps() {
        viewModel.repository.observeApps(editingId).observe(this) { apps ->
            appsAdapter.submitList(apps)
            binding.appsEmptyHint.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * 强制刷新应用网格图标（替换图标后调用?     * 重新提交列表触发 DiffUtil 更新
     */
    private fun refreshAppGrid() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                viewModel.repository.getApps(editingId)
            }
            appsAdapter.submitList(apps)
        }
    }

    private fun launchAppPicker() {
        val intent = Intent(this, AppPickerActivity::class.java).apply {
            putExtra(AppPickerActivity.EXTRA_COLLECTION_ID, editingId)
        }
        startActivity(intent)
    }

    private fun confirmRemoveApp(app: AppItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("移除应用")
            .setMessage("确定从抽屉中移除${app.displayName}」吗？")
            .setPositiveButton("移除") { _, _ ->
                lifecycleScope.launch {
                    viewModel.repository.removeAppFromCollection(app)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ===== 加载已有集合 =====

    private fun loadExisting(id: Long) {
        lifecycleScope.launch {
            val c = viewModel.repository.getCollection(id) ?: return@launch
            binding.nameInput.setText(c.name)
            selectedShortcutIconDrawable = c.shortcutIconDrawable
            selectedGridColumns = c.gridColumns
            selectedIconSizeDp = c.iconSizeDp
            selectedShowAppName = c.showAppName
            selectedCompactAppName = c.compactAppName
            selectedAppNameSizeSp = c.appNameSizeSp
            selectedAppNameTextColor = c.appNameTextColor

            updateShortcutIconPreview()
            updateTextColorUI()

            // 同步弹窗样式控件
            binding.columnsSeekBar.progress = selectedGridColumns - 3
            binding.columnsValue.text = selectedGridColumns.toString()
            binding.iconSizeSeekBar.progress = selectedIconSizeDp - 36
            binding.iconSizeValue.text = "${selectedIconSizeDp}dp"
            binding.showNameSwitch.isChecked = selectedShowAppName
            binding.compactNameSwitch.isChecked = selectedCompactAppName
            binding.nameSizeSeekBar.progress = selectedAppNameSizeSp - 8
            binding.nameSizeValue.text = "${selectedAppNameSizeSp}sp"
            updateCompactNameRowVisibility()

        }
    }

    // ===== 图标主题选择（底部弹窗）=====

    private fun setupIconPackSelector() {
        binding.selectIconPackButton.setOnClickListener {
            showIconPackSelectorDialog()
        }
        updateIconPackName()
    }

    private fun showIconPackSelectorDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_icon_pack_selector, null)
        dialog.setContentView(view)

        val closeBtn = view.findViewById<ImageView>(R.id.closeButton)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val recycler = view.findViewById<RecyclerView>(R.id.iconPackList)

        closeBtn.setOnClickListener { dialog.dismiss() }

        lifecycleScope.launch(Dispatchers.IO) {
            val packs = iconPackManager.getInstalledIconPacks()
            val activePack = iconPackManager.getActiveIconPack()

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE

                if (packs.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@CollectionEditActivity)
                    val adapter = IconPackSelectorAdapter(
                        packs = packs,
                        activePackPkg = activePack,
                        onClick = { selectedPkg ->
                            iconPackManager.setActiveIconPack(selectedPkg)
                            updateIconPackName()
                            updateShortcutIconPreview()
                            refreshAppGrid()
                            dialog.dismiss()
                        }
                    )
                    recycler.adapter = adapter
                }
            }
        }

        dialog.show()
    }

    private fun updateIconPackName() {
        val activePack = iconPackManager.getActiveIconPack()
        binding.iconPackName.text = if (activePack.isNullOrEmpty()) {
            "系统默认图标"
        } else {
            try {
                val pm = packageManager
                val info = pm.getApplicationInfo(activePack, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                activePack
            }
        }
    }

    // ===== 快捷方式图标选择?=====

    private fun setupShortcutIconPicker() {
        binding.pickIconButton.setOnClickListener {
            pendingAppForIconReplace = null
            pendingIconPackPackage = null
            iconPickerLauncher.launch(
                Intent(this, IconPickerActivity::class.java)
            )
        }
        updateShortcutIconPreview()
    }

    private fun updateShortcutIconPreview() {
        val drawableName = selectedShortcutIconDrawable
        if (drawableName.isNullOrBlank()) {
            binding.shortcutIconPreview.setImageDrawable(null)
            binding.shortcutIconPreview.setBackgroundColor(0xFFC4704A.toInt())
            binding.shortcutIconLabel.text = "未选择（默认字母图标）"
        } else {
            binding.shortcutIconPreview.setBackgroundColor(Color.TRANSPARENT)
            binding.shortcutIconLabel.text = drawableName
            lifecycleScope.launch {
                val drawable = withContext(Dispatchers.IO) {
                    iconPackManager.getDrawableByName(drawableName)
                }
                binding.shortcutIconPreview.setImageDrawable(drawable)
            }
        }
    }





    // ===== 弹窗样式控件 =====

    private fun setupDialogStyleControls() {
        binding.columnsSeekBar.setOnSeekBarChangeListener(simpleSeekListener(
            onProgress = { progress ->
                selectedGridColumns = progress + 3
                binding.columnsValue.text = selectedGridColumns.toString()
            }
        ))

        binding.iconSizeSeekBar.setOnSeekBarChangeListener(simpleSeekListener(
            onProgress = { progress ->
                selectedIconSizeDp = progress + 36
                binding.iconSizeValue.text = "${selectedIconSizeDp}dp"
            }
        ))

        binding.showNameSwitch.setOnCheckedChangeListener { _, checked ->
            selectedShowAppName = checked
            updateCompactNameRowVisibility()
        }

        binding.compactNameSwitch.setOnCheckedChangeListener { _, checked ->
            selectedCompactAppName = checked
        }

        binding.nameSizeSeekBar.setOnSeekBarChangeListener(simpleSeekListener(
            onProgress = { progress ->
                selectedAppNameSizeSp = progress + 8
                binding.nameSizeValue.text = "${selectedAppNameSizeSp}sp"
            }
        ))

        // 全局弹窗设置入口
        binding.openGlobalSettingsBtn.setOnClickListener {
            startActivity(Intent(this, GlobalDialogSettingsActivity::class.java))
        }
    }

    // ===== 每抽屉文字颜色 =====

    private fun setupTextColorControls() {
        updateTextColorUI()
        binding.appNameColorBtn.setOnClickListener {
            ColorPickerDialog.show(
                this,
                if (selectedAppNameTextColor != 0) selectedAppNameTextColor else 0xFFFFFFFF.toInt()
            ) { picked ->
                selectedAppNameTextColor = picked
                updateTextColorUI()
            }
        }
    }

    private fun updateTextColorUI() {
        applySwatch(binding.appNameColorSwatch, selectedAppNameTextColor)
        binding.appNameColorBtn.text = if (selectedAppNameTextColor != 0)
            String.format("#%08X", selectedAppNameTextColor) else "自动"
    }

    private fun applySwatch(swatch: View, color: Int) {
        (swatch.background as? GradientDrawable)?.setColor(
            if (color != 0) color else Color.TRANSPARENT
        )
    }

    private fun updateCompactNameRowVisibility() {
        // 不显示APP名称时，整个名称相关设置组都隐藏
        binding.nameSizeGroup.visibility =
            if (selectedShowAppName) View.VISIBLE else View.GONE
    }


    // ===== 工具方法 =====

    private fun simpleSeekListener(onProgress: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgress(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    // ===== 保存 =====

    private fun saveAndContinueEditing() {
        val name = binding.nameInput.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.nameInput.error = "请先输入抽屉名称"
            return
        }

        lifecycleScope.launch {
            val newId = viewModel.repository.createCollection(
                name = name,
                shortcutIconDrawable = selectedShortcutIconDrawable,
                gridColumns = selectedGridColumns,
                iconSizeDp = selectedIconSizeDp,
                showAppName = selectedShowAppName,
                compactAppName = selectedCompactAppName,
                appNameSizeSp = selectedAppNameSizeSp
            )
            editingId = newId
            isNew = false
            binding.toolbar.title = "编辑抽屉"
            observeApps()
            launchAppPicker()
        }
    }

    private fun save() {
        val name = binding.nameInput.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            binding.nameInput.error = "名称不能为空"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            if (editingId != -1L) {
                val existing = viewModel.repository.getCollection(editingId)
                if (existing != null) {
                    val updated = existing.copy(
                        name = name,
                        shortcutIconDrawable = selectedShortcutIconDrawable,
                        gridColumns = selectedGridColumns,
                        iconSizeDp = selectedIconSizeDp,
                        showAppName = selectedShowAppName,
                        compactAppName = selectedCompactAppName,
                        appNameSizeSp = selectedAppNameSizeSp,
                        appNameTextColor = selectedAppNameTextColor
                    )
                    viewModel.repository.updateCollection(updated)

                    // 保存拖拽后的顺序
                    val currentApps = appsAdapter.currentList
                    if (currentApps.isNotEmpty()) {
                        viewModel.repository.reorderApps(currentApps)
                    }
                }
            } else {
                // 首次保存：创建抽屉，继续留在编辑页
                val newId = viewModel.repository.createCollection(
                    name = name,
                    shortcutIconDrawable = selectedShortcutIconDrawable,
                    gridColumns = selectedGridColumns,
                    iconSizeDp = selectedIconSizeDp,
                    showAppName = selectedShowAppName,
                    compactAppName = selectedCompactAppName,
                    appNameSizeSp = selectedAppNameSizeSp
                )
                editingId = newId
                isNew = false
                withContext(Dispatchers.Main) {
                    binding.toolbar.title = "编辑抽屉"
                }
                loadExisting(newId)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CollectionEditActivity, "配置已保存", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collection_id"
        const val EXTRA_IS_NEW = "is_new"
    }
}

/**
 * Icon Pack 选择器适配器（底部弹窗列表）
 */
private class IconPackSelectorAdapter(
    private val packs: List<IconPackManager.IconPackInfo>,
    private val activePackPkg: String?,
    private val onClick: (String?) -> Unit
) : RecyclerView.Adapter<IconPackSelectorAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.packIcon)
        val label: TextView = itemView.findViewById(R.id.packLabel)
        val check: ImageView = itemView.findViewById(R.id.checkIcon)
    }

    override fun getItemCount(): Int = packs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_pack, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pack = packs[position]
        holder.label.text = pack.label
        holder.check.visibility = if (pack.packageName == activePackPkg) View.VISIBLE else View.GONE
        // 尝试加载 icon pack 图标
        try {
            val pm = holder.itemView.context.packageManager
            val drawable = pm.getApplicationIcon(pack.packageName)
            holder.icon.setImageDrawable(drawable)
        } catch (_: Exception) {
            holder.icon.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        holder.itemView.setOnClickListener { onClick(pack.packageName) }
    }
}

/**
 * 图标选项数据类
 */
data class IconOption(
    val label: String,
    val loadIcon: (suspend () -> android.graphics.drawable.Drawable?)? = null,
    val onClick: (IconOption, Int) -> Unit = { _, _ -> }
)

/**
 * 图标选项 Adapter
 */
class IconOptionAdapter(
    private val options: List<IconOption>,
    private val onItemClick: (IconOption) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<IconOptionAdapter.VH>() {

    inner class VH(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val icon: android.widget.ImageView = itemView.findViewById(R.id.optionIcon)
        val label: android.widget.TextView = itemView.findViewById(R.id.optionLabel)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_icon_option, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val opt = options[position]
        holder.label.text = opt.label

        // 加载图标
        val loader = opt.loadIcon
        if (loader != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val drawable = withContext(kotlinx.coroutines.Dispatchers.IO) { loader() }
                holder.icon.setImageDrawable(drawable)
            }
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onItemClick(opt) }
    }

    override fun getItemCount() = options.size
}
