package com.fengnian.folderdrawer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fengnian.folderdrawer.adapter.InstalledAppAdapter
import com.fengnian.folderdrawer.data.AppItem
import com.fengnian.folderdrawer.databinding.ActivityAppPickerBinding
import com.fengnian.folderdrawer.iconpack.IconPackManager
import com.fengnian.folderdrawer.util.AppUtils
import com.fengnian.folderdrawer.viewmodel.CollectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App 选择器：从已安装应用里选要加入集合?App
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private val viewModel: CollectionViewModel by viewModels()
    private lateinit var adapter: InstalledAppAdapter
    private var collectionId: Long = -1
    private val alreadyAdded = mutableSetOf<String>()
    private var allApps: List<com.fengnian.folderdrawer.util.InstalledApp> = emptyList()
    private var searchQuery: String = ""
    private var currentFilter: String = "all" // all / selected / unselected
    private var selectedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        collectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1)
        if (collectionId == -1L) { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // 用自绘 TextView 显示标题+数量，禁用系统 title（避免改 title 触发的闪烁）
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
        updateTitle()

        adapter = InstalledAppAdapter(
            context = this,
            iconPackManager = IconPackManager.getInstance(this),
            scope = lifecycleScope,
            alreadyAdded = alreadyAdded,
            onToggle = { app, checked -> toggleApp(app, checked) }
        )
        binding.appRecycler.layoutManager = LinearLayoutManager(this)
        binding.appRecycler.adapter = adapter
        // 关闭默认 item 动画，避免筛选/重绘时整列闪烁
        binding.appRecycler.itemAnimator = null

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                applyFilters()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            currentFilter = when (checkedIds.first()) {
                R.id.chipAll -> "all"
                R.id.chipSelected -> "selected"
                R.id.chipUnselected -> "unselected"
                else -> "all"
            }
            applyFilters()
        }
        // 默认选中"所有"
        binding.chipAll.isChecked = true

        loadAlreadyAdded()
        loadApps()
    }

    private fun updateTitle() {
        // 仅更新自绘 TextView 的文字，不触碰 Toolbar.title，避免重排闪烁
        binding.toolbarTitle.text = "添加应用 ($selectedCount)"
    }

    private fun loadAlreadyAdded() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                viewModel.repository.getApps(collectionId)
            }
            alreadyAdded.clear()
            apps.forEach { alreadyAdded.add("${it.packageName}/${it.activityClassName}") }
            selectedCount = alreadyAdded.size
            updateTitle()
            applyFilters()
        }
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                AppUtils.getInstalledApps(this@AppPickerActivity)
            }
            allApps = apps
            binding.progressBar.visibility = View.GONE
            applyFilters()
        }
    }

    private fun applyFilters() {
        var result = allApps

        // 搜索过滤
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            result = result.filter { it.label.lowercase().contains(q) }
        }

        // 类别过滤
        result = when (currentFilter) {
            "selected" -> result.filter { app ->
                alreadyAdded.contains("${app.packageName}/${app.activityName}")
            }
            "unselected" -> result.filter { app ->
                !alreadyAdded.contains("${app.packageName}/${app.activityName}")
            }
            else -> result
        }

        adapter.submitList(result)
    }

    private fun toggleApp(app: com.fengnian.folderdrawer.util.InstalledApp, checked: Boolean) {
        lifecycleScope.launch {
            if (checked) {
                viewModel.repository.addAppToCollection(
                    collectionId = collectionId,
                    packageName = app.packageName,
                    activityClassName = app.activityName,
                    displayName = app.label
                )
                alreadyAdded.add("${app.packageName}/${app.activityName}")
            } else {
                val items = withContext(Dispatchers.IO) {
                    viewModel.repository.getApps(collectionId)
                }
                items.find { it.packageName == app.packageName && it.activityClassName == app.activityName }
                    ?.let { viewModel.repository.removeAppFromCollection(it) }
                alreadyAdded.remove("${app.packageName}/${app.activityName}")
            }
            selectedCount = alreadyAdded.size
            updateTitle()
            // 被点的行已在 adapter 里就地更新了 UI；"所有"视图无需整表重绘（避免闪烁）。
            // 仅当处于"已选/未选"筛选下，才重建列表让条目即时出现/消失。
            if (currentFilter != "all") {
                applyFilters()
            }
        }
    }

    companion object {
        const val EXTRA_COLLECTION_ID = "collection_id"
    }
}
